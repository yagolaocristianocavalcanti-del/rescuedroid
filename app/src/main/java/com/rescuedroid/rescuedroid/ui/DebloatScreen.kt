package com.rescuedroid.rescuedroid.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.RiskLevel
import com.rescuedroid.rescuedroid.model.AppInfo
import com.rescuedroid.rescuedroid.components.DangerZoneDialog

@Composable
fun DebloatScreen(vm: MainViewModel) {
    val isConnected by vm.isConnected.collectAsStateWithLifecycle()
    val apps by vm.debloatApps.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshingApps.collectAsStateWithLifecycle()
    val filter by vm.debloatFilter.collectAsStateWithLifecycle()
    
    var showDangerDialog by remember { mutableStateOf<AppInfo?>(null) }
    var actionType by remember { mutableStateOf("") }
    var selectedRiskFilter by remember { mutableStateOf<RiskLevel?>(null) }

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

        // BÔNUS: Botão IA Debloat Auto
        Button(
            onClick = { vm.debloatSeguroAutomatico() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003333)),
            border = BorderStroke(1.dp, Color.Cyan),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("🤖 DEBLOAT IA AUTO (SÓ SEGUROS)", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        OutlinedTextField(
            value = filter,
            onValueChange = { vm.updateDebloatFilter(it) },
            label = { Text("Filtrar por nome ou pacote...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Filtros de Risco
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RiskFilterChip("TODOS", null, Icons.Default.List, selectedRiskFilter == null) { selectedRiskFilter = null }
            RiskFilterChip("SEGURO", RiskLevel.SEGURO, Icons.Default.CheckCircle, selectedRiskFilter == RiskLevel.SEGURO) { selectedRiskFilter = RiskLevel.SEGURO }
            RiskFilterChip("PERIGOSO", RiskLevel.PERIGOSO, Icons.Default.Warning, selectedRiskFilter == RiskLevel.PERIGOSO) { selectedRiskFilter = RiskLevel.PERIGOSO }
            RiskFilterChip("CRÍTICO", RiskLevel.CRITICO, Icons.Default.Dangerous, selectedRiskFilter == RiskLevel.CRITICO) { selectedRiskFilter = RiskLevel.CRITICO }
        }

        LazyColumn {
            val filteredApps = apps.filter { 
                val matchesText = it.packageName.contains(filter, ignoreCase = true) || it.label.contains(filter, ignoreCase = true)
                val matchesRisk = selectedRiskFilter == null || it.risk == selectedRiskFilter
                matchesText && matchesRisk
            }
            items(filteredApps) { app ->
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
fun RiskFilterChip(label: String, risk: RiskLevel?, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val color = when(risk) {
        RiskLevel.SEGURO -> Color.Green
        RiskLevel.PERIGOSO -> Color(0xFFFF4500)
        RiskLevel.CRITICO -> Color.Red
        else -> Color.Cyan
    }
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(14.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.3f),
            selectedLabelColor = color,
            selectedLeadingIconColor = color,
            labelColor = Color.Gray,
            iconColor = Color.Gray
        )
    )
}

@Composable
fun AppItem(app: AppInfo, onUninstall: () -> Unit, onDisable: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(), // ✨ ANIMAÇÃO SUAVE
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, if (app.risk == RiskLevel.CRITICO) Color.Red else if (app.risk == RiskLevel.PERIGOSO) Color(0xFFFF4500) else Color(0xFF222222))
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        RiskBadge(app.risk)
                    }
                    Text(app.packageName, color = Color.Gray, fontSize = 10.sp)
                    if (app.riskReason.isNotEmpty() && !expanded) {
                        Text(app.riskReason, color = Color.Cyan.copy(alpha = 0.7f), fontSize = 9.sp, fontStyle = FontStyle.Italic, maxLines = 1)
                    }
                }
                IconButton(onClick = onDisable) { Icon(Icons.Default.Block, null, tint = Color.Yellow, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onUninstall) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
            }

            // DETALHES EXPANDIDOS
            AnimatedVisibility(visible = expanded) {
                Surface(
                    color = Color(0xFF111111),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (app.riskReason.isNotEmpty()) {
                            Text("Risco: ${app.riskReason}", color = Color.Cyan, fontSize = 11.sp)
                        }
                        if (app.acaoSugerida.isNotEmpty()) {
                            Text("Sugestão: ${app.acaoSugerida}", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Caminho: /system/app/${app.label}", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RiskBadge(risk: RiskLevel) {
    val cor = when(risk) {
        RiskLevel.SEGURO -> Color.Green
        RiskLevel.PERIGOSO -> Color(0xFFFF4500)
        RiskLevel.CRITICO -> Color.Red
    }
    
    Box(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(6.dp), ambientColor = cor, spotColor = cor)
            .background(cor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .border(1.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = when(risk) { RiskLevel.SEGURO -> "SEGURO"; RiskLevel.PERIGOSO -> "PERIGOSO"; else -> "CRÍTICO" },
            color = cor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}
