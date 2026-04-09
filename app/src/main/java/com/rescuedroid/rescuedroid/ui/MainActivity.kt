package com.rescuedroid.rescuedroid.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.viewmodel.AppScreen
import com.rescuedroid.rescuedroid.viewmodel.AdbConnectionState
import com.rescuedroid.rescuedroid.ui.theme.RescuedroidTheme
import com.rescuedroid.rescuedroid.components.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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

data class NavScreen(
    val screen: AppScreen,
    val icon: ImageVector,
    val label: String
)

@Composable
fun MainContent(vm: MainViewModel) {
    val context = LocalContext.current
    val currentScreen by vm.currentScreen.collectAsStateWithLifecycle()
    val isHackerMode by vm.isHackerMode.collectAsStateWithLifecycle()
    val isSupportOpen by vm.isSupportOpen.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.startDeviceMonitoring(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.Black, contentColor = Color.Cyan) {
                val screens = listOf(
                    NavScreen(AppScreen.ADB_RESCUE, Icons.Default.Build, "Resgate"),
                    NavScreen(AppScreen.SCRCPY, Icons.Default.CastConnected, "Mirror"),
                    NavScreen(AppScreen.SCRIPTS, Icons.Default.Code, "Scripts"),
                    NavScreen(AppScreen.FILE_MANAGER, Icons.Default.Folder, "Arquivos"),
                    NavScreen(AppScreen.DEBLOAT, Icons.Default.CleaningServices, "Debloat"),
                    NavScreen(AppScreen.CONFIGURACOES, Icons.Default.Settings, "Config")
                )

                screens.forEach { navItem ->
                    NavigationBarItem(
                        selected = currentScreen == navItem.screen,
                        onClick = { 
                            vm.setCurrentScreen(navItem.screen)
                            when(navItem.screen) {
                                AppScreen.SCRIPTS -> vm.carregarScripts()
                                AppScreen.FILE_MANAGER -> vm.listarArquivos("/sdcard/")
                                AppScreen.DEBLOAT -> vm.refreshDebloatApps()
                                else -> {}
                            }
                        },
                        icon = { Icon(navItem.icon, null) },
                        label = { Text(navItem.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Cyan,
                            unselectedIconColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            Column {
                StatusCard(vm)
                
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        AppScreen.ADB_RESCUE -> AdbRescueScreen(vm)
                        AppScreen.SCRCPY -> ScrcpyScreen(vm)
                        AppScreen.FILE_MANAGER -> FileManagerScreen(vm)
                        AppScreen.LOGCAT -> LogcatScreen(vm)
                        AppScreen.DEBLOAT -> DebloatScreen(vm)
                        AppScreen.SUPORTE_IA -> SupportScreen(vm)
                        AppScreen.SCRIPTS -> AutomationScreen(vm)
                        AppScreen.FASTBOOT -> FastbootScreen(vm)
                        AppScreen.CONFIGURACOES -> SettingsScreen(vm)
                        else -> {}
                    }
                }
            }

            // Overlay Flutuante do Suporte IA
            AnimatedVisibility(
                visible = isSupportOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .background(Color(0xFF050505), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(top = 8.dp)
                ) {
                    SupportScreen(vm)
                }
            }

            // IA PicoClaw FAB Global
            FloatingActionButton(
                onClick = { vm.toggleSupport() },
                containerColor = Color.Cyan,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 16.dp)
                    .size(56.dp)
            ) {
                AnimatedContent(targetState = isSupportOpen, label = "fabIcon") { open ->
                    Icon(
                        if (open) Icons.Default.Close else Icons.Default.SmartToy,
                        contentDescription = "Suporte IA",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (isHackerMode) {
                HackerModeOverlay()
            }
        }
    }
}
}
