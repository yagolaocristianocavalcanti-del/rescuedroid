package com.rescuedroid.rescuedroid

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuedroid.rescuedroid.adb.AdbManager
import com.rescuedroid.rescuedroid.adb.UsbAdbConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

enum class AppScreen { ADB_RESCUE, LOCAL_SHELL }
private enum class ShellTarget { LOCAL, ADB }

class MainViewModel : ViewModel() {
    private companion object {
        const val ANDROID_SHELL = "/system/bin/sh"
        const val DEFAULT_ANDROID_PATH =
            "/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin"
        const val LOCAL_EXIT_MARKER = "__RD_EXIT__:"
    }

    private var localShellProcess: Process? = null
    private var localShellWriter: BufferedWriter? = null
    private var localShellReaderJob: Job? = null

    var currentScreen by mutableStateOf(AppScreen.ADB_RESCUE)
    var isConnected by mutableStateOf(false)
    var adbIp by mutableStateOf("192.168.1.100")
    var adbPort by mutableStateOf("5555")
    var shellCommand by mutableStateOf("ls /sdcard")
    var localShellCommand by mutableStateOf("")

    val consoleLogs = mutableStateListOf<String>()
    val localConsoleLogs = mutableStateListOf<String>()

    init { addLog("🚀 RescueDroid 2.0 Iniciado") }

    fun addLog(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            msg.split("\n").filter { it.isNotBlank() }.forEach { consoleLogs.add(it) }
        }
    }

    fun addLocalLog(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            msg.split("\n").filter { it.isNotBlank() }.forEach { localConsoleLogs.add(it) }
        }
    }

    fun clearLogs() { consoleLogs.clear() }

    fun clearLocalLogs() { localConsoleLogs.clear() }

    private fun explainUsbFailure(raw: String): String {
        val reason = raw.trim()
        return when {
            reason.contains("Permissao USB negada", ignoreCase = true) ->
                "permissao USB negada"
            reason.contains("Nenhuma interface ADB encontrada", ignoreCase = true) ->
                "sem interface ADB no dispositivo conectado"
            reason.contains("Tempo limite no handshake USB ADB", ignoreCase = true) ->
                "depuracao USB provavelmente desativada ou a chave ADB nao foi aceita no outro aparelho"
            reason.contains("Nao foi possivel carregar ou gerar a chave ADB", ignoreCase = true) ->
                "nao foi possivel preparar a chave ADB deste app"
            reason.isBlank() ->
                "ative a depuracao USB e aceite a chave ADB no outro aparelho"
            else -> reason
        }
    }

    private fun explainWifiFailure(raw: String): String {
        val reason = raw.trim()
        return when {
            reason.contains("Connection refused", ignoreCase = true) ->
                "conexao recusada; verifique IP, porta e se o ADB por rede esta ativo"
            reason.contains("timeout", ignoreCase = true) ->
                "tempo limite; o aparelho remoto nao respondeu ao handshake ADB"
            reason.contains("Nao foi possivel carregar ou gerar a chave ADB", ignoreCase = true) ->
                "nao foi possivel preparar a chave ADB deste app"
            reason.isBlank() ->
                "verifique IP, porta e autorizacao ADB"
            else -> reason
        }
    }

    private fun addShellLog(target: ShellTarget, message: String) {
        when (target) {
            ShellTarget.LOCAL -> addLocalLog(message)
            ShellTarget.ADB -> addLog(message)
        }
    }

    private fun normalizeCommand(rawCommand: String, target: ShellTarget): String? {
        val cmd = rawCommand.trim()
        if (cmd.isBlank()) return null

        if (target == ShellTarget.LOCAL) {
            when {
                cmd == "pkg" || cmd.startsWith("pkg ") || cmd == "apt" || cmd.startsWith("apt ") -> {
                    addShellLog(target, "❌ 'pkg' e 'apt' sao comandos do Termux, nao do shell padrao do Android")
                    addShellLog(target, "💡 Use o app Termux para instalar pacotes, ou rode comandos nativos como ls, getprop, id")
                    return null
                }
                cmd == "tcpip" || cmd.startsWith("tcpip ") -> {
                    addShellLog(target, "❌ 'tcpip' sozinho nao e um comando do shell do Android")
                    addShellLog(target, "💡 Para ADB por rede, use a area de resgate/ADB com o dispositivo ja conectado")
                    return null
                }
            }
        }

        if (target == ShellTarget.ADB) {
            when {
                cmd == "adb" || cmd.startsWith("adb ") -> {
                    addShellLog(target, "❌ Este console ADB executa shell remoto, nao comandos do cliente 'adb' do PC")
                    addShellLog(target, "💡 Use comandos do Android remoto como getprop, pm, settings, ls, screencap")
                    return null
                }
                cmd == "connect" || cmd.startsWith("connect ") -> {
                    addShellLog(target, "💡 A conexao ADB e feita pelos botoes 'Conectar Wi-Fi' e 'USB OTG'")
                    return null
                }
            }
        }

        val pingCorrection = autocorrectPing(cmd)
        if (pingCorrection != null) {
            addShellLog(target, "🛠 Corrigido automaticamente: $pingCorrection")
            return pingCorrection
        }

        if (cmd.startsWith("ping ") && !cmd.contains(" -c ") && !cmd.startsWith("ping -c ")) {
            addShellLog(target, "⚠ 'ping' pode parecer travado sem limite de pacotes")
            addShellLog(target, "💡 Tente: ping -c 4 ${cmd.removePrefix("ping ").trim()}")
        }

        return cmd
    }

    private fun autocorrectPing(command: String): String? {
        val parts = command.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.first() != "ping") return null

        if (parts.size == 3 && parts[1] == "-c" && parts[2].contains(".")) {
            return "ping -c 4 ${parts[2]}"
        }

        if (parts.size >= 4 && parts[1].contains(".") && parts[2] == "-c") {
            return "ping -c 4 ${parts[1]}"
        }

        return null
    }

    fun connectNetwork(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⏳ Conectando em $adbIp:$adbPort...")
            try {
                val success = AdbManager.connect(context, adbIp, adbPort.toIntOrNull() ?: 5555)
                withContext(Dispatchers.Main) {
                    isConnected = success
                    if (success) addLog("✅ Wi-Fi Conectado ao REMOTO")
                    else addLog("❌ Falha na conexão Wi-Fi: ${explainWifiFailure(AdbManager.lastErrorMessage)}")
                }
            } catch (e: Exception) { addLog("❌ Erro: ${e.message}") }
        }
    }

    fun connectUsb(context: Context) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull()
        if (device == null) {
            addLog("⚠ Cabo OTG não detectado")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⏳ Iniciando OTG...")
            try {
                val success = UsbAdbConnector.connect(context, device)
                withContext(Dispatchers.Main) {
                    isConnected = success
                    if (success) addLog("✅ OTG Conectado ao REMOTO")
                    else addLog("❌ Falha USB: ${explainUsbFailure(UsbAdbConnector.lastErrorMessage)}")
                }
            } catch (e: Exception) { addLog("❌ Erro USB: ${e.message}") }
        }
    }

    // --- Ações de Resgate ---

    fun blindUnlock() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("🔓 Unlock Cego...")
                AdbManager.executeCommand("input keyevent 26")
                delay(1000)
                AdbManager.executeCommand("input swipe 300 1400 300 400 400")
                addLog("✅ Sequência enviada")
            } catch (e: Exception) { addLog("❌ Erro no unlock") }
        }
    }

    fun blindUnlockAdvanced(pin: String = "") { blindUnlock() }

    fun takeScreenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("📸 Tirando print remoto...")
                AdbManager.executeCommand("screencap -p /sdcard/rescue.png")
                addLog("✅ Salvo no dispositivo remoto: /sdcard/rescue.png")
            } catch (e: Exception) { addLog("❌ Falha print") }
        }
    }

    fun enableFullAccessibility() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("🛠 Ativando Acessibilidade...")
                val cmds = listOf(
                    "settings put secure accessibility_enabled 1",
                    "settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService",
                    "settings put system screen_off_timeout 600000"
                )
                cmds.forEach { AdbManager.executeCommand(it); delay(500) }
                addLog("✅ Pronto!")
            } catch (e: Exception) { addLog("❌ Falha acessibilidade") }
        }
    }

    fun enableVoiceAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("🎤 Ativando Voice Access...")
                AdbManager.executeCommand("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:com.google.android.accessibility.voiceaccess/.VoiceAccessService")
                addLog("✅ Voice Access ON")
            } catch (e: Exception) { addLog("❌ Erro Voice Access") }
        }
    }

    fun disableTalkBack() {
        viewModelScope.launch(Dispatchers.IO) {
            AdbManager.executeCommand("settings put secure accessibility_enabled 0")
            addLog("🔇 Acessibilidade OFF")
        }
    }

    fun increaseScreenTimeout() {
        viewModelScope.launch(Dispatchers.IO) {
            AdbManager.executeCommand("settings put system screen_off_timeout 600000")
            addLog("⏰ Timeout: 10m")
        }
    }

    fun installSelectedApk(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isConnected) { addLog("❌ Conecte-se primeiro!"); return@launch }
            try {
                addLog("📦 Lendo APK...")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) { addLog("❌ Erro ao ler arquivo"); return@launch }

                val remotePath = "/data/local/tmp/app_rescue.apk"
                addLog("⏳ Enviando APK para o REMOTO...")
                if (AdbManager.pushFile(bytes, remotePath)) {
                    addLog("⏳ Instalando no REMOTO...")
                    val res = AdbManager.executeCommand("pm install -r \"$remotePath\"")
                    addLog("📩 Resposta Remota: $res")
                    AdbManager.executeCommand("rm \"$remotePath\"")
                } else { addLog("❌ Falha no envio ADB") }
            } catch (e: Exception) { addLog("❌ Erro: ${e.message}") }
        }
    }

    fun deviceDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val model = AdbManager.executeCommand("getprop ro.product.model")
                val android = AdbManager.executeCommand("getprop ro.build.version.release")
                addLog("📱 Modelo: $model | Android: $android")
            } catch (e: Exception) { addLog("❌ Falha diagnóstico") }
        }
    }

    fun startMirror() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("📺 Iniciando Mirror...")
            AdbManager.execAsync("CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 2.0")
            addLog("✅ Comando mirror enviado")
        }
    }

    fun runAdbCommand() {
        val cmd = normalizeCommand(shellCommand, ShellTarget.ADB) ?: return
        if (cmd.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isConnected) {
                addLog("❌ Conecte um dispositivo ADB antes de enviar comandos")
                return@launch
            }
            addLog("> $cmd")
            val res = AdbManager.executeCommand(cmd)
            addLog(res)
        }
    }

    fun disconnectAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            AdbManager.disconnect()
            withContext(Dispatchers.Main) { isConnected = false; addLog("🔴 Desconectado") }
        }
    }

    // --- Shell Local ---
    private fun ensureLocalShellSession() {
        if (localShellProcess?.isAlive == true && localShellWriter != null) return

        try {
            val process = ProcessBuilder(ANDROID_SHELL)
                .redirectErrorStream(true)
                .apply {
                    environment()["PATH"] = DEFAULT_ANDROID_PATH
                }
                .start()

            localShellProcess = process
            localShellWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            localShellReaderJob?.cancel()
            localShellReaderJob = viewModelScope.launch(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith(LOCAL_EXIT_MARKER) -> {
                                val exitCode = line.removePrefix(LOCAL_EXIT_MARKER).toIntOrNull()
                                when (exitCode) {
                                    0 -> addLocalLog("✅ Comando finalizado")
                                    127 -> addLocalLog("❌ Codigo 127: comando nao encontrado neste Android")
                                    null -> addLocalLog("⚠ Saida de status invalida do shell")
                                    else -> addLocalLog("❌ Comando finalizado com codigo $exitCode")
                                }
                            }
                            line.isNotBlank() -> addLocalLog(line)
                        }
                    }
                }
                addLocalLog("⚠ Sessao do shell local foi encerrada")
                localShellWriter = null
                localShellProcess = null
            }

            addLocalLog("✅ Sessao do shell local iniciada")
        } catch (e: Exception) {
            addLocalLog("❌ Nao foi possivel iniciar o shell local: ${e.message ?: "erro desconhecido"}")
        }
    }

    fun runLocalShell() {
        val cmd = normalizeCommand(localShellCommand, ShellTarget.LOCAL) ?: return
        if (cmd.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            addLocalLog("$ $cmd")
            try {
                ensureLocalShellSession()
                val writer = localShellWriter
                if (writer == null) {
                    addLocalLog("❌ Sessao local indisponivel")
                    return@launch
                }
                writer.write("$cmd\n")
                writer.write("printf '$LOCAL_EXIT_MARKER%s\\n' \"$?\"\n")
                writer.flush()
            } catch (e: Exception) {
                addLocalLog("❌ Erro local: ${e.message ?: "falha ao executar comando"}")
            }
        }
    }

    override fun onCleared() {
        localShellReaderJob?.cancel()
        localShellWriter?.runCatching { close() }
        localShellProcess?.destroy()
        super.onCleared()
    }
}
