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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

enum class AppScreen { ADB_RESCUE, LOCAL_SHELL }

class MainViewModel : ViewModel() {

    var currentScreen by mutableStateOf(AppScreen.ADB_RESCUE)
    var isConnected by mutableStateOf(false)
    var hasRoot by mutableStateOf(false)
    
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

    fun connectNetwork(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⏳ Conectando em $adbIp:$adbPort...")
            try {
                val success = AdbManager.connect(context, adbIp, adbPort.toIntOrNull() ?: 5555)
                withContext(Dispatchers.Main) {
                    isConnected = success
                    if (success) addLog("✅ Wi-Fi Conectado ao REMOTO")
                    else addLog("❌ Falha na conexão Wi-Fi")
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
                    else addLog("❌ Falha handshake USB")
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
        val cmd = shellCommand
        if (cmd.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
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
    fun requestRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val output = BufferedReader(InputStreamReader(process.inputStream)).readLine() ?: ""
                withContext(Dispatchers.Main) {
                    hasRoot = output.contains("uid=0")
                    addLocalLog(if (hasRoot) "✅ Root concedido" else "❌ Root negado")
                }
            } catch (e: Exception) { addLocalLog("❌ Erro Root") }
        }
    }

    fun runLocalShell() {
        val cmd = localShellCommand
        if (cmd.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            addLocalLog("$ $cmd")
            try {
                val shell = if (hasRoot) "su" else "sh"
                val process = Runtime.getRuntime().exec(arrayOf(shell, "-c", cmd))
                BufferedReader(InputStreamReader(process.inputStream)).forEachLine { addLocalLog(it) }
            } catch (e: Exception) { addLocalLog("❌ Erro local") }
        }
    }
}
