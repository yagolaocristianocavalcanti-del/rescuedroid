package com.rescuedroid.rescuedroid.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Terminal
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
import com.rescuedroid.rescuedroid.viewmodel.AdbConnectionState

@Composable
fun StatusCard(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val isHackerMode by vm.isHackerMode.collectAsStateWithLifecycle()
    val isConnected = session.isReady

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
        border = BorderStroke(1.dp, if (isConnected) Color.Cyan else Color.DarkGray)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(if (isConnected) Color.Cyan else Color.Red, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isConnected) (session.device ?: "MODELO") else "DISPOSITIVO DESCONECTADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(session.status.name, color = Color.Gray, fontSize = 10.sp)
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
