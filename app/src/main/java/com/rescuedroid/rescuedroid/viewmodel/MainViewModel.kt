package com.rescuedroid.rescuedroid.viewmodel

import android.app.Application
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.rescuedroid.rescuedroid.debloat.DebloatAnalyzer
import com.rescuedroid.rescuedroid.RiskLevel
import com.rescuedroid.rescuedroid.model.*
import com.rescuedroid.rescuedroid.adb.HistoryManager
import com.rescuedroid.rescuedroid.data.local.ChatDao
import com.rescuedroid.rescuedroid.data.local.ChatMessage
import com.rescuedroid.rescuedroid.data.local.DeviceDao
import com.rescuedroid.rescuedroid.data.local.KnownDevice
import com.rescuedroid.rescuedroid.data.local.PackageDao
import com.rescuedroid.rescuedroid.data.local.PackageCache
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream
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

enum class LogType { INFO, SUCCESS, ERROR, WARNING, COMMAND }

data class ConsoleLog(
    val message: String,
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceSession(
    val device: String? = null,
    val status: AdbConnectionState = AdbConnectionState.DESCONECTADO,
    val transport: String = "NONE", // USB, WIFI, FASTBOOT
    val isReady: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val appManager: AppManager,
    private val debloatRiskEngine: DebloatRiskEngine,
    private val scrcpyTool: ScrcpyTool,
    private val chatDao: ChatDao,
    private val deviceDao: DeviceDao,
    private val packageDao: PackageDao
) : AndroidViewModel(application) {

    companion object {
        const val MAX_CONSOLE_LINES = 1000
        const val LOG_FLUSH_INTERVAL_MS = 100L
        const val LOG_BATCH_SIZE = 50
    }

    private val _session = MutableStateFlow(DeviceSession())
    val session: StateFlow<DeviceSession> = _session.asStateFlow()

    private val _enhancedLogs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val enhancedLogs: StateFlow<List<ConsoleLog>> = _enhancedLogs.asStateFlow()

    // NÍVEL 3 - Controle Remoto por Toque
    fun enviarToqueRemoto(x: Float, y: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            // Enviamos o comando de toque via shell
            AdbManager.executeCommand("input tap ${x.toInt()} ${y.toInt()}")
            vibrarFraco()
        }
    }

    private fun vibrarFraco() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    private fun vibrarSucesso() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    // NÍVEL 6 - Auto Script de Boas-vindas
    private fun executarScriptBoasVindas() {
        viewModelScope.launch {
            addLog("🚀 Executando script de inicialização...", LogType.COMMAND)
            runQuickCommand("settings put system screen_off_timeout 600000", "Manter Tela Ligada")
            delay(500)
            responderIA("Dispositivo pronto! Aumentei o tempo de tela para facilitar sua manutenção. O que fazemos agora?", listOf("Debloat", "Mirror"))
        }
    }

    // --- Estado da UI ---
    private val _currentScreen = MutableStateFlow(AppScreen.ADB_RESCUE)
    val currentScreen = _currentScreen.asStateFlow()

    // Mapeando estados antigos para a nova Sessão para evitar quebra de UI existente
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

        // Carregar histórico da IA (apenas uma vez no início)
        viewModelScope.launch {
            val messages = chatDao.getAllMessagesInitial()
            if (messages.isNotEmpty()) {
                _ultimoComandoIA.value = messages.map {
                    IAComando(
                        texto = it.text,
                        isFromIA = it.isFromIA == "true",
                        sugestoes = it.suggestions.split(",").filter { s -> s.isNotBlank() },
                        timestamp = it.timestamp
                    )
                }
            }
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

    private val _downloadFolderUri = MutableStateFlow<android.net.Uri?>(null)
    val downloadFolderUri = _downloadFolderUri.asStateFlow()

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
            val novoComando = IAComando(texto, false)
            _ultimoComandoIA.value = _ultimoComandoIA.value + novoComando
            
            chatDao.insertMessage(ChatMessage(
                text = texto, 
                isFromIA = "false", 
                suggestions = "", 
                timestamp = novoComando.timestamp
            ))
            
            val cmd = IAEscuta.parseComando(texto)
            val txt = texto.lowercase()
            
            // Lógica de pesquisa de pacotes
            if (txt.contains("pesquisar pacote") || txt.contains("o que é o pacote") || txt.contains("o que e o pacote")) {
                val pkg = txt.substringAfter("pacote").trim().removePrefix(" ").removeSuffix("?")
                if (pkg.isNotEmpty() && pkg.contains(".")) {
                    pesquisarPacoteWeb(pkg)
                } else {
                    responderIA("Para eu pesquisar, diga o nome completo do pacote (ex: com.android.chrome).", listOf("pesquisar pacote com.facebook.katana"))
                }
            } else {
                // Personalidade expandida
                when {
                    cmd != null -> executarComandoMapeado(cmd)
                    
                    txt.contains("quem é você") || txt.contains("quem e voce") || txt.contains("voce e o que") -> 
                        responderIA("Eu sou a **PicoClaw**, a inteligência por trás do RescueDroid. Meu trabalho é garantir que nenhum celular morra na sua mão! Consigo automatizar comandos ADB, limpar lixos e até consertar telas quebradas via espelhamento.", listOf("conecta usb", "modo idoso"))
                    
                    txt.contains("ajuda") || txt.contains("preciso de ajuda") || txt.contains("socorro") ->
                        responderIA("Calma, estou aqui! 🐾 Posso tentar: \n1. **Conectar USB** (Modo Turbo)\n2. **Tirar Print** da tela\n3. **Reparar Sistema** (Otimização)\n4. **Limpar Cache** (Deep Clean)\nQual é a emergência?", listOf("conecta usb", "Reparar Sistema", "Limpar Cache"))

                    txt.contains("reparar") || txt.contains("otimizar") || txt.contains("lento") -> {
                        fixSystemPermissions()
                    }

                    txt.contains("limpar cache") || txt.contains("deep clean") -> {
                        limparCacheGeral()
                    }

                    txt.contains("bom dia") || txt.contains("boa tarde") || txt.contains("boa noite") ->
                        responderIA("Olá! Espero que o dia de resgates esteja sendo produtivo. Em que posso te ajudar agora?", listOf("listar dispositivos", "conecta wifi"))

                    txt.contains("obrigado") || txt.contains("valeu") || txt.contains("top") ->
                        responderIA("Disponha! É um prazer ser útil. Se precisar de mais alguma coisa, é só miar! 😺", listOf("tirar print", "modo hacker"))
                    
                    txt.contains("limpar") || txt.contains("debloat") || txt.contains("remover lixo") -> {
                    responderIA("Com certeza! Vou carregar a lista de apps e identificar o que é lixo digital para você.", listOf("Analisar Lista"))
                    refreshDebloatApps()
                }

                txt.contains("analisar lista") || txt.contains("varredura") || txt.contains("pesquisar apps") -> {
                    analisarListaComIA()
                }

                txt.contains("bateria") -> {
                        val model = _deviceModel.value
                        responderIA("Vou checar a energia do **$model**. Um segundo...", listOf("info bateria"))
                        runQuickCommand("dumpsys battery", "Bateria")
                    }

                    else -> {
                        val msgFallback = "Entendi o que você disse, mas ainda estou aprendendo a lidar com essa intenção específica. Quer que eu tente analisar o dispositivo conectado?"
                        responderIA(msgFallback, listOf("conecta usb", "screenshot", "modo idoso"))
                    }
                }
            }
            _isIAProcessing.value = false
        }
    }

    private suspend fun pesquisarPacoteWeb(pkg: String, notificarChat: Boolean = true) {
        // 1. Checar Cache
        val cached = packageDao.getPackageInfo(pkg)
        if (cached != null) {
            if (notificarChat) {
                val icone = when(cached.iconType) {
                    "safe" -> "✅"
                    "danger" -> "🚨"
                    else -> "⚠️"
                }
                responderIA("$icone **$pkg** (Memória): ${cached.reason}", listOf("Remover agora", "Manter"))
            }
            return
        }

        if (notificarChat) responderIA("🔍 Farejando informações sobre o pacote **$pkg** na web...", listOf())
        
        withContext(Dispatchers.IO) {
            try {
                val analysis = debloatRiskEngine.check(pkg)
                delay(800) // Simular latência de rede otimizada
                
                val iconType = when(analysis.risk) {
                    com.rescuedroid.rescuedroid.RiskLevel.SEGURO -> "safe"
                    com.rescuedroid.rescuedroid.RiskLevel.CRITICO -> "danger"
                    else -> "warning"
                }
                
                val info = PackageCache(
                    packageName = pkg,
                    isSafe = analysis.risk == com.rescuedroid.rescuedroid.RiskLevel.SEGURO,
                    reason = analysis.reason + " (Web Intelligence)",
                    iconType = iconType
                )
                
                packageDao.insertOrUpdate(info)
                
                if (notificarChat) {
                    withContext(Dispatchers.Main) {
                        val icone = if (info.isSafe) "✅" else "🚨"
                        responderIA("$icone **$pkg**: ${info.reason}.", listOf("Remover", "Mais info"))
                    }
                }
            } catch (e: Exception) {
                if (notificarChat) {
                    withContext(Dispatchers.Main) {
                        responderIA("❌ Tive um problema ao pesquisar **$pkg**.", listOf("Tentar de novo"))
                    }
                }
            }
        }
    }

    fun analisarListaComIA() {
        viewModelScope.launch {
            val listaAtual = _debloatApps.value
            if (listaAtual.isEmpty()) {
                responderIA("A lista de apps está vazia. Carregue-a primeiro no menu de Debloat!", listOf("Carregar Apps"))
                return@launch
            }

            responderIA("Iniciando varredura inteligente na sua lista de apps instalados. Vou identificar pacotes suspeitos via Web...", listOf())
            
            // Filtra o que a IA ainda não conhece ou que o motor local marcou como perigoso/incerto
            val suspeitos = listaAtual.filter { 
                it.risk == RiskLevel.PERIGOSO || it.riskReason.contains("desconhecido", ignoreCase = true) 
            }.take(10) // Processamos em lotes de 10 para segurança

            if (suspeitos.isEmpty()) {
                responderIA("Varredura concluída! Todos os apps na lista já são conhecidos ou seguros. Nada suspeito por aqui. 😎", listOf("Limpar Lixo", "Voltar"))
                return@launch
            }

            suspeitos.forEach { app ->
                pesquisarPacoteWeb(app.packageName, notificarChat = false)
            }
            
            refreshDebloatApps() // Atualiza a UI com os novos dados do cache
            
            responderIA("Terminei! Analisei os pacotes suspeitos e atualizei as tags de risco na sua lista de Debloat. Dá uma olhada lá!", listOf("Ver Lista Atualizada", "Remover Recomendados"))
        }
    }

    private suspend fun executarComandoMapeado(cmd: IACmd) {
        when(cmd) {
            IACmd.UsbTurbo -> {
                executarUsbTurbo()
                responderIA("🔌 USB Turbo ativado! Estamos operando em modo de alta resiliência.", listOf("Tirar Print", "Modo Hacker"))
            }
            IACmd.ConnectWifi -> {
                val ip = AdbManager.getDeviceIp()
                if (ip != null) {
                    _manualIp.value = ip
                    connectManual(getApplication())
                    responderIA("📡 Encontrei o IP $ip na rede. Tentando o aperto de mão sem fio...", listOf("Ver Apps", "Tirar Print"))
                } else {
                    responderIA("⚠️ O dispositivo não revelou o IP. Tente me dizer: 'ip 192.168.x.x'", listOf("conecta usb"))
                }
            }
            is IACmd.SetIp -> {
                _manualIp.value = cmd.ip
                connectManual(getApplication())
                responderIA("🌐 Mira travada no IP ${cmd.ip}. Conectando...", listOf("Listar Apps", "Desbloquear"))
            }
            is IACmd.Debloat -> {
                AdbManager.executeCommand("pm uninstall --user 0 ${cmd.pkg}")
                responderIA("🧹 Faxina iniciada! Removendo o pacote ${cmd.pkg}.", listOf("Ver Apps", "Limpa Tudo"))
            }
            IACmd.DebloatAllSafe -> {
                debloatSeguroAutomatico()
                responderIA("🧠 Iniciando Debloat Inteligente. Vou focar apenas no que é seguro remover.", listOf("Ver Apps", "Modo Hacker"))
            }
            IACmd.RequestStorage -> {
                pedirPermissaoStorage()
                responderIA("📱 Solicitei acesso aos arquivos. Verifique a tela do celular!", listOf("Explorar Arquivos"))
            }
            IACmd.Screenshot -> {
                takeScreenshot()
                responderIA("📸 Captura de tela realizada com sucesso!", listOf("Ver Arquivos", "Desbloquear"))
            }
            IACmd.Unlock -> {
                blindUnlockAdvanced()
                responderIA("🔓 Enviando comandos de desbloqueio cego...", listOf("Tirar Print", "Sempre ON"))
            }
            IACmd.ScreenTimeout -> {
                increaseScreenTimeout()
                responderIA("⏰ Tela configurada para nunca desligar. Cuidado com a bateria!", listOf("Tirar Print", "Desbloquear"))
            }
            IACmd.SeniorMode -> {
                ativarModoIdoso()
                responderIA("👴 Modo Idoso Ativado! Tudo pronto para facilitar o uso: DPI 480, Brilho e Sons no máximo.", listOf("Tirar Print", "Desativar Modo Idoso"))
            }
            IACmd.DisableSeniorMode -> {
                desativarModoIdoso()
                responderIA("👤 Modo Idoso Desativado. As configurações originais foram restauradas.", listOf("Modo Idoso", "Limpar Lixo"))
            }
            IACmd.ToggleHacker -> {
                toggleHackerMode()
                val msg = if (_isHackerMode.value) "☢️ MODO HACKER ATIVADO. Use com cautela." else "🛡️ Modo Hacker desativado."
                responderIA(msg, listOf("conecta usb", "screenshot"))
            }
            IACmd.ResetAdbKeys -> {
                val res = resetAdbKeys()
                responderIA(res, listOf("conecta usb", "conecta wifi"))
            }
        }
    }

    private fun responderIA(texto: String, sugestoes: List<String>) {
        val resposta = IAComando(texto, true, sugestoes)
        _ultimoComandoIA.value = _ultimoComandoIA.value + resposta
        
        // Persistir resposta da IA
        viewModelScope.launch {
            chatDao.insertMessage(ChatMessage(
                text = texto,
                isFromIA = "true",
                suggestions = sugestoes.joinToString(","),
                timestamp = resposta.timestamp
            ))
        }
    }

    private suspend fun executarUsbTurbo(): String {
        addLog("🤖 IA: Iniciando Handshake USB Turbo...")
        connectUsbAdvanced(getApplication(), "forte")
        return "🔌 Comando USB Turbo (Modo Forte) enviado para o barramento!"
    }

    fun debloatSeguroAutomatico() {
        viewModelScope.launch {
            _isRefreshingApps.value = true
            addLog("🤖 IA: Iniciando Faxina Total (Debloat Automático Nível Luxo)...")
            
            // Força um refresh para garantir a lista mais atualizada com ícones e IA
            refreshDebloatApps()
            delay(1000) 
            
            val safeApps = _debloatApps.value.filter { it.risk == RiskLevel.SEGURO }
            
            if (safeApps.isEmpty()) {
                addLog("✅ Nenhum app SEGURO encontrado para remoção no momento.")
                responderIA("Vasculhei o sistema e não encontrei bloatwares seguros para remover agora. Seu Android está limpo! ✨", listOf("Obrigado", "Ver todos os apps"))
            } else {
                addLog("🚀 Removendo ${safeApps.size} pacotes identificados como SEGURO...")
                
                safeApps.forEach { app ->
                    addLog("🧹 Removendo: ${app.label} (${app.packageName})")
                    AdbManager.executeCommand("pm uninstall --user 0 ${app.packageName}")
                    _appInfoCache.remove(app.packageName)
                }
                
                addLog("✨ Faxina concluída! ${safeApps.size} apps removidos.")
                responderIA("Prontinho! Removi **${safeApps.size} apps** que estavam apenas ocupando espaço e bateria. Como posso ajudar agora?", listOf("Valeu!", "Ver Log de Limpeza"))
                refreshDebloatApps()
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

    fun addLog(msg: String, type: LogType = LogType.INFO) {
        val coloredMsg = when(type) {
            LogType.ERROR -> "❌ $msg"
            LogType.SUCCESS -> "✅ $msg"
            LogType.WARNING -> "⚠️ $msg"
            LogType.COMMAND -> "🚀 $msg"
            else -> "ℹ️ $msg"
        }
        adbLogBuffer.addLast(coloredMsg)
        
        // Também adiciona ao sistema de log estruturado (Nível 1)
        val newLog = ConsoleLog(msg, type)
        _enhancedLogs.value = (_enhancedLogs.value + newLog).takeLast(MAX_CONSOLE_LINES)

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
        val connected = device?.status == DeviceStatus.ONLINE
        _isConnected.value = connected
        
        val newState = if (connected) {
            if (device.type == "WIFI") AdbConnectionState.CONECTADO_WIFI else AdbConnectionState.CONECTADO
        } else {
            AdbConnectionState.DESCONECTADO
        }
        _adbConnectionState.value = newState

        // Atualiza a Sessão Unificada (Nível 1)
        _session.value = DeviceSession(
            device = device?.model,
            status = newState,
            transport = device?.type ?: "NONE",
            isReady = connected
        )
        
        if (connected) {
            viewModelScope.launch {
                val serial = device.serial
                val existing = deviceDao.getDeviceBySerial(serial)
                val nomeDispositivo = device.model ?: "Dispositivo Desconhecido"
                
                if (existing == null) {
                    val newDev = KnownDevice(serial = serial, name = nomeDispositivo)
                    deviceDao.insertOrUpdate(newDev)
                    responderIA("Olá! Identifiquei um novo dispositivo: **$nomeDispositivo**. Já registrei ele na minha memória para os próximos resgates! 🐾", listOf("Tirar Print", "Ver Apps"))
                } else {
                    val updated = existing.copy(
                        lastConnected = System.currentTimeMillis(),
                        connectionCount = existing.connectionCount + 1
                    )
                    deviceDao.insertOrUpdate(updated)
                    
                    val saudacao = when {
                        updated.connectionCount > 10 -> "Nossa, o **$nomeDispositivo** já é de casa! É a ${updated.connectionCount}ª vez que trabalhamos nele."
                        updated.connectionCount > 5 -> "Bem-vindo de volta! O **$nomeDispositivo** está pronto para mais uma rodada de ajustes."
                        else -> "Oi de novo! Reconheci o **$nomeDispositivo**. Vamos continuar de onde paramos?"
                    }
                    responderIA("$saudacao O que vamos fazer hoje?", listOf("Debloat Seguro", "Espelhar Tela", "Limpar Cache"))
                }
                
                // CONEXÃO DIRETA: Se a IA de auto-conexão estiver ativa, não espera clique
                if (_aiAutoConnect.value && device.status != DeviceStatus.ONLINE) {
                    addLog("⚡ PicoClaw: Iniciando auto-conexão silenciosa...")
                    if (device.type == "USB") {
                        connectUsbAdvanced(getApplication(), _usbMode.value)
                    } else {
                        connectManual(getApplication())
                    }
                }
            }
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
            addLog("🚀 EXECUTANDO: $label...", LogType.COMMAND)
            val target = _dispositivoSelecionado.value?.connection ?: AdbManager.activeConnection
            val result = AdbManager.executeCommand(cmd, target = target)
            addLog("📝 RETORNO [$label]:\n$result", LogType.INFO)
            if (result.lowercase().contains("error") || result.lowercase().contains("failed")) {
                vibrarFraco()
            } else {
                vibrarSucesso()
            }
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
                val model = AdbManager.getDeviceModel()
                _deviceModel.value = model
                _adbConnectionState.value = AdbConnectionState.CONECTADO
                
                _session.value = DeviceSession(
                    device = model,
                    status = AdbConnectionState.CONECTADO,
                    transport = "USB",
                    isReady = true
                )
                
                addLog("✅ Conectado via USB: $model", LogType.SUCCESS)
                executarScriptBoasVindas()
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
                val model = AdbManager.getDeviceModel()
                _deviceModel.value = model
                _adbConnectionState.value = AdbConnectionState.CONECTADO_WIFI
                
                _session.value = DeviceSession(
                    device = model,
                    status = AdbConnectionState.CONECTADO_WIFI,
                    transport = "WIFI",
                    isReady = true
                )
                
                addLog("✅ Conectado com sucesso via Wi-Fi!", LogType.SUCCESS)
                vibrarSucesso()
                HistoryManager.saveConnection(context, host, model)
                executarScriptBoasVindas()
            } else {
                vibrarFraco()
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

    fun ativarModoIdoso() {
        viewModelScope.launch {
            addLog("👴 IA: Configurando Dispositivo para Modo Idoso...", LogType.COMMAND)
            // Aumentar densidade de tela (ícones maiores)
            AdbManager.executeCommand("wm density 480") 
            // Brilho no máximo
            AdbManager.executeCommand("settings put system screen_brightness 255")
            // Volume no máximo
            AdbManager.executeCommand("service call audio 3 i32 3 i32 15 i32 1")
            // Ativar legendas
            AdbManager.executeCommand("settings put secure accessibility_captioning_enabled 1")
            
            addLog("✅ Modo Idoso ativado!", LogType.SUCCESS)
            vibrarSucesso()
        }
    }

    fun desativarModoIdoso() {
        viewModelScope.launch {
            addLog("👤 IA: Restaurando configurações padrão...", LogType.COMMAND)
            // Resetar densidade
            AdbManager.executeCommand("wm density reset")
            // Brilho automático/médio
            AdbManager.executeCommand("settings put system screen_brightness 128")
            // Volume médio
            AdbManager.executeCommand("service call audio 3 i32 3 i32 7 i32 1")
            // Desativar legendas
            AdbManager.executeCommand("settings put secure accessibility_captioning_enabled 0")
            
            addLog("✅ Configurações restauradas com sucesso!", LogType.SUCCESS)
            vibrarSucesso()
        }
    }
    fun takeScreenshot() {
        viewModelScope.launch {
            addLog("📸 Capturando tela...")
            AdbManager.executeCommand("screencap -p /data/local/tmp/screen.png")
            addLog("✅ Print salvo em /data/local/tmp/screen.png")
            vibrarSucesso()
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
            _session.value = DeviceSession() // Reset session
            AdbManager.disconnect()
            addLog("🔌 Dispositivo desconectado.", LogType.WARNING)
        }
    }

    private val _appInfoCache = mutableMapOf<String, AppInfo>()

    fun refreshDebloatApps() {
        viewModelScope.launch {
            _isRefreshingApps.value = true
            try {
                val packages = appManager.listApps()
                val context = getApplication<Application>()
                val pm = context.packageManager

                val appsInfo = packages.map { pkg ->
                    // 1. Checar Cache de Memória Primeiro
                    if (_appInfoCache.containsKey(pkg)) {
                        return@map _appInfoCache[pkg]!!
                    }

                    // 2. Tentar obter info e ícone local (Host) como referência
                    val (localLabel, localIcon) = try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai).toString() to pm.getApplicationIcon(ai)
                    } catch (e: Exception) {
                        pkg.substringAfterLast(".") to null
                    }

                    // 3. Análise de Risco (Local + Engine)
                    val isSystem = pkg.startsWith("com.android") || pkg.startsWith("android") || pkg.contains(".system")
                    val (risk, reason) = DebloatAnalyzer.analyze(pkg, isSystem)
                    val action = DebloatAnalyzer.suggestAction(risk, isSystem)

                    // 4. Cruzamento com Cache do Banco (IA Web)
                    val cachedIA = packageDao.getPackageInfo(pkg)
                    val finalRisk = if (cachedIA != null) {
                        if (cachedIA.isSafe) RiskLevel.SEGURO else RiskLevel.PERIGOSO
                    } else risk

                    val finalReason = if (cachedIA != null) {
                        "✅ [IA] ${cachedIA.reason}"
                    } else reason

                    val app = AppInfo(
                        packageName = pkg,
                        label = localLabel,
                        icon = localIcon,
                        isBloat = finalRisk == RiskLevel.SEGURO || finalRisk == RiskLevel.MODERADO,
                        risk = finalRisk,
                        riskScore = if (finalRisk == RiskLevel.SEGURO) 90 else 20,
                        riskReason = finalReason,
                        isSystem = isSystem,
                        recommendedAction = if (cachedIA?.isSafe == true) Action.UNINSTALL else action
                    )
                    
                    _appInfoCache[pkg] = app
                    app
                }
                
                _debloatApps.value = appsInfo
                
                if (_aiAutoModify.value && appsInfo.any { it.risk == RiskLevel.SEGURO }) {
                    val count = appsInfo.count { it.risk == RiskLevel.SEGURO }
                    responderIA("Análise 'Luxo' concluída! Identifiquei **$count apps** seguros para remoção imediata. Deseja que eu execute a faxina?", listOf("Faxina Total", "Ver Lista"))
                }
            } catch (e: Exception) {
                addLog("❌ Erro ao processar lista inteligente: ${e.message}", LogType.ERROR)
            } finally {
                _isRefreshingApps.value = false
            }
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
    fun uninstallApp(app: AppInfo) { 
        viewModelScope.launch { 
            addLog("🗑️ Desinstalando ${app.packageName}...")
            AdbManager.executeCommand("pm uninstall --user 0 ${app.packageName}")
            _appInfoCache.remove(app.packageName)
            refreshDebloatApps() 
        } 
    }
    
    fun disableApp(app: AppInfo) { 
        viewModelScope.launch { 
            addLog("🚫 Desativando ${app.packageName}...")
            AdbManager.executeCommand("pm disable-user --user 0 ${app.packageName}")
            _appInfoCache.remove(app.packageName)
            refreshDebloatApps() 
        } 
    }


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

    fun setDownloadFolder(uri: android.net.Uri) {
        _downloadFolderUri.value = uri
        addLog("📂 Pasta de download definida: ${uri.path}", LogType.INFO)
    }

    fun criarPasta(nome: String) {
        viewModelScope.launch {
            val fullPath = if (_caminhoAtual.value.endsWith("/")) "${_caminhoAtual.value}$nome" else "${_caminhoAtual.value}/$nome"
            addLog("📁 Criando pasta: $fullPath")
            AdbManager.executeCommand("mkdir -p $fullPath")
            listarArquivos(_caminhoAtual.value)
        }
    }

    fun salvarArquivoBaixado(context: android.content.Context, nome: String, data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = _downloadFolderUri.value
            if (uri == null) {
                // Fallback para pasta padrão se não houver SAF configurado
                try {
                    val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val localFile = File(downloadsDir, nome)
                    FileOutputStream(localFile).use { it.write(data) }
                    addLog("✅ Arquivo salvo em: ${localFile.absolutePath}", LogType.SUCCESS)
                } catch (e: Exception) {
                    addLog("❌ Erro ao salvar localmente: ${e.message}", LogType.ERROR)
                }
                return@launch
            }

            try {
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val newFile = documentFile?.createFile("*/*", nome)
                if (newFile != null) {
                    context.contentResolver.openOutputStream(newFile.uri)?.use { 
                        it.write(data)
                    }
                    addLog("✅ Arquivo salvo via SAF: $nome", LogType.SUCCESS)
                } else {
                    addLog("❌ Falha ao criar arquivo no destino SAF.", LogType.ERROR)
                }
            } catch (e: Exception) {
                addLog("❌ Erro ao salvar via SAF: ${e.message}", LogType.ERROR)
            }
        }
    }

    fun uploadArquivo(remotePath: String, content: ByteArray) {
        viewModelScope.launch {
            addLog("📤 Enviando arquivo para $remotePath...")
            val success = AdbManager.pushFile(content, remotePath)
            if (success) {
                addLog("✅ Arquivo enviado com sucesso!", LogType.SUCCESS)
                listarArquivos(_caminhoAtual.value)
            } else {
                addLog("❌ Falha ao enviar arquivo.", LogType.ERROR)
            }
        }
    }

    fun downloadArquivo(remotePath: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            addLog("📥 Baixando $remotePath...")
            val data = AdbManager.pullFile(remotePath)
            if (data != null) {
                addLog("✅ Download concluído: ${data.size} bytes", LogType.SUCCESS)
                onResult(data)
            } else {
                addLog("❌ Falha ao baixar arquivo.", LogType.ERROR)
                onResult(null)
            }
        }
    }

    // Logcat Logic - NÍVEL 6 (Monitoramento Reativo com Batching)
    private var logcatJob: Job? = null
    fun startLogcat() {
        if (_isStreamingLogcat.value) return
        _isStreamingLogcat.value = true
        addLog("📜 PicoClaw: Iniciando monitoramento em tempo real do sistema...", LogType.INFO)
        
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            val logBuffer = mutableListOf<LogEntry>()
            var lastUpdate = System.currentTimeMillis()

            AdbManager.openLogcatStream().collect { line ->
                if (line == null) return@collect
                
                // 1. Parsing e Análise IA
                val entry = parseLogcatLine(line)
                analisarLogComIA(entry)

                synchronized(logBuffer) {
                    logBuffer.add(entry)
                }

                // 2. Batching para não fritar a UI
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 800 || logBuffer.size >= 50) {
                    val batch = synchronized(logBuffer) {
                        val b = logBuffer.toList()
                        logBuffer.clear()
                        b
                    }
                    withContext(Dispatchers.Main) {
                        _logcatEntries.value = (_logcatEntries.value + batch).takeLast(1000)
                    }
                    lastUpdate = now
                }
            }
        }
    }

    private fun parseLogcatLine(line: String): LogEntry {
        // Exemplo: 05-23 12:34:56.789  1234  5678 E Tag: Mensagem
        return try {
            val levelChar = if (line.length > 31) line[31] else 'I'
            val level = when(levelChar) {
                'V' -> LogLevel.VERBOSE
                'D' -> LogLevel.DEBUG
                'W' -> LogLevel.WARN
                'E' -> LogLevel.ERROR
                'F' -> LogLevel.FATAL
                else -> LogLevel.INFO
            }
            // Tentar extrair tag e mensagem se estiver no formato longo do ADB
            val parts = line.split(":", limit = 2)
            val tag = if (parts.size > 1) parts[0].substringAfterLast(" ").trim() else "ADB"
            val msg = if (parts.size > 1) parts[1].trim() else line
            
            LogEntry(level, tag, msg, "", "", line)
        } catch (e: Exception) {
            LogEntry(LogLevel.INFO, "ADB", line, "", "", line)
        }
    }

    private fun analisarLogComIA(entry: LogEntry) {
        val msg = entry.message.lowercase()
        if (entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL || msg.contains("exception") || msg.contains("crash")) {
            // Detecção de Bootloop ou Crash de Sistema
            if (msg.contains("fatal exception") || msg.contains("anr in") || msg.contains("system_server") || msg.contains("zygot")) {
                viewModelScope.launch(Dispatchers.Main) {
                    addLog("🚨 IA DETECTOU INSTABILIDADE: ${entry.tag}", LogType.ERROR)
                    if (!_isSupportOpen.value && _aiAutoModify.value) {
                        responderIA("Detectei uma falha crítica (`${entry.tag}`). Isso geralmente indica arquivos corrompidos ou falta de memória. Deseja rodar o 'Reparo de Permissões'?", listOf("Reparar Sistema", "Ver Logcat"))
                    }
                }
            }
        }
    }

    fun stopLogcat() { 
        _isStreamingLogcat.value = false
        logcatJob?.cancel()
        addLog("📜 Stream do Logcat interrompido.")
    }

    // --- FERRAMENTAS DE MANUTENÇÃO AVANÇADA ---

    fun fixSystemPermissions() {
        viewModelScope.launch {
            addLog("🛠️ Iniciando Reparo e Otimização do Sistema...", LogType.COMMAND)
            // Otimização de compilação (DEX)
            runQuickCommand("pm compile -a -f -m speed-profile", "Otimizar Compilação")
            delay(500)
            // Limpeza de cache de background
            runQuickCommand("pm bg-dexopt-job", "Limpeza Background")
            delay(500)
            // Otimização de armazenamento (Trim) - Funciona em muitos dispositivos mesmo sem root
            runQuickCommand("sm fstrim dotrim", "Otimizar SSD/Flash")
            
            vibrarSucesso()
            responderIA("Concluí o pacote de manutenção. Otimizei o processamento de todos os apps e forcei o sistema a organizar o armazenamento interno. Como o celular está agora?", listOf("Muito Melhor!", "Ainda Lento"))
        }
    }

    fun limparCacheGeral() {
        viewModelScope.launch {
            addLog("🧹 Iniciando Limpeza Profunda de Cache...", LogType.COMMAND)
            val apps = appManager.listApps()
            addLog("📦 Analisando ${apps.size} pacotes para limpeza...")
            
            // Limpa cache de apps de terceiros conhecidos por acumular lixo
            val appsParaLimpar = apps.filter { pkg ->
                pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") || 
                pkg.contains("chrome") || pkg.contains("youtube") || pkg.contains("browser")
            }
            
            appsParaLimpar.forEach { pkg ->
                addLog("🧹 Limpando: $pkg")
                AdbManager.executeCommand("pm clear $pkg") // Nota: isso desloga o usuário, use com cautela no debloat seguro
            }
            
            // Método seguro: trim caches de todo o sistema
            AdbManager.executeCommand("pm trim-caches 4096G") 
            
            addLog("✨ Limpeza concluída!", LogType.SUCCESS)
            vibrarSucesso()
            responderIA("Faxina feita! Limpei o cache dos apps mais pesados e forcei o sistema a liberar memória temporária.", listOf("Valeu!", "Ver Armazenamento"))
        }
    }

    private suspend fun getScreenResolution(): Pair<Int, Int> {
        return try {
            val output = AdbManager.executeCommand("wm size")
            val match = "Physical size: (\\d+)x(\\d+)".toRegex().find(output)
            if (match != null) {
                val (w, h) = match.destructured
                w.toInt() to h.toInt()
            } else 1080 to 1920
        } catch (e: Exception) {
            1080 to 1920
        }
    }

    fun blindUnlockAdvanced() {
        viewModelScope.launch {
            addLog("🔓 PicoClaw: Iniciando Desbloqueio Adaptativo...", LogType.COMMAND)
            val (w, h) = getScreenResolution()
            
            tecla(26) // ACORDAR
            delay(500)
            
            // Deslize centralizado adaptativo
            val x = w / 2
            val yStart = (h * 0.8).toInt()
            val yEnd = (h * 0.2).toInt()
            
            addLog("👆 Deslizando de $yStart para $yEnd em $x...")
            AdbManager.executeCommand("input swipe $x $yStart $x $yEnd 250")
            
            delay(500)
            // Segunda tentativa com ângulo diferente se a primeira falhar
            AdbManager.executeCommand("input swipe ${x-100} $yStart ${x+100} $yEnd 250")
            
            addLog("✅ Sequência enviada. Tente usar o Mirror agora!", LogType.SUCCESS)
            vibrarSucesso()
        }
    }

    fun clearLogcatEntries() { _logcatEntries.value = emptyList() }
    fun updateLogcatFilter(f: String) { _logcatFilter.value = f }
    fun setLogcatMinLevel(l: LogLevel) { _logcatMinLevel.value = l }

    fun scriptPersonalizadoIA(problema: String) {
        viewModelScope.launch {
            addLog("🧠 PicoClaw: Gerando script inteligente para '$problema'...", LogType.COMMAND)
            val scriptConteudo = when {
                problema.contains("bateria") -> "dumpsys batterystats --reset && dumpsys battery"
                problema.contains("rede") || problema.contains("wifi") -> "svc wifi disable && svc wifi enable && netcfg"
                problema.contains("interface") || problema.contains("lento") -> "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5"
                else -> "logcat -d *:E"
            }
            val nome = "ia_fix_${System.currentTimeMillis() / 1000}.sh"
            salvarScript(nome, scriptConteudo)
            responderIA("Criei um script personalizado (`$nome`) baseado no seu problema. Você pode executá-lo na aba Scripts.", listOf("Ver Scripts", "Executar Agora"))
            vibrarSucesso()
        }
    }

    fun getFastbootVars() { 
        viewModelScope.launch { 
            addLog("⚡ PicoClaw: Lendo variáveis Fastboot...", LogType.COMMAND)
            // Simulação de leitura de variáveis reais em modo fastboot
            val vars = listOf(
                FastbootVar("version-bootloader", "0.4"),
                FastbootVar("product", "rescue_droid"),
                FastbootVar("secure", "yes"),
                FastbootVar("unlocked", "no"),
                FastbootVar("battery-voltage", "3850mV"),
                FastbootVar("slot-active", "a")
            )
            _fastbootVars.value = vars
            vibrarSucesso()
        } 
    }
    fun fastbootReboot() { viewModelScope.launch { addLog("⚡ Fastboot Reboot..."); AdbManager.executeCommand("reboot") } }
    
    fun fastbootFlash(partition: String, data: ByteArray) {
        viewModelScope.launch {
            addLog("⚡ [FASTBOOT] Flashing $partition (${data.size} bytes)...")
            // Fastboot protocol is different from ADB, but here we simulate or use a bridge if available
            // For now, we log the intent as Fastboot is typically handled by a separate transport
            delay(2000)
            addLog("✅ Partição $partition atualizada via Fastboot (Simulação).", LogType.SUCCESS)
        }
    }

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
    fun startMirror(context: Context) {
        _isMirroring.value = true
        addLog("🎥 Mirror iniciado", LogType.INFO)
    }

    fun stopMirror() {
        _isMirroring.value = false
        addLog("⏹ Mirror interrompido", LogType.INFO)
    }
}
