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
import androidx.compose.foundation.lazy.itemsIndexed
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
                        label = { Text("Termux") }
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
        
        // Seletor de APK
        val apkLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { vm.installSelectedApk(context, it) }
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
                Button(onClick = { vm.connectNetwork(context) }, modifier = Modifier.weight(1f)) { Text("Conectar Wi-Fi") }
                Button(onClick = { vm.connectUsb(context) }, modifier = Modifier.weight(1f)) { Text("USB OTG") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.DarkGray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.blindUnlock() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) { Text("🔓 Unlock") }
                Button(onClick = { vm.takeScreenshot() }, modifier = Modifier.weight(1f)) { Text("📸 Print") }
            }

            Text("Acessibilidade", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.enableFullAccessibility() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))) { Text("Ativar Full", fontSize = 12.sp) }
                Button(onClick = { vm.disableTalkBack() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Desativar", fontSize = 12.sp) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.deviceDiagnostics() }, modifier = Modifier.weight(1f)) { Text("Diagnóstico", fontSize = 10.sp) }
                Button(onClick = { apkLauncher.launch("application/vnd.android.package-archive") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))) { 
                    Text("Instalar APK", fontSize = 10.sp) 
                }
                Button(onClick = { vm.startMirror() }, modifier = Modifier.weight(1f)) { Text("📺 Mirror", fontSize = 10.sp) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Console ADB", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.clearLogs() }) { Icon(Icons.Default.Delete, "Limpar", tint = Color.Gray) }
            }
            
            TerminalConsole(vm.consoleLogs, modifier = Modifier.height(300.dp))
            
            TermuxKeyRow(onKeyClick = { key -> vm.shellCommand += "$key" }, onRun = { vm.runAdbCommand() })
        }
    }

    @Composable
    fun TermuxShellUI() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Shell Termux Local", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF4CAF50))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { vm.requestRoot() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if(vm.hasRoot) Color(0xFF2E7D32) else Color(0xFFC62828))
            ) {
                Text(if(vm.hasRoot) "ROOT CONCEDIDO" else "SOLICITAR ROOT (su)")
            }

            Spacer(modifier = Modifier.height(8.dp))

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
        LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

        Box(modifier = modifier.fillMaxWidth().background(Color(0xFF0D1117)).padding(8.dp)) {
            LazyColumn(state = listState) {
                itemsIndexed(items = logs, key = { index, item -> "${index}_${item.hashCode()}" }) { _, line ->
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
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
