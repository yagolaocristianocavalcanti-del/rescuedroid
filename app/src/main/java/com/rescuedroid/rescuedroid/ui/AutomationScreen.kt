package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.model.ScriptLocal

@Composable
fun AutomationScreen(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val scripts by vm.scriptsLocais.collectAsStateWithLifecycle()
    val isExecutando by vm.isExecutandoScript.collectAsStateWithLifecycle()
    
    val isConnected = session.isReady

    var showEditor by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<ScriptLocal?>(null) }

    if (showEditor) {
        ScriptEditorDialog(
            script = editingScript,
            onSave = { nome, conteudo ->
                vm.salvarScript(nome, conteudo)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    LaunchedEffect(Unit) { vm.carregarScripts() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🚀 AUTOMAÇÃO & SCRIPTS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        if (isExecutando) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Cyan)
            Text("Executando script no dispositivo...", color = Color.Cyan, fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(scripts) { script ->
                ScriptItem(script, isExecutando || !isConnected,
                    onRun = { vm.executarScript(script) },
                    onDelete = { vm.deletarScript(script) }
                )
            }
        }
        
        Button(
            onClick = { 
                editingScript = null
                showEditor = true 
            }, 
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("NOVO SCRIPT (.SH)")
        }
    }
}

@Composable
fun ScriptEditorDialog(
    script: ScriptLocal?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var nome by remember { mutableStateOf(script?.nome ?: "meu_script.sh") }
    var conteudo by remember { mutableStateOf(script?.conteudo ?: "#!/system/bin/sh\n\n") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        title = { Text("Editor de Script ADB", color = Color.Cyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do Arquivo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = conteudo,
                    onValueChange = { conteudo = it },
                    label = { Text("Conteúdo Shell") },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nome, conteudo) }) {
                Text("SALVAR", color = Color.Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ScriptItem(script: ScriptLocal, isRunning: Boolean, onRun: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Text(script.nome, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onRun, enabled = !isRunning) { Icon(Icons.Default.PlayArrow, null, tint = Color.Green) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.DarkGray) }
        }
    }
}
