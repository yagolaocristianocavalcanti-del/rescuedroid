package com.rescuedroid.rescuedroid.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Dvr
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.tools.ScrcpyTool
import com.rescuedroid.rescuedroid.viewmodel.AdbConnectionState
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import android.view.SurfaceHolder
import android.view.SurfaceView

@Composable
fun ScrcpyScreen(vm: MainViewModel) {
    val isMirroring by vm.isMirroring.collectAsStateWithLifecycle()
    val mirrorQuality by vm.mirrorQuality.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // JANELA DE VISUALIZAÇÃO (O QUE SUMIU)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, if (isMirroring) Color.Green else Color.DarkGray),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isMirroring) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        vm.vincularSurfaceMirror(holder.surface)
                                    }
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        vm.desvincularSurfaceMirror()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Dvr, null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("AGUARDANDO INÍCIO", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        // HEADER STATUS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusLed(if (isMirroring) AdbConnectionState.CONECTADO else AdbConnectionState.DESCONECTADO)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (isMirroring) "🔴 MIRROR ATIVO" else "⚫ MIRROR PARADO",
                        fontWeight = FontWeight.Bold,
                        color = if (isMirroring) Color.Green else Color.Gray
                    )
                    Text("Qualidade: ${mirrorQuality.label}", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // BOTÕES PRINCIPAIS
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { vm.startMirror(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40)),
                enabled = !isMirroring
            ) {
                Text("▶️ INICIAR ESPELHAMENTO", fontWeight = FontWeight.Black)
            }
            
            Button(
                onClick = { vm.stopMirror() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                enabled = isMirroring
            ) {
                Text("⏹ PARAR ESPELHAMENTO", fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // SELETOR QUALIDADE
        Text("⚙️ CONFIGURAÇÃO DE IMAGEM", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ScrcpyTool.Quality.values().filter { it != ScrcpyTool.Quality.STANDARD }.forEach { quality ->
                QualityChip(quality.label, quality, mirrorQuality) { vm.setMirrorQuality(quality) }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // CONTROLES REMOTOS
        if (isMirroring) {
            Text("🎮 CONTROLE REMOTO (VIA SHELL)", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ControleRemotoGrid(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityChip(label: String, quality: ScrcpyTool.Quality, current: ScrcpyTool.Quality, onClick: () -> Unit) {
    val isSelected = quality == current
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.Cyan,
            labelColor = if (isSelected) Color.Black else Color.White
        )
    )
}

@Composable
fun ControleRemotoGrid(vm: MainViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(200.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ControleBtn("🏠", Icons.Default.Home) { vm.sendAdbKey(KeyEvent.KEYCODE_HOME) } }
        item { ControleBtn("🔙", Icons.AutoMirrored.Filled.ArrowBack) { vm.sendAdbKey(KeyEvent.KEYCODE_BACK) } }
        item { ControleBtn("❌", Icons.Default.Close) { vm.sendAdbKey(KeyEvent.KEYCODE_APP_SWITCH) } }
        item { ControleBtn("📝", Icons.Default.Edit) { vm.runQuickCommand("input text ' '", "Espaço") } }
        item { ControleBtn("🔍", Icons.Default.Search) { vm.sendAdbKey(KeyEvent.KEYCODE_SEARCH) } }
        item { ControleBtn("📱", Icons.Default.PhoneAndroid) { vm.sendAdbKey(KeyEvent.KEYCODE_MENU) } }
    }
}

@Composable
fun ControleBtn(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
