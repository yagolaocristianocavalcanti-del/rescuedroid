package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.tools.ScrcpyTool

import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import com.rescuedroid.rescuedroid.adb.AdbManager
import android.media.MediaCodec
import android.media.MediaFormat
import kotlinx.coroutines.*

@Composable
fun ScrcpyScreen(vm: MainViewModel) {
    val quality by vm.mirrorQuality.collectAsStateWithLifecycle()
    val isMirroring by vm.isMirroring.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🎥 ESPELHAMENTO EM TEMPO REAL", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Cyan)
        
        Spacer(Modifier.height(16.dp))
        
        // Janela de Visualização
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(2.dp, if (isMirroring) Color.Cyan else Color.DarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isMirroring) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                        vm.vincularSurfaceMirror(holder.surface)
                                    }
                                    override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, hi: Int) {}
                                    override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                                        vm.desvincularSurfaceMirror()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CastConnected, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ESPELHAMENTO DESATIVADO", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Controles
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isMirroring) {
                        MirrorControlButton("▶️ INICIAR ESPELHAMENTO", Icons.Default.PlayArrow, Color(0xFF004D40), true) { vm.startMirror(context) }
                    } else {
                        MirrorControlButton("⏹ FINALIZAR ESPELHAMENTO", Icons.Default.Stop, Color(0xFFB71C1C), true) { vm.stopMirror() }
                    }
                }

                if (isMirroring) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.enviarMensagemSuporte("O espelhamento está funcionando corretamente?") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan),
                        border = BorderStroke(1.dp, Color.Cyan)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PEDIR AJUDA AO PICOCLAW")
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text("QUALIDADE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualitySelector("💎 ULTRA", ScrcpyTool.Quality.ULTRA, quality == ScrcpyTool.Quality.ULTRA) { vm.setMirrorQuality(ScrcpyTool.Quality.ULTRA) }
                    QualitySelector("🟢 HIGH", ScrcpyTool.Quality.HIGH, quality == ScrcpyTool.Quality.HIGH) { vm.setMirrorQuality(ScrcpyTool.Quality.HIGH) }
                    QualitySelector("⚡ LIGHT", ScrcpyTool.Quality.LIGHT, quality == ScrcpyTool.Quality.LIGHT) { vm.setMirrorQuality(ScrcpyTool.Quality.LIGHT) }
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
