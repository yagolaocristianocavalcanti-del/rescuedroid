package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel

@Composable
fun FastbootScreen(vm: MainViewModel) {
    val vars by vm.fastbootVars.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    
    val isFastboot = session.transport == "FASTBOOT"
    val isConnected = session.isReady
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ FASTBOOT ENGINE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            if (isFastboot) {
                Surface(color = Color.Green.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("DEVICE DETECTED", color = Color.Green, fontSize = 10.sp, modifier = Modifier.padding(4.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF110000))) {
            Column(Modifier.padding(16.dp)) {
                Text("CUIDADO: Comandos Fastboot podem brickar o aparelho.", color = Color.Red, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.getFastbootVars() }, modifier = Modifier.weight(1f)) { 
                        Text("GETVAR", fontSize = 12.sp) 
                    }
                    Button(onClick = { vm.fastbootReboot() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { 
                        Text("REBOOT", fontSize = 12.sp) 
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text("FLASH PARTITIONS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlashButton("BOOT", Color.Cyan) { vm.fastbootFlash("boot", byteArrayOf()) }
                    FlashButton("RECOVERY", Color.Magenta) { vm.fastbootFlash("recovery", byteArrayOf()) }
                    FlashButton("SYSTEM", Color.Yellow) { vm.fastbootFlash("system", byteArrayOf()) }
                }
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
fun FlashButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
