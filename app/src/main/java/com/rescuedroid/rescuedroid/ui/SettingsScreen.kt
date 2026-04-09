package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val isHackerMode by vm.isHackerMode.collectAsStateWithLifecycle()
    val aiAutoConnect by vm.aiAutoConnect.collectAsStateWithLifecycle()
    val aiAutoModify by vm.aiAutoModify.collectAsStateWithLifecycle()
    val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Configurações",
            color = Color.Cyan,
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection("Geral") {
            SettingsToggleItem(
                title = "Modo Hacker (Visual)",
                description = "Ativa overlays de dados estilo terminal",
                checked = isHackerMode,
                icon = Icons.Default.BugReport,
                onCheckedChange = { vm.toggleHackerMode() }
            )
            
            SettingsItem(
                title = "Idioma",
                description = "Atual: $appLanguage (Toque para mudar)",
                icon = Icons.Default.Translate
            )
        }

        SettingsSection("Inteligência Artificial (PicoClaw)") {
            SettingsToggleItem(
                title = "Auto-Conexão Inteligente",
                description = "Permitir que a IA tente conexões sozinha",
                checked = aiAutoConnect,
                icon = Icons.Default.SmartToy,
                onCheckedChange = { vm.toggleAiAutoConnect() }
            )
            SettingsToggleItem(
                title = "Poderes de Modificação",
                description = "Permitir que a IA execute scripts e debloat",
                checked = aiAutoModify,
                icon = Icons.Default.SmartToy,
                onCheckedChange = { vm.toggleAiAutoModify() }
            )
        }
        
        SettingsSection("Conexão") {
            SettingsItem("Limpar Histórico", "Remove todos os dispositivos recentes", Icons.Default.Security)
            SettingsItem(
                title = "Resetar Chaves ADB", 
                description = "Gera um novo par de chaves RSA", 
                icon = Icons.Default.Security,
                onClick = { vm.processarComandoIA("resetar chaves") }
            )
        }
        
        SettingsSection("Sobre") {
            SettingsItem("Versão", "2.0.0-BETA", Icons.Default.Info)
            SettingsItem("Desenvolvedor", "RescueDroid Team", Icons.Default.ColorLens)
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        content()
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
    }
}

@Composable
fun SettingsItem(title: String, description: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = { onClick?.invoke() },
        color = Color.Transparent,
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, description: String, checked: Boolean, icon: ImageVector, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
        )
    }
}
