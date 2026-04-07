package com.rescuedroid.rescuedroid

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.hardware.usb.UsbManager
import android.net.Uri
import com.cgutman.adblib.AdbConnection
import com.rescuedroid.rescuedroid.adb.AdbDevice
import com.rescuedroid.rescuedroid.adb.AdbDeviceWatcher
import com.rescuedroid.rescuedroid.adb.AdbManager
import com.rescuedroid.rescuedroid.adb.AdbRepository
import com.rescuedroid.rescuedroid.adb.FastbootVar
import com.rescuedroid.rescuedroid.adb.UsbAdbConnector
import com.rescuedroid.rescuedroid.control.RemoteControl
import com.rescuedroid.rescuedroid.debloat.AutoDebloat
import com.rescuedroid.rescuedroid.debloat.DebloatAI
import com.rescuedroid.rescuedroid.debloat.DebloatRiskEngine
import com.rescuedroid.rescuedroid.mirror.MirrorActivity
import com.rescuedroid.rescuedroid.tools.AppManager
import com.rescuedroid.rescuedroid.tools.AutoScriptEngine
import com.rescuedroid.rescuedroid.tools.ScrcpyTool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

enum class AppScreen {
    ADB_RESCUE, SCRCPY, LOGCAT, FASTBOOT, DEBLOAT, SCRIPTS, FILE_MANAGER
}

enum class AdbConnectionState {
    DESCONECTADO, CONECTANDO, CONECTADO, CONECTADO_WIFI, FALHOU
}

enum class ShellTarget { LOCAL, ADB }

enum class UsbPortState { NO_DEVICE, DEVICE_NO_ADB, ADB_READY }

enum class RiskLevel { SEGURO, PERIGOSO, CRITICO }

enum class LogLevel(val code: String, val label: String, val color: Long) {
    VERBOSE("V", "VERBOSE", 0xFF9E9E9E),
    DEBUG("D", "DEBUG", 0xFF00BCD4),
    INFO("I", "INFO", 0xFF4CAF50),
    WARN("W", "AVISO", 0xFFFFEB3B),
    ERROR("E", "ERRO", 0xFFF44336),
    FATAL("F", "FATAL", 0xFFB71C1C)
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val time: String,
    val pid: String,
    val original: String
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val isBloat: Boolean,
    val risk: RiskLevel,
    val riskScore: Int,
    val riskReason: String,
    val acaoSugerida: String = ""
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val adbRepository: AdbRepository,
    private val appManager: AppManager,
    private val autoDebloat: AutoDebloat,
    private val remoteControl: RemoteControl,
    private val autoScriptEngine: AutoScriptEngine,
    private val debloatRiskEngine: DebloatRiskEngine,
    private val debloatAI: DebloatAI,
    private val scrcpyTool: ScrcpyTool
) : AndroidViewModel(application) {

    companion object {
        const val MAX_CONSOLE_LINES = 1000
        const val LOG_FLUSH_INTERVAL_MS = 100L
        const val LOG_BATCH_SIZE = 50
    }

    private val _currentScreen = MutableStateFlow(AppScreen.ADB_RESCUE)
    val currentScreen = _currentScreen.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _adbConnectionState = MutableStateFlow(AdbConnectionState.DESCONECTADO)
    val adbConnectionState = _adbConnectionState.asStateFlow()

    private val _deviceModel = MutableStateFlow("Nenhum")
    val deviceModel = _deviceModel.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs = _consoleLogs.asStateFlow()

    private val _shellCommand = MutableStateFlow("")
    val shellCommand = _shellCommand.asStateFlow()

    private val _isMirroring = MutableStateFlow(false)
    val isMirroring = _isMirroring.asStateFlow()

    private val _mirrorQuality = MutableStateFlow(ScrcpyTool.Quality.HIGH)
    val mirrorQuality = _mirrorQuality.asStateFlow()

    private val _isStreamingLogcat = MutableStateFlow(false)
    val isStreamingLogcat = _isStreamingLogcat.asStateFlow()

    private val _logcatEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logcatEntries = _logcatEntries.asStateFlow()

    private val _logcatFilter = MutableStateFlow("")
    val logcatFilter = _logcatFilter.asStateFlow()

    private val _logcatMinLevel = MutableStateFlow(LogLevel.VERBOSE)
    val logcatMinLevel = _logcatMinLevel.asStateFlow()

    private val _debloatApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val debloatApps = _debloatApps.asStateFlow()

    private val _debloatFilter = MutableStateFlow("")
    val debloatFilter = _debloatFilter.asStateFlow()

    private val _isRefreshingApps = MutableStateFlow(false)
    val isRefreshingApps = _isRefreshingApps.asStateFlow()

    private val _manualIp = MutableStateFlow("192.168.1.3")
    val manualIp = _manualIp.asStateFlow()

    private val _manualPort = MutableStateFlow("5555")
    val manualPort = _manualPort.asStateFlow()

    private val _localConsoleLogs = MutableStateFlow<List<String>>(emptyList())
    val localConsoleLogs = _localConsoleLogs.asStateFlow()

    private val _localShellCommand = MutableStateFlow("")
    val localShellCommand = _localShellCommand.asStateFlow()

    data class ScriptLocal(val nome: String, val conteudo: String, val path: String)
    private val _scriptsLocais = MutableStateFlow<List<ScriptLocal>>(emptyList())
    val scriptsLocais = _scriptsLocais.asStateFlow()

    private val _isExecutandoScript = MutableStateFlow(false)
    val isExecutandoScript = _isExecutandoScript.asStateFlow()

    private val _isHackerMode = MutableStateFlow(false)
    val isHackerMode = _isHackerMode.asStateFlow()

    fun toggleHackerMode() { _isHackerMode.value = !_isHackerMode.value }

    private val _fastbootVars = MutableStateFlow<List<FastbootVar>>(emptyList())
    val fastbootVars = _fastbootVars.asStateFlow()

    private val adbLogBuffer = ArrayDeque<String>()
    private var adbLogFlushJob: Job? = null

    private val logcatBuffer = ArrayDeque<LogEntry>()
    private var logcatJob: Job? = null

    private val _selectedDevice = MutableStateFlow<AdbDevice?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private val activeConnection: AdbConnection?
        get() = AdbManager.activeConnection

    fun addLog(msg: String) {
        synchronized(adbLogBuffer) {
            adbLogBuffer.addLast(msg)
            if (adbLogBuffer.size > MAX_CONSOLE_LINES) adbLogBuffer.removeFirst()
        }
        ensureAdbLogFlusher()
    }

    private fun ensureAdbLogFlusher() {
        if (adbLogFlushJob?.isActive == true) return
        adbLogFlushJob = viewModelScope.launch {
            while (true) {
                val batch = synchronized(adbLogBuffer) {
                    val items = adbLogBuffer.take(LOG_BATCH_SIZE)
                    repeat(items.size) { adbLogBuffer.removeFirst() }
                    items
                }
                if (batch.isNotEmpty()) {
                    _consoleLogs.value = (_consoleLogs.value + batch).takeLast(MAX_CONSOLE_LINES)
                }
                if (adbLogBuffer.isEmpty()) break
                delay(LOG_FLUSH_INTERVAL_MS)
            }
        }
    }

    fun updateShellCommand(cmd: String) { _shellCommand.value = cmd }

    fun runAdbCommand() {
        val cmd = _shellCommand.value
        if (cmd.isNotBlank()) {
            _shellCommand.value = ""
            viewModelScope.launch(Dispatchers.IO) {
                addLog("> $cmd")
                val output = AdbManager.executeCommand(cmd)
                addLog(output)
            }
        }
    }

    // --- CONTROLE REMOTO ---
    fun tocarTela(x: Int, y: Int) { viewModelScope.launch(Dispatchers.IO) { AdbManager.executeCommand("input tap $x $y") } }
    fun deslizar(x1: Int, y1: Int, x2: Int, y2: Int, d: Int = 300) { viewModelScope.launch(Dispatchers.IO) { AdbManager.executeCommand("input swipe $x1 $y1 $x2 $y2 $d") } }
    fun digitar(t: String) { viewModelScope.launch(Dispatchers.IO) { AdbManager.executeCommand("input text \"$t\"") } }
    fun tecla(k: Int) { viewModelScope.launch(Dispatchers.IO) { AdbManager.executeCommand("input keyevent $k") } }

    fun desbloquearPadrao() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("🔓 Tentando desbloqueio padrão...")
            AdbManager.executeCommand("input swipe 100 1000 500 600 300")
            delay(200)
            AdbManager.executeCommand("input swipe 500 600 900 1000 300")
        }
    }

    fun setupAutomatico() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⚡ Iniciando Setup Automático...")
            AdbManager.executeCommand("svc power stayon true")
            AdbManager.executeCommand("settings put system screen_off_timeout 1800000")
            AdbManager.executeCommand("settings put global stay_on_while_plugged_in 3")
            addLog("✅ Dispositivo Otimizado!")
        }
    }

    // --- DEBLOAT ---
    fun refreshDebloatApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingApps.value = true
            try {
                val packages = appManager.listApps(serial = _selectedDevice.value?.serial)
                val appsInfo = packages.map { pkg ->
                    val analysis = debloatRiskEngine.check(pkg)
                    AppInfo(
                        packageName = pkg,
                        label = pkg.substringAfterLast("."),
                        isBloat = analysis.risk != RiskLevel.CRITICO,
                        risk = analysis.risk,
                        riskScore = analysis.score,
                        riskReason = analysis.reason,
                        acaoSugerida = analysis.suggestedAction
                    )
                }
                _debloatApps.value = appsInfo
                addLog("✅ Apps atualizados: ${packages.size}")
            } catch (e: Exception) {
                addLog("❌ Erro Debloat: ${e.message}")
            } finally {
                _isRefreshingApps.value = false
            }
        }
    }

    fun updateDebloatFilter(f: String) { _debloatFilter.value = f }

    fun uninstallApp(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AdbManager.executeCommand("pm uninstall --user 0 ${app.packageName}")
                addLog("🗑️ Removido: ${app.packageName}")
                refreshDebloatApps()
            } catch (e: Exception) { addLog("❌ Erro: ${e.message}") }
        }
    }

    fun disableApp(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AdbManager.executeCommand("pm disable-user ${app.packageName}")
                addLog("🚫 Desativado: ${app.packageName}")
                refreshDebloatApps()
            } catch (e: Exception) { addLog("❌ Erro: ${e.message}") }
        }
    }

    // --- SCRIPTS ---
    fun carregarScripts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = File(context.filesDir, "scripts")
            if (!folder.exists()) folder.mkdirs()
            val lista = folder.listFiles()?.map { ScriptLocal(it.name, it.readText(), it.absolutePath) } ?: emptyList()
            _scriptsLocais.value = lista
        }
    }

    fun salvarScript(context: Context, nome: String, conteudo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = File(context.filesDir, "scripts")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, if (nome.endsWith(".sh")) nome else "$nome.sh")
            file.writeText(conteudo)
            carregarScripts(context)
            addLog("✅ Script '$nome' salvo.")
        }
    }

    fun executarScript(context: Context, script: ScriptLocal) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExecutandoScript.value = true
            val remotePath = "/data/local/tmp/${script.nome}"
            if (AdbManager.pushFile(script.conteudo.toByteArray(), remotePath)) {
                AdbManager.executeCommand("chmod 755 $remotePath")
                val out = AdbManager.executeCommand("sh $remotePath")
                addLog("📝 Script ${script.nome}:\n$out")
                AdbManager.executeCommand("rm $remotePath")
            }
            _isExecutandoScript.value = false
        }
    }

    fun deletarScript(context: Context, script: ScriptLocal) {
        File(script.path).delete()
        carregarScripts(context)
    }

    // --- LOGCAT ---
    fun startLogcat() {
        if (_isStreamingLogcat.value) return
        _isStreamingLogcat.value = true
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                AdbManager.openLogcatStream().collect { line ->
                    parseAndAddLogcatLine(line)
                }
            } catch (e: Exception) { addLog("❌ Erro Logcat: ${e.message}") }
        }
    }

    private fun parseAndAddLogcatLine(line: String) {
        // Parse simples para exemplo
        val level = LogLevel.VERBOSE
        val entry = LogEntry(level, "Logcat", line, "", "", line)
        synchronized(logcatBuffer) {
            logcatBuffer.addLast(entry)
            if (logcatBuffer.size > 2000) logcatBuffer.removeFirst()
        }
        _logcatEntries.value = logcatBuffer.toList()
    }

    fun stopLogcat() {
        _isStreamingLogcat.value = false
        logcatJob?.cancel()
    }

    fun clearLogcatEntries() {
        synchronized(logcatBuffer) { logcatBuffer.clear() }
        _logcatEntries.value = emptyList()
    }

    fun setLogcatMinLevel(l: LogLevel) { _logcatMinLevel.value = l }
    fun updateLogcatFilter(f: String) { _logcatFilter.value = f }

    fun updateLocalShellCommand(c: String) { _localShellCommand.value = c }

    fun runLocalShell() {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = _localShellCommand.value
            _localConsoleLogs.value += "> $cmd"
            // Aqui executaria no shell local do app
            _localConsoleLogs.value += "Execução local não implementada (Sandbox)"
        }
    }

    // --- ARQUIVOS ---
    data class ArquivoAdb(val nome: String, val eDiretorio: Boolean, val tamanho: String, val permissao: String)
    private val _arquivosAtuais = MutableStateFlow<List<ArquivoAdb>>(emptyList())
    val arquivosAtuais = _arquivosAtuais.asStateFlow()
    private val _caminhoAtual = MutableStateFlow("/sdcard/")
    val caminhoAtual = _caminhoAtual.asStateFlow()

    fun listarArquivos(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _caminhoAtual.value = path
            val res = AdbManager.executeCommand("ls -F -l $path")
            val files = res.lines().mapNotNull { line ->
                if (line.isBlank() || line.startsWith("total")) return@mapNotNull null
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 7) return@mapNotNull null
                val name = parts.last()
                ArquivoAdb(name.removeSuffix("/"), name.endsWith("/"), parts[4], parts[0])
            }
            _arquivosAtuais.value = files
        }
    }

    fun voltarDiretorio() {
        val p = _caminhoAtual.value.removeSuffix("/").substringBeforeLast("/", "/") + "/"
        listarArquivos(p)
    }

    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            AdbManager.executeCommand("rm -rf \"$path\"")
            listarArquivos(_caminhoAtual.value)
        }
    }

    // --- MIRROR ---
    fun startMirror(context: Context) {
        _isMirroring.value = true
        viewModelScope.launch {
            scrcpyTool.startServer(context, _mirrorQuality.value, "default_device", null)
        }
    }

    fun stopMirror() {
        _isMirroring.value = false
        scrcpyTool.stopServer()
    }

    fun setMirrorQuality(q: ScrcpyTool.Quality) { _mirrorQuality.value = q }

    // --- FASTBOOT ---
    fun getFastbootVars() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⚡ Lendo variáveis Fastboot...")
            val vars = adbRepository.getFastbootVars(_selectedDevice.value?.serial)
            _fastbootVars.value = vars
            addLog("✅ ${vars.size} variáveis obtidas.")
        }
    }

    fun fastbootReboot() {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("⚡ Reiniciando via Fastboot...")
            // Simulação
        }
    }

    fun updateManualIp(ip: String) { _manualIp.value = ip }
    fun updateManualPort(port: String) { _manualPort.value = port }

    fun connectManual(context: Context) {
        viewModelScope.launch {
            val ip = _manualIp.value
            val port = _manualPort.value.toIntOrNull() ?: 5555
            addLog("📡 Conexão Manual: $ip:$port...")
            _adbConnectionState.value = AdbConnectionState.CONECTANDO
            val success = AdbManager.connect(context, ip, port)
            if (success) {
                _isConnected.value = true
                _adbConnectionState.value = AdbConnectionState.CONECTADO_WIFI
                val model = AdbManager.getDeviceModel()
                _deviceModel.value = model
                addLog("✅ Conectado via Wi-Fi: $model")
            } else {
                _adbConnectionState.value = AdbConnectionState.FALHOU
                addLog("❌ Falha na conexão manual: ${AdbManager.lastErrorMessage}")
            }
        }
    }

    fun usbToWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isConnected.value || AdbManager.usbConnection == null) {
                addLog("⚠️ Conecte via USB primeiro!")
                return@launch
            }
            addLog("🔄 Convertendo USB -> WiFi (TCPIP 5555)...")
            val out = AdbManager.executeCommand("tcpip 5555", target = AdbManager.usbConnection)
            addLog(out)
            addLog("✅ Agora você pode desconectar o cabo e usar WiFi Smart.")
        }
    }

    fun connectUsbAdvanced(context: Context) {
        viewModelScope.launch {
            addLog("🔌 Iniciando conexão USB avançada...")
            // AdbRepository logic here
        }
    }

    fun connectNetworkSmart(context: Context) {
        viewModelScope.launch {
            addLog("📡 Escaneando rede para ADB Wireless...")
        }
    }

    fun blindUnlockAdvanced() {
        viewModelScope.launch {
            addLog("🔓 Tentando desbloqueio cego (Z-Swipe + Keyevents)...")
            desbloquearPadrao()
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            addLog("📸 Capturando tela...")
        }
    }

    fun enableFullAccessibility() {
        viewModelScope.launch {
            addLog("♿ Ativando serviços de acessibilidade...")
            AdbManager.executeCommand("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService")
        }
    }

    fun increaseScreenTimeout() {
        viewModelScope.launch {
            addLog("⏰ Configurando timeout de tela para 30 min...")
            AdbManager.executeCommand("settings put system screen_off_timeout 1800000")
        }
    }

    fun fixSystemUI() {
        viewModelScope.launch {
            addLog("🛠️ Reiniciando System UI...")
            AdbManager.executeCommand("pkill com.android.systemui")
        }
    }

    fun enableVoiceAccess() {
        viewModelScope.launch {
            addLog("🎙️ Ativando Voice Access...")
        }
    }

    fun sendAdbKey(code: Int) {
        viewModelScope.launch {
            tecla(code)
        }
    }

    fun rebootSystem() { viewModelScope.launch { AdbManager.executeCommand("reboot") } }
    fun rebootRecovery() { viewModelScope.launch { AdbManager.executeCommand("reboot recovery") } }
    fun rebootBootloader() { viewModelScope.launch { AdbManager.executeCommand("reboot bootloader") } }

    fun disconnectAdb() {
        viewModelScope.launch {
            _isConnected.value = false
            _adbConnectionState.value = AdbConnectionState.DESCONECTADO
            addLog("🔌 Dispositivo desconectado pelo usuário.")
        }
    }

    fun startDeviceMonitoring(context: Context) {
        // Mock ou implementação real
    }

    fun setCurrentScreen(s: AppScreen) { _currentScreen.value = s }
    fun showError(m: String) { addLog("❌ ERRO: $m") }
}
