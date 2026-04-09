package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
