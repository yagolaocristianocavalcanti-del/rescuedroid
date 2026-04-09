package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.model.LogEntry
import com.rescuedroid.rescuedroid.model.LogLevel
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel

@Composable
fun LogcatScreen(vm: MainViewModel) {
    val entries by vm.logcatEntries.collectAsStateWithLifecycle()
    val isStreaming by vm.isStreamingLogcat.collectAsStateWithLifecycle()
    val filter by vm.logcatFilter.collectAsStateWithLifecycle()
    val minLevel by vm.logcatMinLevel.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📋 LOGCAT REAL-TIME", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clearLogcatEntries() }) { Icon(Icons.Default.ClearAll, null, tint = Color.Gray) }
            Button(
                onClick = { if (isStreaming) vm.stopLogcat() else vm.startLogcat() },
                colors = ButtonDefaults.buttonColors(containerColor = if (isStreaming) Color.Red else Color.DarkGray)
            ) {
                Text(if (isStreaming) "PARAR" else "INICIAR")
            }
        }

        Spacer(Modifier.height(8.dp))
        LogLevelSelector(minLevel) { vm.setLogcatMinLevel(it) }

        OutlinedTextField(
            value = filter,
            onValueChange = { vm.updateLogcatFilter(it) },
            label = { Text("Filtrar Tag ou Mensagem", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
        )

        val filteredEntries = entries.filter { 
            it.level.ordinal >= minLevel.ordinal && 
            (it.tag.contains(filter, true) || it.message.contains(filter, true))
        }

        LazyColumn(Modifier.weight(1f).background(Color(0xFF050505)).border(1.dp, Color.DarkGray)) {
            items(filteredEntries) { entry ->
                LogcatEntryItem(entry)
            }
        }
    }
}

@Composable
fun LogcatEntryItem(entry: LogEntry) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row {
            Text(entry.level.code, color = Color(entry.level.color), fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(4.dp))
            Text(entry.tag, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(80.dp), maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(entry.message, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.1f))
    }
}

@Composable
fun LogLevelSelector(selected: LogLevel, onSelect: (LogLevel) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = { Text(level.code, fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(level.color), selectedLabelColor = Color.Black)
            )
        }
    }
}
