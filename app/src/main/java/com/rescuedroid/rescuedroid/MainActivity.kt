package com.rescuedroid.rescuedroid

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private data class ConfirmationDialogState(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val onConfirm: () -> Unit
    )

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PermissionRequester {
                    MainContainer()
                }
            }
        }
    }

    @Composable
    fun PermissionRequester(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val permissionsToRequest = remember {
            buildList {
                add(Manifest.permission.CAMERA)
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

        LaunchedEffect(Unit) {
            launcher.launch(permissionsToRequest.toTypedArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
        content()
    }

    @Composable
    fun MainContainer() {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF161B22)) {
                    NavigationBarItem(
                        selected = vm.currentScreen == AppScreen.ADB_RESCUE,
                        onClick = { vm.currentScreen = AppScreen.ADB_RESCUE },
                        icon = { Icon(Icons.Default.Build, null) },
                        label = { Text("Resgate") }
                    )
                    NavigationBarItem(
                        selected = vm.currentScreen == AppScreen.LOCAL_SHELL,
                        onClick = { vm.currentScreen = AppScreen.LOCAL_SHELL },
                        icon = { Icon(Icons.Default.Info, null) },
                        label = { Text("Shell") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (vm.currentScreen) {
                    AppScreen.ADB_RESCUE -> AdbRescueUI()
                    AppScreen.LOCAL_SHELL -> TermuxShellUI()
                }
            }
        }
    }

    @Composable
    fun AdbRescueUI() {
        val context = LocalContext.current
        var dialogState by remember { mutableStateOf<ConfirmationDialogState?>(null) }
        
        // Seletor de APK
        val apkLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { vm.installSelectedApk(context, it) }
        }

        dialogState?.let { state ->
            AlertDialog(
                onDismissRequest = { dialogState = null },
                title = { Text(state.title) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = {
                        dialogState = null
                        state.onConfirm()
                    }) {
                        Text(state.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RescueDroid 2.0", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            
            StatusCard()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = vm.adbIp, onValueChange = { vm.adbIp = it }, label = { Text("IP") }, modifier = Modifier.weight(2f), singleLine = true)
                OutlinedTextField(value = vm.adbPort, onValueChange = { vm.adbPort = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Conectar por Wi-Fi",
                            message = "Tentar conexao ADB com ${vm.adbIp}:${vm.adbPort}? O outro aparelho precisa estar com depuracao ADB ativa e autorizado.",
                            confirmLabel = "Conectar"
                        ) { vm.connectNetwork(context) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Conectar Wi-Fi") }
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Conectar por USB OTG",
                            message = "Permitir que o RescueDroid tente controlar o aparelho conectado por USB OTG? Se houver popup do Android, aceite a permissao USB e a chave ADB no outro aparelho.",
                            confirmLabel = "Permitir"
                        ) { vm.connectUsb(context) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("USB OTG") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.DarkGray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Enviar unlock",
                            message = "Enviar a sequencia de desbloqueio cego para o aparelho remoto?",
                            confirmLabel = "Enviar"
                        ) { vm.blindUnlock() }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) { Text("🔓 Unlock") }
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Capturar print remoto",
                            message = "Executar screencap no aparelho remoto e salvar em /sdcard/rescue.png?",
                            confirmLabel = "Capturar"
                        ) { vm.takeScreenshot() }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📸 Print") }
            }

            Text("Acessibilidade", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Ativar acessibilidade",
                            message = "Aplicar comandos ADB para alterar configuracoes de acessibilidade e timeout de tela no aparelho remoto?",
                            confirmLabel = "Ativar"
                        ) { vm.enableFullAccessibility() }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                ) { Text("Ativar Full", fontSize = 12.sp) }
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Desativar acessibilidade",
                            message = "Desativar a acessibilidade do aparelho remoto via ADB?",
                            confirmLabel = "Desativar"
                        ) { vm.disableTalkBack() }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Desativar", fontSize = 12.sp) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Diagnostico do aparelho",
                            message = "Consultar modelo e versao Android do aparelho remoto via ADB?",
                            confirmLabel = "Consultar"
                        ) { vm.deviceDiagnostics() }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Diagnóstico", fontSize = 10.sp) }
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Instalar APK remoto",
                            message = "Escolher um APK e enviar para instalacao no aparelho remoto conectado por ADB?",
                            confirmLabel = "Escolher APK"
                        ) { apkLauncher.launch("application/vnd.android.package-archive") }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                ) { 
                    Text("Instalar APK", fontSize = 10.sp) 
                }
                Button(
                    onClick = {
                        dialogState = ConfirmationDialogState(
                            title = "Iniciar mirror",
                            message = "Enviar o comando de inicializacao do scrcpy-server no aparelho remoto?",
                            confirmLabel = "Iniciar"
                        ) { vm.startMirror() }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📺 Mirror", fontSize = 10.sp) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Console ADB", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.clearLogs() }) { Icon(Icons.Default.Delete, "Limpar", tint = Color.Gray) }
            }
            
            TerminalConsole(vm.consoleLogs, modifier = Modifier.height(300.dp))

            OutlinedTextField(
                value = vm.shellCommand,
                onValueChange = { vm.shellCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("comando ADB remoto", color = Color.Gray) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.runAdbCommand() }),
                trailingIcon = {
                    IconButton(onClick = { vm.runAdbCommand() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF58A6FF))
                    }
                }
            )
            
            TermuxKeyRow(onKeyClick = { key -> vm.shellCommand += "$key" }, onRun = { vm.runAdbCommand() })
        }
    }

    @Composable
    fun TermuxShellUI() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Shell Local Android", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF4CAF50))
            
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1B5E20),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Shell local sem root (nao e Termux)",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Console local", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.clearLocalLogs() }) {
                    Icon(Icons.Default.Delete, "Limpar console local", tint = Color.Gray)
                }
            }

            TerminalConsole(vm.localConsoleLogs, modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = vm.localShellCommand,
                onValueChange = { vm.localShellCommand = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("$ command", color = Color.Gray) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.runLocalShell() }),
                trailingIcon = { IconButton(onClick = { vm.runLocalShell() }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF4CAF50)) } }
            )

            TermuxKeyRow(
                onKeyClick = { key -> vm.localShellCommand += key },
                onRun = { vm.runLocalShell() }
            )
        }
    }

    @Composable
    fun TermuxKeyRow(onKeyClick: (String) -> Unit, onRun: () -> Unit) {
        val keys = listOf("ESC", "CTRL", "ALT", "TAB", "-", "/", "_", "|")
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.forEach { key ->
                Surface(
                    modifier = Modifier.weight(1f).height(36.dp).clickable { onKeyClick(key) },
                    color = Color(0xFF21262D),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Surface(
                modifier = Modifier.width(48.dp).height(36.dp).clickable { onRun() },
                color = Color(0xFF238636),
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
                }
            }
        }
    }

    @Composable
    fun StatusCard() {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (vm.isConnected) Color(0xFF1B5E20) else Color(0xFF37474F),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(if(vm.isConnected) Color.Green else Color.Red, shape = RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (vm.isConnected) "DISPOSITIVO CONECTADO" else "DISPOSITIVO DESCONECTADO",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    @Composable
    fun TerminalConsole(logs: List<String>, modifier: Modifier = Modifier) {
        val listState = rememberLazyListState()
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.scrollToItem(logs.size - 1)
            }
        }

        Box(modifier = modifier.fillMaxWidth().background(Color(0xFF0D1117)).padding(8.dp)) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(items = logs) { line ->
                    Text(
                        text = line,
                        color = when {
                            line.startsWith(">") || line.startsWith("$") -> Color(0xFF58A6FF)
                            line.startsWith("❌") || line.contains("Erro") -> Color(0xFFF85149)
                            line.startsWith("✅") -> Color(0xFF3FB950)
                            line.startsWith("⚠") -> Color(0xFFD29922)
                            else -> Color(0xFFC9D1D9)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 6
                    )
                }
            }
        }
    }
}
