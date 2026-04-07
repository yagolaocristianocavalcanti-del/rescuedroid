package com.rescuedroid.rescuedroid

import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.rescuedroid.rescuedroid.adb.AdbDevice
import com.rescuedroid.rescuedroid.ui.theme.RescuedroidTheme
import com.rescuedroid.rescuedroid.tools.ScrcpyTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private var instance: MainActivity? = null
        fun getAppContext() = instance?.applicationContext ?: throw IllegalStateException("Activity not initialized")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContent {
            RescuedroidTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PermissionGate {
                        val vm: MainViewModel = viewModel()
                        MainContent(vm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissionState = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.CAMERA
            )
        } else {
            listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            )
        }
    )

    LaunchedEffect(Unit) {
        permissionState.launchMultiplePermissionRequest()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
        }
    }

    when {
        permissionState.allPermissionsGranted -> {
            content()
        }
        permissionState.shouldShowRationale -> {
            PermissionRationaleDialog(
                onRetry = { permissionState.launchMultiplePermissionRequest() }
            )
        }
        else -> {
            PermissionDeniedScreen(
                onRetry = { permissionState.launchMultiplePermissionRequest() }
            )
        }
    }
}

@Composable
fun PermissionRationaleDialog(onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Permissões Necessárias", color = Color.Cyan) },
        text = { Text("O RescueDroid precisa de acesso aos arquivos e câmera para funcionar corretamente.", color = Color.White) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("REPOR", color = Color.Cyan)
            }
        },
        containerColor = Color(0xFF111111)
    )
}

@Composable
fun PermissionDeniedScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = Color.Red)
        Spacer(Modifier.height(16.dp))
        Text("Acesso Negado", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Sem as permissões o app não pode resgatar dispositivos.",
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("TENTAR NOVAMENTE")
        }
    }
}

@Composable
fun DangerZoneDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("EXECUTAR AÇÃO INSANA", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("abortar", color = Color.Gray)
            }
        },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text(title.uppercase(), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = { 
            Text(message, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace) 
        },
        containerColor = Color.Black,
        shape = RoundedCornerShape(0.dp)
    )
}

@Composable
fun MainContent(vm: MainViewModel) {
    val context = LocalContext.current
    val currentScreen by vm.currentScreen.collectAsStateWithLifecycle()
    val isConnected by vm.isConnected.collectAsStateWithLifecycle()
    val adbState by vm.adbConnectionState.collectAsStateWithLifecycle()
    val isHackerMode by vm.isHackerMode.collectAsStateWithLifecycle()
    val isConnecting = adbState == AdbConnectionState.CONECTANDO

    LaunchedEffect(Unit) {
        vm.startDeviceMonitoring(context)
    }

    Scaffold(
        bottomBar = {
            // ... (bottomBar code)
            NavigationBar(containerColor = Color.Black, contentColor = Color.Cyan) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.ADB_RESCUE,
                    onClick = { vm.setCurrentScreen(AppScreen.ADB_RESCUE) },
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text("Resgate", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SCRCPY,
                    onClick = { vm.setCurrentScreen(AppScreen.SCRCPY) },
                    icon = { Icon(Icons.Default.CastConnected, null) },
                    label = { Text("Mirror", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SCRIPTS,
                    onClick = { 
                        vm.setCurrentScreen(AppScreen.SCRIPTS)
                        vm.carregarScripts(context)
                    },
                    icon = { Icon(Icons.Default.Code, null) },
                    label = { Text("Scripts", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.FILE_MANAGER,
                    onClick = { 
                        vm.setCurrentScreen(AppScreen.FILE_MANAGER)
                        vm.listarArquivos("/sdcard/")
                    },
                    icon = { Icon(Icons.Default.Folder, null) },
                    label = { Text("Arquivos", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.LOGCAT,
                    onClick = { vm.setCurrentScreen(AppScreen.LOGCAT) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                    label = { Text("Logcat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DEBLOAT,
                    onClick = { 
                        vm.setCurrentScreen(AppScreen.DEBLOAT)
                        vm.refreshDebloatApps()
                    },
                    icon = { Icon(Icons.Default.CleaningServices, null) },
                    label = { Text("Debloat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Cyan, unselectedIconColor = Color.Gray, indicatorColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Column {
                StatusCard(vm, isConnected, isConnecting)
                
                Crossfade(targetState = currentScreen) { screen ->
                    when (screen) {
                        AppScreen.ADB_RESCUE -> AdbRescueScreen(vm, isConnected, isConnecting)
                        AppScreen.SCRCPY -> ScrcpyScreen(vm)
                        AppScreen.FILE_MANAGER -> FileManagerScreen(vm)
                        AppScreen.LOGCAT -> LogcatScreen(vm)
                        AppScreen.DEBLOAT -> DebloatScreen(vm, isConnected)
                        AppScreen.SCRIPTS -> AutomationScreen(vm)
                        AppScreen.FASTBOOT -> FastbootScreen(vm)
                    }
                }
            }

            if (isHackerMode) {
                HackerModeOverlay()
            }
        }
    }
}

@Composable
fun HackerModeOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanlineSpacing = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Green.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += scanlineSpacing
        }
    }
}

@Composable
fun StatusCard(vm: MainViewModel, isConnected: Boolean, isConnecting: Boolean) {
    val model by vm.deviceModel.collectAsStateWithLifecycle()
    val connState by vm.adbConnectionState.collectAsStateWithLifecycle()
    val isHackerMode by vm.isHackerMode.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
        border = BorderStroke(1.dp, if (isConnected) Color.Cyan else Color.DarkGray)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(if (isConnected) Color.Cyan else Color.Red, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isConnected) model else "DISPOSITIVO DESCONECTADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(estadoAdbParaTexto(connState), color = Color.Gray, fontSize = 10.sp)
            }
            IconButton(onClick = { vm.toggleHackerMode() }) {
                Icon(Icons.Default.Terminal, null, tint = if (isHackerMode) Color.Green else Color.Gray)
            }
            if (isConnected) {
                IconButton(onClick = { vm.disconnectAdb() }) {
                    Icon(Icons.Default.LinkOff, null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AdbRescueScreen(vm: MainViewModel, isConnected: Boolean, isConnecting: Boolean) {
    val scrollState = rememberScrollState()
    val manualIp by vm.manualIp.collectAsStateWithLifecycle()
    val manualPort by vm.manualPort.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("🛠️ FERRAMENTAS DE RESGATE", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(20.dp))

        if (!isConnected) {
            SectionHeader("CONEXÕES INTELIGENTES")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionButton("USB TURBO", Icons.Default.Usb, Color.Cyan, Modifier.weight(1f)) { vm.connectUsbAdvanced(context) }
                ConnectionButton("WIFI AUTO", Icons.Default.Wifi, Color.Green, Modifier.weight(1f)) { vm.connectNetworkSmart(context) }
            }
            Spacer(Modifier.height(8.dp))
            ConnectionButton("USB ➔ WIFI", Icons.Default.SwapHoriz, Color.Yellow, Modifier.fillMaxWidth()) { vm.usbToWifi() }
            
            Spacer(Modifier.height(16.dp))
            SectionHeader("CONFIGURAÇÃO MANUAL")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { vm.updateManualIp(it) },
                    label = { Text("IP", color = Color.Gray, fontSize = 10.sp) },
                    modifier = Modifier.weight(2f),
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { vm.updateManualPort(it) },
                    label = { Text("Porta", color = Color.Gray, fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    singleLine = true
                )
                Button(
                    onClick = { vm.connectManual(context) },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Default.Link, null, tint = Color.Cyan)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SectionHeader("AÇÕES RÁPIDAS (MODO PREGUIÇA)")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("🔓 DESBLOQUEIO CEGO") { vm.blindUnlockAdvanced() }
            ActionButton("📸 CAPTURAR TELA") { vm.takeScreenshot() }
            ActionButton("♿ ATIVAR TALKBACK") { vm.enableFullAccessibility() }
            ActionButton("⏰ TELA SEMPRE ON") { vm.increaseScreenTimeout() }
            ActionButton("🛠️ FIX SYSTEM UI") { vm.fixSystemUI() }
            ActionButton("🎙️ VOICE ACCESS") { vm.enableVoiceAccess() }
            ActionButton("🔑 ADB KEYS") { vm.sendAdbKey(66) }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("REINICIALIZAÇÃO")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("SYSTEM", { vm.rebootSystem() }, Color.Green)
            SmallButton("RECOVERY", { vm.rebootRecovery() }, Color.Yellow)
            SmallButton("BOOTLOADER", { vm.rebootBootloader() }, Color.Red)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("SHELL INTERATIVO")
        val logs by vm.consoleLogs.collectAsStateWithLifecycle()
        ConsoleWithInput(vm, logs, Modifier.height(250.dp))
    }
}

fun estadoAdbParaTexto(state: AdbConnectionState): String = when(state) {
    AdbConnectionState.DESCONECTADO -> "Aguardando conexão..."
    AdbConnectionState.CONECTANDO -> "Negociando handshake..."
    AdbConnectionState.CONECTADO -> "Conectado via USB (Turbo Mode)"
    AdbConnectionState.CONECTADO_WIFI -> "Conectado via Wi-Fi"
    AdbConnectionState.FALHOU -> "Erro na conexão. Tente outro cabo."
}

@Composable
fun ConnectionButton(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DebloatScreen(vm: MainViewModel, isConnected: Boolean) {
    val apps by vm.debloatApps.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshingApps.collectAsStateWithLifecycle()
    val filter by vm.debloatFilter.collectAsStateWithLifecycle()
    
    var showDangerDialog by remember { mutableStateOf<AppInfo?>(null) }
    var actionType by remember { mutableStateOf("") }

    if (showDangerDialog != null) {
        DangerZoneDialog(
            title = "Ação de Alto Risco",
            message = "Você está prestes a $actionType o app '${showDangerDialog?.label}'.\nEste é um componente ${showDangerDialog?.risk}. Tem certeza?",
            onConfirm = {
                val app = showDangerDialog!!
                if (actionType == "desinstalar") vm.uninstallApp(app) else vm.disableApp(app)
                showDangerDialog = null
            },
            onDismiss = { showDangerDialog = null }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧹 DEBLOAT INTELIGENTE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.Cyan)
            else IconButton(onClick = { vm.refreshDebloatApps() }) { Icon(Icons.Default.Refresh, null, tint = Color.Cyan) }
        }
        
        OutlinedTextField(
            value = filter,
            onValueChange = { vm.updateDebloatFilter(it) },
            label = { Text("Filtrar apps...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        LazyColumn {
            items(apps.filter { it.packageName.contains(filter, ignoreCase = true) || it.label.contains(filter, ignoreCase = true) }) { app ->
                AppItem(app, 
                    onUninstall = { 
                        if (app.risk == RiskLevel.CRITICO || app.risk == RiskLevel.PERIGOSO) {
                            actionType = "desinstalar"
                            showDangerDialog = app
                        } else vm.uninstallApp(app)
                    },
                    onDisable = { 
                         if (app.risk == RiskLevel.CRITICO || app.risk == RiskLevel.PERIGOSO) {
                            actionType = "desativar"
                            showDangerDialog = app
                        } else vm.disableApp(app)
                    }
                )
            }
        }
    }
}

@Composable
fun FastbootScreen(vm: MainViewModel) {
    val vars by vm.fastbootVars.collectAsStateWithLifecycle()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("⚡ FASTBOOT ENGINE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF110000))) {
            Column(Modifier.padding(16.dp)) {
                Text("CUIDADO: Comandos Fastboot podem brickar o aparelho.", color = Color.Red, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.getFastbootVars() }, modifier = Modifier.fillMaxWidth()) { Text("LER VARIÁVEIS (GETVAR)") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.fastbootReboot() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("REBOOT SYSTEM") }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        LazyColumn(Modifier.weight(1f).background(Color(0xFF050505)).border(1.dp, Color.DarkGray)) {
            items(vars) { v ->
                Row(Modifier.padding(8.dp).fillMaxWidth()) {
                    Text(v.name, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(v.value, color = Color.White, fontSize = 12.sp)
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
fun LocalShellScreen(vm: MainViewModel) {
    val logs by vm.localConsoleLogs.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🐚 SHELL LOCAL (APP)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        ConsoleView(logs, Modifier.weight(1f))
        
        var cmd by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextField(
                value = cmd,
                onValueChange = { cmd = it },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            IconButton(onClick = { 
                vm.updateLocalShellCommand(cmd)
                vm.runLocalShell()
                cmd = ""
            }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Cyan) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun SectionHeader(text: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(text, color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
        HorizontalDivider(color = Color.Cyan.copy(alpha = 0.3f), thickness = 1.dp)
    }
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(bottom = 4.dp, end = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.dp, Color.DarkGray),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallButton(label: String, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, color),
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConsoleWithInput(vm: MainViewModel, logs: List<String>, modifier: Modifier = Modifier) {
    val cmd by vm.shellCommand.collectAsStateWithLifecycle()
    
    Column(modifier.background(Color(0xFF080808), RoundedCornerShape(8.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))) {
        ConsoleView(logs, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", color = Color.Cyan, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = cmd,
                onValueChange = { vm.updateShellCommand(it) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.Cyan)
            )
            IconButton(onClick = { vm.runAdbCommand() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ConsoleView(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    
    LazyColumn(state = listState, modifier = modifier.padding(8.dp)) {
        items(logs) { log ->
            Text(log, color = if (log.startsWith(">")) Color.Cyan else Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
fun AppItem(app: AppInfo, onUninstall: () -> Unit, onDisable: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, if (app.risk == RiskLevel.CRITICO) Color.Red else if (app.risk == RiskLevel.PERIGOSO) Color(0xFFFF4500) else Color(0xFF222222))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    RiskBadge(app.risk)
                }
                Text(app.packageName, color = Color.Gray, fontSize = 10.sp)
                if (app.riskReason.isNotEmpty()) {
                    Text(app.riskReason, color = Color.Cyan.copy(alpha = 0.7f), fontSize = 9.sp, fontStyle = FontStyle.Italic)
                }
                if (app.acaoSugerida.isNotEmpty()) {
                    Text("💡 ${app.acaoSugerida}", color = Color.Green.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onDisable) { Icon(Icons.Default.Block, null, tint = Color.Yellow, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onUninstall) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun RiskBadge(risk: RiskLevel) {
    val (text, color) = when (risk) {
        RiskLevel.SEGURO -> "SEGURO" to Color.Green
        RiskLevel.PERIGOSO -> "PERIGOSO" to Color(0xFFFF4500)
        RiskLevel.CRITICO -> "CRÍTICO" to Color.Red
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun FileManagerScreen(vm: MainViewModel) {
    val arquivos by vm.arquivosAtuais.collectAsStateWithLifecycle()
    val path by vm.caminhoAtual.collectAsStateWithLifecycle()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF0A0A0A)).padding(8.dp).fillMaxWidth()) {
            IconButton(onClick = { vm.voltarDiretorio() }) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Cyan) 
            }
            Text(path, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { vm.listarArquivos(path) }) { 
                Icon(Icons.Default.Refresh, null, tint = Color.Green) 
            }
        }
        
        HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = Color(0xFF222222))
        
        LazyColumn(Modifier.weight(1f)) {
            items(arquivos.sortedWith(compareByDescending<MainViewModel.ArquivoAdb> { it.eDiretorio }.thenBy { it.nome })) { arquivo ->
                ArquivoItem(arquivo, 
                    onClick = { 
                        val newPath = if (path.endsWith("/")) "$path${arquivo.nome}" else "$path/${arquivo.nome}"
                        if (arquivo.eDiretorio) vm.listarArquivos("$newPath/") 
                    },
                    onDelete = { vm.deleteFile(if (path.endsWith("/")) "$path${arquivo.nome}" else "$path/${arquivo.nome}") }
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ActionButton("ROOT EXPLORER", { vm.listarArquivos("/") })
            Spacer(Modifier.width(8.dp))
            ActionButton("SDCARD", { vm.listarArquivos("/sdcard/") })
        }
    }
}

@Composable
fun ArquivoItem(arquivo: MainViewModel.ArquivoAdb, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (arquivo.eDiretorio) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            null,
            tint = if (arquivo.eDiretorio) Color.Cyan else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(arquivo.nome, color = Color.White, fontSize = 13.sp)
            Text("${arquivo.tamanho} | ${arquivo.permissao}", color = Color.Gray, fontSize = 10.sp)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.DarkGray, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun LogcatScreen(vm: MainViewModel) {
    val entries by vm.logcatEntries.collectAsStateWithLifecycle()
    val isStreaming by vm.isStreamingLogcat.collectAsStateWithLifecycle()
    val filter by vm.logcatFilter.collectAsStateWithLifecycle()
    val minLevel by vm.logcatMinLevel.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📋 LOGCAT REAL-TIME", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clearLogcatEntries() }) { Icon(Icons.Default.ClearAll, null, tint = Color.Gray) }
            Button(
                onClick = { if (isStreaming) vm.stopLogcat() else vm.startLogcat() },
                colors = ButtonDefaults.buttonColors(containerColor = if (isStreaming) Color.Red else Color.DarkGray)
            ) {
                Text(if (isStreaming) "PARAR" else "INICIAR")
            }
        }

        Spacer(Modifier.height(8.dp))
        LogLevelSelector(minLevel) { vm.setLogcatMinLevel(it) }

        OutlinedTextField(
            value = filter,
            onValueChange = { vm.updateLogcatFilter(it) },
            label = { Text("Filtrar Tag ou Mensagem", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
        )

        val filteredEntries = entries.filter { 
            it.level.ordinal >= minLevel.ordinal && 
            (it.tag.contains(filter, true) || it.message.contains(filter, true))
        }

        LazyColumn(Modifier.weight(1f).background(Color(0xFF050505)).border(1.dp, Color.DarkGray)) {
            items(filteredEntries) { entry ->
                LogcatEntryItem(entry)
            }
        }
    }
}

@Composable
fun LogcatEntryItem(entry: LogEntry) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row {
            Text(entry.level.code, color = Color(entry.level.color), fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(4.dp))
            Text(entry.tag, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(80.dp), maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(entry.message, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.1f))
    }
}

@Composable
fun LogLevelSelector(selected: LogLevel, onSelect: (LogLevel) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LogLevel.values().forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = { Text(level.code, fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(level.color), selectedLabelColor = Color.Black)
            )
        }
    }
}

@Composable
fun AutomationScreen(vm: MainViewModel) {
    val scripts by vm.scriptsLocais.collectAsStateWithLifecycle()
    val isExecutando by vm.isExecutandoScript.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEditor by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<MainViewModel.ScriptLocal?>(null) }

    if (showEditor) {
        ScriptEditorDialog(
            script = editingScript,
            onSave = { nome, conteudo ->
                vm.salvarScript(context, nome, conteudo)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    LaunchedEffect(Unit) { vm.carregarScripts(context) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🚀 AUTOMAÇÃO & SCRIPTS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        if (isExecutando) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Cyan)
            Text("Executando script no dispositivo...", color = Color.Cyan, fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(scripts) { script ->
                ScriptItem(script, isExecutando, 
                    onRun = { vm.executarScript(context, script) },
                    onDelete = { vm.deletarScript(context, script) }
                )
            }
        }
        
        Button(
            onClick = { 
                editingScript = null
                showEditor = true 
            }, 
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("NOVO SCRIPT (.SH)")
        }
    }
}

@Composable
fun ScriptEditorDialog(
    script: MainViewModel.ScriptLocal?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var nome by remember { mutableStateOf(script?.nome ?: "meu_script.sh") }
    var conteudo by remember { mutableStateOf(script?.conteudo ?: "#!/system/bin/sh\n\n") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        title = { Text("Editor de Script ADB", color = Color.Cyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do Arquivo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = conteudo,
                    onValueChange = { conteudo = it },
                    label = { Text("Conteúdo Shell") },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nome, conteudo) }) {
                Text("SALVAR", color = Color.Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ScriptItem(script: MainViewModel.ScriptLocal, isRunning: Boolean, onRun: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Text(script.nome, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onRun, enabled = !isRunning) { Icon(Icons.Default.PlayArrow, null, tint = Color.Green) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.DarkGray) }
        }
    }
}

@Composable
fun ScrcpyScreen(vm: MainViewModel) {
    val quality by vm.mirrorQuality.collectAsStateWithLifecycle()
    val isMirroring by vm.isMirroring.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🎥 ESPELHAMENTO PROFISSIONAL", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MirrorControlButton("▶️ INICIAR", Icons.Default.PlayArrow, Color(0xFF1B5E20), !isMirroring) { vm.startMirror(context) }
                    MirrorControlButton("⏹ PARAR", Icons.Default.Stop, Color(0xFFB71C1C), isMirroring) { vm.stopMirror() }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text("QUALIDADE DA TRANSMISSÃO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualitySelector("💎 ULTRA", ScrcpyTool.Quality.ULTRA, quality == ScrcpyTool.Quality.ULTRA) { vm.setMirrorQuality(ScrcpyTool.Quality.ULTRA) }
                    QualitySelector("🟢 HIGH", ScrcpyTool.Quality.HIGH, quality == ScrcpyTool.Quality.HIGH) { vm.setMirrorQuality(ScrcpyTool.Quality.HIGH) }
                    QualitySelector("🔵 SD", ScrcpyTool.Quality.STANDARD, quality == ScrcpyTool.Quality.STANDARD) { vm.setMirrorQuality(ScrcpyTool.Quality.STANDARD) }
                    QualitySelector("⚡ LIGHT", ScrcpyTool.Quality.LIGHT, quality == ScrcpyTool.Quality.LIGHT) { vm.setMirrorQuality(ScrcpyTool.Quality.LIGHT) }
                }

                if (isMirroring) {
                    Spacer(Modifier.height(16.dp))
                    Text("🟢 Transmissão ativa em segundo plano", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MirrorControlButton(text: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(label: String, quality: ScrcpyTool.Quality, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Cyan, selectedLabelColor = Color.Black, containerColor = Color(0xFF111111), labelColor = Color.Gray),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = if (isSelected) Color.Cyan else Color(0xFF222222), borderWidth = 1.dp, selectedBorderColor = Color.Cyan)
    )
}
