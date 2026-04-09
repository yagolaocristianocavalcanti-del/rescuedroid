package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.viewmodel.AdbConnectionState
import com.rescuedroid.rescuedroid.viewmodel.AppScreen

@Composable
fun AdbRescueScreen(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val manualIp by vm.manualIp.collectAsStateWithLifecycle()
    val manualPort by vm.manualPort.collectAsStateWithLifecycle()
    val pairingCode by vm.pairingCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val usbMode by vm.usbMode.collectAsStateWithLifecycle()
    
    val isConnected = session.isReady
    val isConnecting = session.status == AdbConnectionState.CONECTANDO
    
    val dispositivos by vm.dispositivos.collectAsStateWithLifecycle()
    val selecionado by vm.dispositivoSelecionado.collectAsStateWithLifecycle()
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        SectionHeader("📊 DIAGNÓSTICO E DISPOSITIVOS")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DISPOSITIVOS ADB", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.atualizarListaDispositivos() }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = Color.Cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ATUALIZAR", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (dispositivos.isEmpty()) {
                    Text("Nenhum dispositivo detectado", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    dispositivos.forEach { device ->
                        val isSel = selecionado?.serial == device.serial || (selecionado?.usbDevice != null && selecionado?.usbDevice?.deviceName == device.usbDevice?.deviceName)
                        
                        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { vm.selecionarDispositivo(device) },
                                    onDoubleClick = {
                                        vm.selecionarDispositivo(device)
                                        if (device.type == "USB") {
                                            vm.connectUsbAdvanced(context, usbMode)
                                        } else {
                                            vm.connectManual(context)
                                        }
                                    }
                                ),
                            color = if (isSel) Color(0xFF1A3333) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                            border = if (isSel) BorderStroke(1.dp, Color.Cyan) else null
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (device.status == com.rescuedroid.rescuedroid.adb.DeviceStatus.ONLINE) Color.Green else Color.Red, CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.model ?: "Modelo Desconhecido", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${device.type} - ${device.serial}", color = Color.Gray, fontSize = 10.sp)
                                }
                                if (device.status != com.rescuedroid.rescuedroid.adb.DeviceStatus.ONLINE) {
                                    Text("OFFLINE", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("📡 CONEXÃO ADB")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { vm.updateManualIp(it) },
                label = { Text("IP", color = Color.Gray, fontSize = 10.sp) },
                modifier = Modifier.weight(2f),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color.Cyan)
            )
            OutlinedTextField(
                value = manualPort,
                onValueChange = { vm.updateManualPort(it) },
                label = { Text("Porta", color = Color.Gray, fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color.Cyan)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { vm.updatePairingCode(it) },
                label = { Text("Código Pareamento (6 dígitos)", color = Color.Gray, fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color.Yellow)
            )
            Button(
                onClick = { vm.pairAndConnect(context, pairingCode) },
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332200)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.Yellow)
            ) {
                Text("PAREAR", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GoldButton("Wi-Fi Direto", Modifier.weight(1f)) { vm.connectManual(context) }
            GoldButton("USB Auto", Modifier.weight(1f)) { vm.connectUsbAdvanced(context, usbMode) }
            Button(
                onClick = { vm.usbToWifi() },
                modifier = Modifier.weight(1f).height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("USB ➔ Wi-Fi", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("🔧 CONEXÃO TÉCNICA USB")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
            Column(Modifier.padding(12.dp)) {
                Text("Modo Atual: ${usbMode.uppercase()}", color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeButton("Forte", "💪", usbMode == "forte") { vm.setUsbMode("forte") }
                    ModeButton("Martelo", "🔨", usbMode == "martelo") { vm.setUsbMode("martelo") }
                    ModeButton("Legado", "👴", usbMode == "legado") { vm.setUsbMode("legado") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("🖥️ CONSOLE ADB")
        val logs by vm.consoleLogs.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxWidth().height(180.dp).background(Color.Black).border(1.dp, Color.DarkGray).padding(8.dp)) {
            LazyColumn {
                item { Text("🚀 RescueDroid 2.0 Iniciado", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                items(logs) { log ->
                    Text(log, color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ESC" to 111, "HOME" to 3, "BACK" to 4, "UP" to 19).forEach { (label, code) ->
                KeyButton(label, Modifier.weight(1f)) { vm.sendAdbKey(code) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("DOWN" to 20, "LEFT" to 21, "RIGHT" to 22, "ENTER" to 66).forEach { (label, code) ->
                KeyButton(label, Modifier.weight(1f)) { vm.sendAdbKey(code) }
            }
        }

        Spacer(Modifier.height(12.dp))
        ContextualQuickActions(vm)

        Spacer(Modifier.height(16.dp))
        ShellInput(vm)
        
        Spacer(Modifier.height(24.dp))
        SectionHeader("FERRAMENTAS DE RESGATE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("🔓 DESBLOQUEIO CEGO", Modifier.weight(1f)) { vm.blindUnlockAdvanced() }
            ActionButton("📸 PRINT", Modifier.weight(1f)) { vm.takeScreenshot() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextualQuickActions(vm: MainViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BlueActionButton("🎁 APPS") { vm.runQuickCommand("pm list packages", "Listar Apps") }
        BlueActionButton("🔋 BATERIA") { vm.runQuickCommand("dumpsys battery", "Bateria") }
        BlueActionButton("🛠️ REPARO") { vm.fixSystemPermissions() }
        BlueActionButton("🧹 CACHE") { vm.limparCacheGeral() }
        BlueActionButton("🔓 UNLOCK") { vm.blindUnlockAdvanced() }
        BlueActionButton("📱 TELA") { vm.runQuickCommand("wm size", "Tela") }
        BlueActionButton("🔍 PROCESSOS") { vm.runQuickCommand("ps", "Processos") }
        BlueActionButton("☁️ IP") { vm.runQuickCommand("ip addr show wlan0", "IP") }
        BlueActionButton("🔄 REBOOT") { vm.rebootSystem() }
        BlueActionButton("⚙️ AJUSTES") { vm.runQuickCommand("am start -a android.settings.SETTINGS", "Configs") }
        BlueActionButton("📋 LOGS") { vm.setCurrentScreen(AppScreen.LOGCAT) }
        BlueActionButton("🎯 FOCO") { vm.runQuickCommand("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'", "Foco") }
        BlueActionButton("📂 SD CARD") { vm.setCurrentScreen(AppScreen.FILE_MANAGER) }
        BlueActionButton("🔴 KILL APPS") { vm.runQuickCommand("am kill-all", "Kill All") }
    }
}

@Composable
fun ShellInput(vm: MainViewModel) {
    var shellCmd by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).border(1.dp, Color.DarkGray).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$ ", color = Color.Cyan, modifier = Modifier.padding(start = 8.dp), fontFamily = FontFamily.Monospace)
        BasicTextField(
            value = shellCmd,
            onValueChange = { shellCmd = it },
            modifier = Modifier.weight(1f).padding(8.dp),
            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.Cyan)
        )
        IconButton(onClick = { 
            vm.updateShellCommand(shellCmd)
            vm.runAdbCommand()
            shellCmd = ""
        }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Cyan) }
    }
}

fun estadoAdbParaTexto(state: AdbConnectionState): String = when(state) {
    AdbConnectionState.DESCONECTADO -> "Aguardando conexão..."
    AdbConnectionState.CONECTANDO -> "Negociando handshake..."
    AdbConnectionState.CONECTADO -> "Conectado via USB (Turbo Mode)"
    AdbConnectionState.CONECTADO_WIFI -> "Conectado via Wi-Fi"
    AdbConnectionState.FALHOU -> "Erro na conexão. Tente outro cabo."
}
