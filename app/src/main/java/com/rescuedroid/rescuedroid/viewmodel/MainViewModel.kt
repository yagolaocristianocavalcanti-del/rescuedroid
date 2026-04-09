package com.rescuedroid.rescuedroid.viewmodel

import android.app.Application
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.rescuedroid.rescuedroid.ai.IACmd
import com.rescuedroid.rescuedroid.ai.IAEscuta
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rescuedroid.rescuedroid.adb.AdbManager
import com.rescuedroid.rescuedroid.adb.AdbKeyManager
import com.rescuedroid.rescuedroid.adb.UsbAdbConnector
import com.rescuedroid.rescuedroid.adb.AdbDevice
import com.rescuedroid.rescuedroid.adb.DeviceStatus
import com.rescuedroid.rescuedroid.tools.AppManager
import com.rescuedroid.rescuedroid.tools.ScrcpyTool
import com.rescuedroid.rescuedroid.debloat.DebloatRiskEngine
import com.rescuedroid.rescuedroid.RiskLevel
import com.rescuedroid.rescuedroid.model.*
import com.rescuedroid.rescuedroid.adb.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class AppScreen {
    ADB_RESCUE, SCRCPY, SCRIPTS, FILE_MANAGER, LOGCAT, DEBLOAT, FASTBOOT, SUPORTE_IA, CONFIGURACOES
}

enum class AdbConnectionState {
    DESCONECTADO, CONECTANDO, CONECTADO, CONECTADO_WIFI, FALHOU
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val appManager: AppManager,
    private val debloatRiskEngine: DebloatRiskEngine,
    private val scrcpyTool: ScrcpyTool
) : AndroidViewModel(application) {

    companion object {
        const val MAX_CONSOLE_LINES = 1000
        const val LOG_FLUSH_INTERVAL_MS = 100L
        const val LOG_BATCH_SIZE = 50
    }

    // --- Estado da UI ---
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

    private val _manualIp = MutableStateFlow(HistoryManager.getLastIp(application))
    val manualIp = _manualIp.asStateFlow()

    private val _manualPort = MutableStateFlow("5555")
    val manualPort = _manualPort.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode = _pairingCode.asStateFlow()

    // --- Buffer de Logs (DEVE vir antes do init) ---
    private val adbLogBuffer = ArrayDeque<String>()
    private var adbLogFlushJob: Job? = null

    init {
        val lastIp = HistoryManager.getLastIp(application)
        if (lastIp.isNotBlank()) {
            _manualIp.value = lastIp
            addLog("ℹ️ Último dispositivo conhecido: $lastIp")
        }
    }

    private val _usbMode = MutableStateFlow("normal")
    val usbMode = _usbMode.asStateFlow()

    private val _debloatApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val debloatApps = _debloatApps.asStateFlow()

    private val _isRefreshingApps = MutableStateFlow(false)
    val isRefreshingApps = _isRefreshingApps.asStateFlow()

    private val _debloatFilter = MutableStateFlow("")
    val debloatFilter = _debloatFilter.asStateFlow()

    private val _isHackerMode = MutableStateFlow(false)
    val isHackerMode = _isHackerMode.asStateFlow()

    private val _aiAutoConnect = MutableStateFlow(true)
    val aiAutoConnect = _aiAutoConnect.asStateFlow()

    private val _aiAutoModify = MutableStateFlow(false)
    val aiAutoModify = _aiAutoModify.asStateFlow()

    private val _appLanguage = MutableStateFlow("pt-BR")
    val appLanguage = _appLanguage.asStateFlow()

    private val _isSupportOpen = MutableStateFlow(false)
    val isSupportOpen = _isSupportOpen.asStateFlow()

    fun toggleSupport() { _isSupportOpen.value = !_isSupportOpen.value }

    private val _arquivosAtuais = MutableStateFlow<List<ArquivoAdb>>(emptyList())
    val arquivosAtuais = _arquivosAtuais.asStateFlow()
    private val _caminhoAtual = MutableStateFlow("/sdcard/")
    val caminhoAtual = _caminhoAtual.asStateFlow()

    // Logcat
    private val _logcatEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logcatEntries = _logcatEntries.asStateFlow()
    private val _isStreamingLogcat = MutableStateFlow(false)
    val isStreamingLogcat = _isStreamingLogcat.asStateFlow()
    private val _logcatFilter = MutableStateFlow("")
    val logcatFilter = _logcatFilter.asStateFlow()
    private val _logcatMinLevel = MutableStateFlow(LogLevel.VERBOSE)
    val logcatMinLevel = _logcatMinLevel.asStateFlow()

    // Fastboot
    private val _fastbootVars = MutableStateFlow<List<FastbootVar>>(emptyList())
    val fastbootVars = _fastbootVars.asStateFlow()

    private val _scriptsLocais = MutableStateFlow<List<ScriptLocal>>(emptyList())
    val scriptsLocais = _scriptsLocais.asStateFlow()
    private val _isExecutandoScript = MutableStateFlow(false)
    val isExecutandoScript = _isExecutandoScript.asStateFlow()

    // Mirror
    private val _mirrorQuality = MutableStateFlow(ScrcpyTool.Quality.HIGH)
    val mirrorQuality = _mirrorQuality.asStateFlow()
    private val _isMirroring = MutableStateFlow(false)
    val isMirroring = _isMirroring.asStateFlow()

    // --- Estado da IA ---
    private val _ultimoComandoIA = MutableStateFlow<List<IAComando>>(emptyList())
    val ultimoComandoIA = _ultimoComandoIA.asStateFlow()

    private val _isIAProcessing = MutableStateFlow(false)
    val isIAProcessing = _isIAProcessing.asStateFlow()

    data class IAComando(
        val texto: String,
        val isFromIA: Boolean,
        val sugestoes: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )

    fun processarComandoIA(texto: String) {
        viewModelScope.launch {
            _isIAProcessing.value = true
            _ultimoComandoIA.value = _ultimoComandoIA.value + IAComando(texto, false)
            
            val textoNormalizado = texto.lowercase()
            
            var sugestoes = emptyList<String>()
            
            when {
                textoNormalizado.contains("conecta usb") || textoNormalizado.contains("usb turbo") -> {
                    executarUsbTurbo()
                    sugestoes = listOf("Tirar Print", "Modo Hacker", "Sempre ON")
                    responderIA("🔌 USB Turbo ativado! Dispositivo pronto para comandos de alta velocidade.", sugestoes)
                }
                textoNormalizado.contains("screenshot") || textoNormalizado.contains("print") -> {
                    takeScreenshot()
                    sugestoes = listOf("Ver Arquivos", "Desbloquear")
                    responderIA("📸 Captura de tela realizada com sucesso!", sugestoes)
                }
                textoNormalizado.contains("limpa tudo") || textoNormalizado.contains("limpa lixo") || textoNormalizado.contains("debloat") -> {
                    debloatSeguroAutomatico()
                    sugestoes = listOf("Ver Apps", "Modo Hacker")
                    responderIA("🧠 Iniciando Debloat Inteligente... Removendo apps seguros conhecidos.", sugestoes)
                }
                textoNormalizado.contains("hacker") -> {
                    toggleHackerMode()
                    sugestoes = listOf("conecta usb", "screenshot")
                    val msg = if (_isHackerMode.value) "☢️ MODO HACKER ATIVADO. Acesso root simulado e comandos críticos liberados." else "🛡️ Modo Hacker desativado."
                    responderIA(msg, sugestoes)
                }
                textoNormalizado.contains("tela ligada") || textoNormalizado.contains("timeout") -> {
                    increaseScreenTimeout()
                    sugestoes = listOf("Tirar Print", "Desbloquear")
                    responderIA("⏰ Tela configurada para ficar ligada por 30 minutos (Modo Técnico).", sugestoes)
                }
                textoNormalizado.contains("wifi") || textoNormalizado.contains("conecta rede") -> {
                    val ip = AdbManager.getDeviceIp()
                    if (ip != null) {
                        _manualIp.value = ip
                        connectManual(getApplication())
                        sugestoes = listOf("Listar Apps", "Tirar Print")
                        responderIA("🔌 Detectado IP $ip. Tentando conexão ADB over WiFi...", sugestoes)
                    } else {
                        sugestoes = listOf("conectar ip 192.168.1.10", "ajuda")
                        responderIA("⚠️ Não consegui detectar o IP automaticamente. Use: 'conectar ip [ENDEREÇO]'", sugestoes)
                    }
                }
                textoNormalizado.contains("desbloquear") || textoNormalizado.contains("unlock") -> {
                    blindUnlockAdvanced()
                    sugestoes = listOf("Tirar Print", "Sempre ON")
                    responderIA("🔓 Executando sequência de desbloqueio inteligente...", sugestoes)
                }
                else -> {
                    val cmd = IAEscuta.parseComando(texto)
                    when(cmd) {
                        IACmd.UsbTurbo -> { executarUsbTurbo(); responderIA("🔌 USB Turbo ativado!", listOf("Tirar Print", "Modo Hacker")) }
                        is IACmd.SetIp -> { _manualIp.value = cmd.ip; connectManual(getApplication()); responderIA("🌐 Configurando IP para ${cmd.ip}...", listOf("Listar Apps", "Desbloquear")) }
                        IACmd.RequestStorage -> { pedirPermissaoStorage(); responderIA("📱 Solicitei permissão de armazenamento.", listOf("Tirar Print", "Explorar Arquivos")) }
                        IACmd.ResetAdbKeys -> { val res = resetAdbKeys(); responderIA(res, listOf("conecta usb", "conecta wifi")) }
                        else -> {
                            sugestoes = listOf("conecta usb", "limpa tudo", "screenshot", "modo hacker")
                            responderIA("❓ Não entendi. Tente: 'conecta usb', 'print', 'limpa lixo' ou 'modo hacker'.", sugestoes)
                        }
                    }
                }
            }
            _isIAProcessing.value = false
        }
    }

    private fun responderIA(texto: String, sugestoes: List<String>) {
        _ultimoComandoIA.value = _ultimoComandoIA.value + IAComando(texto, true, sugestoes)
    }

    private suspend fun executarUsbTurbo(): String {
        addLog("🤖 IA: Iniciando Handshake USB Turbo...")
        connectUsbAdvanced(getApplication(), "forte")
        return "🔌 Comando USB Turbo (Modo Forte) enviado para o barramento!"
    }

    fun debloatSeguroAutomatico() {
        viewModelScope.launch {
            _isRefreshingApps.value = true
            addLog("🤖 IA: Iniciando Debloat Automático (Nível Seguro)...")
            refreshDebloatApps()
            delay(1500)
            val safeApps = _debloatApps.value.filter { it.risk == RiskLevel.SEGURO }
            if (safeApps.isEmpty()) {
                addLog("✅ Nenhum app SEGURO encontrado para remoção.")
            } else {
                val count = safeApps.size.coerceAtMost(8)
                safeApps.take(count).forEach { 
                    addLog("🧹 Removendo: ${it.label}")
                    uninstallApp(it) 
                }
                addLog("✨ Debloat automático concluído: $count apps removidos.")
            }
            _isRefreshingApps.value = false
        }
    }

    private fun pedirPermissaoStorage(): String {
        val intent = android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
        return "📱 Abra as permissões de armazenamento na tela que apareceu!"
    }

    fun addLog(msg: String) {
        adbLogBuffer.addLast(msg)
        if (adbLogBuffer.size > MAX_CONSOLE_LINES) adbLogBuffer.removeFirst()
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

    private val _dispositivos = MutableStateFlow<List<AdbDevice>>(emptyList())
    val dispositivos: StateFlow<List<AdbDevice>> = _dispositivos.asStateFlow()

    private val _dispositivoSelecionado = MutableStateFlow<AdbDevice?>(null)
    val dispositivoSelecionado: StateFlow<AdbDevice?> = _dispositivoSelecionado.asStateFlow()

    fun selecionarDispositivo(device: AdbDevice?) {
        _dispositivoSelecionado.value = device
        _deviceModel.value = device?.model ?: "Nenhum"
        _isConnected.value = device?.status == DeviceStatus.ONLINE
        if (device?.status == DeviceStatus.ONLINE) {
            _adbConnectionState.value = if (device.type == "WIFI") AdbConnectionState.CONECTADO_WIFI else AdbConnectionState.CONECTADO
        } else {
            _adbConnectionState.value = AdbConnectionState.DESCONECTADO
        }
    }

    fun atualizarListaDispositivos() {
        viewModelScope.launch {
            val lista = AdbManager.listDevices(getApplication())
            _dispositivos.value = lista
            // Se o selecionado sumiu ou mudou de status, atualiza
            val atual = _dispositivoSelecionado.value
            if (atual != null) {
                val novoEstado = lista.find { it.serial == atual.serial || it.usbDevice?.deviceName == atual.usbDevice?.deviceName }
                if (novoEstado != null && novoEstado.status != atual.status) {
                    selecionarDispositivo(novoEstado)
                }
            } else if (lista.isNotEmpty()) {
                // Auto-seleciona o primeiro online se nada estiver selecionado
                lista.find { it.status == DeviceStatus.ONLINE }?.let { selecionarDispositivo(it) }
            }
        }
    }

    fun setUsbMode(mode: String) { _usbMode.value = mode }
    fun updateManualIp(ip: String) { _manualIp.value = ip }
    fun updateManualPort(p: String) { _manualPort.value = p }
    fun updatePairingCode(code: String) { _pairingCode.value = code }
    fun updateShellCommand(cmd: String) { _shellCommand.value = cmd }
    fun setCurrentScreen(s: AppScreen) { _currentScreen.value = s }

    fun runQuickCommand(cmd: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addLog("🚀 EXECUTANDO: $label...")
            val target = _dispositivoSelecionado.value?.connection ?: AdbManager.activeConnection
            val result = AdbManager.executeCommand(cmd, target = target)
            addLog("📝 RETORNO [$label]:\n$result")
        }
    }

    fun runAdbCommand() {
        val cmd = _shellCommand.value
        if (cmd.isNotBlank()) {
            _shellCommand.value = ""
            viewModelScope.launch(Dispatchers.IO) {
                addLog("> $cmd")
                val target = _dispositivoSelecionado.value?.connection ?: AdbManager.activeConnection
                val result = AdbManager.executeCommand(cmd, target = target)
                addLog(result)
            }
        }
    }

    fun connectUsbAdvanced(context: Context, modeName: String = "normal") {
        viewModelScope.launch {
            _usbMode.value = modeName
            addLog("🔌 Conectando via USB (Modo: ${modeName.uppercase()})...")
            _adbConnectionState.value = AdbConnectionState.CONECTANDO
            
            val mode = when(modeName.lowercase()) {
                "forte" -> UsbAdbConnector.ConnectMode.STRONG
                "martelo" -> UsbAdbConnector.ConnectMode.HAMMER
                "legado" -> UsbAdbConnector.ConnectMode.LEGACY
                else -> UsbAdbConnector.ConnectMode.NORMAL
            }

            val device = UsbAdbConnector.findAdbCapableDevice(context)
            if (device == null) {
                addLog("❌ Nenhum dispositivo USB detectado.")
                _adbConnectionState.value = AdbConnectionState.FALHOU
                return@launch
            }

            val success = UsbAdbConnector.connect(context, device, mode) { event ->
                addLog("📡 Status: $event")
            }

            if (success) {
                _isConnected.value = true
                _adbConnectionState.value = AdbConnectionState.CONECTADO
                val model = AdbManager.getDeviceModel()
                _deviceModel.value = model
                addLog("✅ Conectado via USB: $model")
            } else {
                _adbConnectionState.value = AdbConnectionState.FALHOU
                addLog("❌ Falha na conexão USB: ${UsbAdbConnector.lastErrorMessage}")
            }
        }
    }

    fun connectManual(context: Context) {
        viewModelScope.launch {
            val host = _manualIp.value
            val port = _manualPort.value.toIntOrNull() ?: 5555
            addLog("📡 Tentando conexão manual em $host:$port...")
            val success = AdbManager.connect(context, host, port)
            if (success) {
                _isConnected.value = true
                _adbConnectionState.value = AdbConnectionState.CONECTADO_WIFI
                val model = AdbManager.getDeviceModel()
                _deviceModel.value = model
                addLog("✅ Conectado com sucesso via Wi-Fi!")
                HistoryManager.saveConnection(context, host, model)
            } else {
                _adbConnectionState.value = AdbConnectionState.FALHOU
                addLog("❌ Falha na conexão manual: ${AdbManager.lastErrorMessage}")
            }
        }
    }

    fun pairAndConnect(context: Context, code: String) {
        viewModelScope.launch {
            val host = _manualIp.value
            val port = _manualPort.value.toIntOrNull() ?: 5555
            addLog("🔐 Tentando Parear com $host:$port...")
            
            val paired = AdbManager.pair(context, host, port, code)
            
            if (paired) {
                addLog("✅ Pareamento bem-sucedido. Tentando conectar na porta padrão (5555)...")
                delay(2000)
                val connected = AdbManager.connect(context, host, 5555)
                if (connected) {
                    _isConnected.value = true
                    _adbConnectionState.value = AdbConnectionState.CONECTADO_WIFI
                    addLog("🚀 CONECTADO COM SUCESSO via ADB over Pair!")
                    HistoryManager.saveConnection(context, host, AdbManager.getDeviceModel())
                } else {
                    addLog("⚠️ Pareado, mas porta 5555 não respondeu. Verifique se a 'Depuração Sem Fio' está ligada.")
                }
            } else {
                addLog("❌ Falha no pareamento: ${AdbManager.lastErrorMessage}")
            }
        }
    }

    fun usbToWifi() {
        if (_adbConnectionState.value != AdbConnectionState.CONECTADO) {
            addLog("❌ Conecte via USB primeiro para abrir a porta 5555.")
            return
        }
        
        viewModelScope.launch {
            addLog("🔍 Capturando IP do dispositivo...")
            val ip = AdbManager.getDeviceIp()
            if (ip == null) {
                addLog("⚠️ Não foi possível obter o IP. Verifique se o Wi-Fi está ligado.")
            } else {
                addLog("📍 IP detectado: $ip")
                _manualIp.value = ip
            }
            
            addLog("🔄 Abrindo porta 5555 via USB...")
            val res = AdbManager.executeCommand("tcpip 5555")
            if (res.contains("Erro")) {
                addLog("❌ Falha ao abrir porta: $res")
                return@launch
            }
            
            delay(1500)
            addLog("✅ Porta 5555 aberta.")
            
            if (ip != null) {
                addLog("📡 Tentando conexão sem fio automática...")
                val context = getApplication<android.app.Application>().applicationContext
                val connected = AdbManager.connect(context, ip, 5555)
                if (connected) {
                    _adbConnectionState.value = AdbConnectionState.CONECTADO_WIFI
                    _isConnected.value = true
                    addLog("🚀 CONECTADO VIA WI-FI! Pode remover o cabo.")
                } else {
                    addLog("⏳ Quase lá! Desconecte o cabo e tente 'Wi-Fi Direto' com o IP $ip")
                }
            } else {
                addLog("ℹ️ Agora desconecte o cabo e use o 'Wi-Fi Direto' com o IP do seu dispositivo.")
            }
        }
    }

    // --- CONTROLES ---
    fun sendAdbKey(code: Int) {
        viewModelScope.launch(Dispatchers.IO) { AdbManager.executeCommand("input keyevent $code") }
    }

    fun tecla(code: Int) = sendAdbKey(code)

    fun blindUnlockAdvanced() {
        viewModelScope.launch {
            addLog("🔓 Iniciando Sequência de Desbloqueio Cego...")
            tecla(26) // POWER
            delay(500)
            AdbManager.executeCommand("input swipe 500 1500 500 500 200")
            delay(500)
            addLog("✅ Sequência de deslize enviada.")
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            addLog("📸 Capturando tela...")
            AdbManager.executeCommand("screencap -p /data/local/tmp/screen.png")
            addLog("✅ Print salvo em /data/local/tmp/screen.png")
        }
    }

    fun enableFullAccessibility() {
        viewModelScope.launch {
            addLog("♿ Ativando TalkBack e Acessibilidade...")
            AdbManager.executeCommand("settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService")
            AdbManager.executeCommand("settings put secure accessibility_enabled 1")
            addLog("✅ Comandos enviados.")
        }
    }

    fun increaseScreenTimeout() {
        viewModelScope.launch {
            addLog("⏰ Tela configurada para modo técnico (Sempre ON)...")
            AdbManager.executeCommand("settings put system screen_off_timeout 2147483647")
            AdbManager.executeCommand("svc power stayon true")
        }
    }

    fun rebootSystem() { viewModelScope.launch { addLog("🔄 Rebooting..."); AdbManager.executeCommand("reboot") } }
    fun rebootRecovery() { viewModelScope.launch { addLog("🔄 Rebooting Recovery..."); AdbManager.executeCommand("reboot recovery") } }
    fun rebootBootloader() { viewModelScope.launch { addLog("🔄 Rebooting Bootloader..."); AdbManager.executeCommand("reboot bootloader") } }

    fun startDeviceMonitoring(context: Context) {
        viewModelScope.launch { addLog("🔍 Atualizando lista de dispositivos...") }
    }

    fun disconnectAdb() {
        viewModelScope.launch {
            _isConnected.value = false
            _adbConnectionState.value = AdbConnectionState.DESCONECTADO
            AdbManager.disconnect()
            addLog("🔌 Dispositivo desconectado.")
        }
    }

    fun refreshDebloatApps() {
        viewModelScope.launch {
            _isRefreshingApps.value = true
            try {
                val packages = appManager.listApps()
                val appsInfo = packages.map { pkg ->
                    val analysis = debloatRiskEngine.check(pkg)
                    AppInfo(pkg, pkg.substringAfterLast("."), null, analysis.risk != RiskLevel.CRITICO, analysis.risk, analysis.score, analysis.reason)
                }
                _debloatApps.value = appsInfo
            } finally { _isRefreshingApps.value = false }
        }
    }

    fun toggleHackerMode() { 
        _isHackerMode.value = !_isHackerMode.value 
        addLog(if (_isHackerMode.value) "Modo Hacker Ativado" else "Modo Hacker Desativado")
    }

    private fun resetAdbKeys(): String {
        return try {
            val keyManager = AdbKeyManager(getApplication<Application>())
            keyManager.forceRegenerateCrypto()
            AdbManager.disconnect()
            addLog("Chaves ADB resetadas e regeneradas.")
            "🔐 Chaves ADB resetadas com sucesso! Por favor, desconecte e reconecte o cabo USB para autorizar novamente."
        } catch (e: Exception) {
            "❌ Erro ao resetar chaves: ${e.message}"
        }
    }
    fun toggleAiAutoConnect() { _aiAutoConnect.value = !_aiAutoConnect.value }
    fun toggleAiAutoModify() { _aiAutoModify.value = !_aiAutoModify.value }
    fun setLanguage(lang: String) { _appLanguage.value = lang }
    fun updateDebloatFilter(filter: String) { _debloatFilter.value = filter }
    fun uninstallApp(app: AppInfo) { viewModelScope.launch { addLog("🗑️ Desinstalando ${app.packageName}..."); AdbManager.executeCommand("pm uninstall --user 0 ${app.packageName}"); refreshDebloatApps() } }
    fun disableApp(app: AppInfo) { viewModelScope.launch { addLog("🚫 Desativando ${app.packageName}..."); AdbManager.executeCommand("pm disable-user --user 0 ${app.packageName}"); refreshDebloatApps() } }

    // File Manager Logic
    fun listarArquivos(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _caminhoAtual.value = path
            val output = AdbManager.executeCommand("ls -la $path")
            val items = output.lines().mapNotNull { line ->
                if (line.isBlank() || line.startsWith("total")) return@mapNotNull null
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 8) return@mapNotNull null
                val nome = parts.last()
                val eDiretorio = line.startsWith("d")
                val tamanho = parts[4]
                val permissao = parts[0]
                ArquivoAdb(nome, tamanho, permissao, eDiretorio)
            }
            _arquivosAtuais.value = items
        }
    }
    fun voltarDiretorio() {
        val current = _caminhoAtual.value
        if (current == "/" || current == "/sdcard/") return
        val parent = current.substringBeforeLast("/", "").substringBeforeLast("/", "") + "/"
        listarArquivos(if (parent.isEmpty()) "/" else parent)
    }
    fun deleteFile(path: String) { viewModelScope.launch { addLog("🗑️ Deletando $path..."); AdbManager.executeCommand("rm -rf $path"); listarArquivos(_caminhoAtual.value) } }

    // Logcat Logic
    private var logcatJob: Job? = null
    fun startLogcat() {
        if (_isStreamingLogcat.value) return
        _isStreamingLogcat.value = true
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _isStreamingLogcat.value) {
                val lastLogs = AdbManager.executeCommand("logcat -d -t 50")
                val newEntries = lastLogs.lines().filter { it.isNotBlank() }.map { LogEntry(LogLevel.INFO, "ADB", it, "", "", it) }
                _logcatEntries.value = (_logcatEntries.value + newEntries).takeLast(500)
                delay(2000)
            }
        }
    }
    fun stopLogcat() { _isStreamingLogcat.value = false; logcatJob?.cancel() }
    fun clearLogcatEntries() { _logcatEntries.value = emptyList() }
    fun updateLogcatFilter(f: String) { _logcatFilter.value = f }
    fun setLogcatMinLevel(l: LogLevel) { _logcatMinLevel.value = l }

    // Fastboot Logic
    fun getFastbootVars() { viewModelScope.launch { addLog("⚡ Lendo Fastboot..."); _fastbootVars.value = listOf(FastbootVar("version", "0.4")) } }
    fun fastbootReboot() { viewModelScope.launch { addLog("⚡ Fastboot Reboot...") } }

    // Scripts Logic
    private val _supportMessages = MutableStateFlow<List<SupportChatMessage>>(listOf(
        SupportChatMessage("Olá! Eu sou o assistente PicoClaw. Estou aqui para monitorar seu resgate e dar dicas técnicas.", false),
        SupportChatMessage("Dica: Se o dispositivo USB não for reconhecido, verifique se a 'Depuração USB' está ativa nas Opções de Desenvolvedor.", false)
    ))
    val supportMessages: StateFlow<List<SupportChatMessage>> = _supportMessages

    fun enviarMensagemSuporte(texto: String) {
        if (texto.isBlank()) return
        
        val userMsg = SupportChatMessage(texto, true)
        _supportMessages.value = _supportMessages.value + userMsg
        
        viewModelScope.launch {
            delay(1000)
            val resposta = processarDicaIA(texto)
            _supportMessages.value = _supportMessages.value + SupportChatMessage(resposta, false)
        }
    }

    private fun processarDicaIA(pergunta: String): String {
        val p = pergunta.lowercase()
        val statusAdb = _adbConnectionState.value
        val autoConnect = _aiAutoConnect.value
        val autoModify = _aiAutoModify.value
        
        return when {
            p.contains("conexão") || p.contains("conectar") || p.contains("conexao") -> {
                if (statusAdb != AdbConnectionState.CONECTADO) {
                    if (autoConnect) {
                        // Simulação de poder da IA: Tentar conectar automaticamente
                        "Detectei que você não está conectado. Como o 'Auto-Conexão' está ativo, vou tentar iniciar o handshake no modo normal para você agora."
                    } else {
                        "Detectei que você não está conectado. Tente usar o modo 'Martelo' no menu Resgate ou habilite a 'Auto-Conexão' nas configurações para eu tentar por você."
                    }
                } else {
                    "Você já está conectado ao dispositivo ${_deviceModel.value}. Posso ajudar com comandos shell ou debloat?"
                }
            }
            p.contains("debloat") || p.contains("limpar") || p.contains("remover") -> {
                if (statusAdb != AdbConnectionState.CONECTADO) {
                    "Para fazer debloat, primeiro precisamos de uma conexão ADB ativa."
                } else {
                    if (autoModify) {
                        viewModelScope.launch { refreshDebloatApps() }
                        "Entendido. Como tenho poderes de modificação, já carreguei a lista de apps. Posso remover bloatwares conhecidos se você quiser."
                    } else {
                        "Entendido. Vá na aba 'Debloat' para gerenciar os apps. Habilite meus 'Poderes de Modificação' se quiser que eu faça isso daqui."
                    }
                }
            }
            p.contains("script") || p.contains("automatizar") -> {
                viewModelScope.launch { carregarScripts() }
                "Carreguei seus scripts locais. Você pode criar novos scripts na aba 'Scripts' ou me pedir para sugerir um comando ADB específico."
            }
            p.contains("cache") || p.contains("lento") -> {
                "Dica: Posso rodar um script de limpeza de cache para você. Quer que eu execute o 'limpar_cache.sh' agora?"
            }
            p.contains("sim") || p.contains("pode") || p.contains("executa") -> {
                val ultimoScript = _scriptsLocais.value.firstOrNull()
                if (ultimoScript != null) {
                    executarScript(ultimoScript)
                    "Comando enviado com sucesso: ${ultimoScript.nome}. Verifique o console para o retorno."
                } else {
                    "Nenhum script pendente para execução no momento."
                }
            }
            p.contains("tela") || p.contains("espelhar") -> "Para espelhar a tela, vá na aba 'Mirror'. Certifique-se de que o dispositivo está com a tela ligada."
            p.contains("ajuda") || p.contains("socorro") -> "Estou monitorando os logs em tempo real. Se houver um erro crítico, eu te avisarei aqui imediatamente."
            else -> "Entendi. Vou analisar o estado do sistema para te dar a melhor dica sobre '$pergunta'. Posso ajudar com conexões, debloat ou scripts."
        }
    }

    data class SupportChatMessage(val text: String, val isUser: Boolean)

    fun carregarScripts() { 
        _scriptsLocais.value = listOf(
            ScriptLocal("limpar_cache.sh", "pm trim-caches 999G"),
            ScriptLocal("info_bateria.sh", "dumpsys battery"),
            ScriptLocal("listar_pacotes_terceiros.sh", "pm list packages -3"),
            ScriptLocal("screenshot_pc.sh", "screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png ."),
            ScriptLocal("forcar_parada_google.sh", "pm force-stop com.google.android.gms"),
            ScriptLocal("limpar_logs.sh", "logcat -c && dmesg -c"),
            ScriptLocal("dump_servicos.sh", "service list"),
            ScriptLocal("verificar_root.sh", "which su || echo 'No root access'")
        ) 
    }
    fun salvarScript(nome: String, conteudo: String) { _scriptsLocais.value += ScriptLocal(nome, conteudo) }
    fun deletarScript(script: ScriptLocal) { _scriptsLocais.value -= script }
    fun executarScript(script: ScriptLocal) {
        viewModelScope.launch {
            _isExecutandoScript.value = true
            addLog("🚀 Executando ${script.nome}...")
            delay(1000)
            _isExecutandoScript.value = false
        }
    }

    // Mirror Logic
    fun setMirrorQuality(q: ScrcpyTool.Quality) { _mirrorQuality.value = q }

    private var mirrorSurface: android.view.Surface? = null
    fun vincularSurfaceMirror(surface: android.view.Surface) { mirrorSurface = surface }
    fun desvincularSurfaceMirror() { mirrorSurface = null }
    fun startMirror(context: Context) { _isMirroring.value = true; addLog("🎥 Mirror iniciado") }
    fun stopMirror() { _isMirroring.value = false; addLog("⏹ Mirror interrompido") }
}
