package com.rescuedroid.rescuedroid.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(vm: MainViewModel) {
    val messages by vm.ultimoComandoIA.collectAsStateWithLifecycle()
    val isProcessing by vm.isIAProcessing.collectAsStateWithLifecycle()
    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            vm.processarComandoIA(matches[0])
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "🤖 RESCUE IA ATIVA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Cyan
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Quick Commands
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item { QuickCmdChip("🔌 Conecta USB") { vm.processarComandoIA("conecta usb turbo") } }
                item { QuickCmdChip("📸 Screenshot") { vm.processarComandoIA("screenshot") } }
                item { QuickCmdChip("🧹 Limpar Lixo") { vm.processarComandoIA("limpa lixo") } }
                item { QuickCmdChip("🔓 Desbloquear") { vm.processarComandoIA("desbloquear") } }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatBubbleIA(msg, onSuggestionClick = { vm.processarComandoIA(it) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                label = { Text("Comando (ex: 'conecta usb', 'print')") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                enabled = !isProcessing,
                trailingIcon = {
                    if (isProcessing) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.Cyan, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { 
                            if (chatInput.isNotBlank()) {
                                vm.processarComandoIA(chatInput)
                                chatInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.Cyan)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.Cyan,
                    cursorColor = Color.Cyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // FAB MIC PARA VOZ - Ajustado para não sobrepor o FAB Global
        FloatingActionButton(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o comando...")
                }
                voiceLauncher.launch(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 170.dp, end = 16.dp), // Aumentado de 90.dp para 170.dp
            containerColor = Color.Magenta,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Mic, "Falar")
        }
    }
}

@Composable
fun QuickCmdChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = Color.Cyan,
            containerColor = Color.Cyan.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubbleIA(comando: MainViewModel.IAComando, onSuggestionClick: (String) -> Unit) {
    val isIA = comando.isFromIA
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(comando.timestamp) { timeFormat.format(Date(comando.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isIA) Alignment.Start else Alignment.End
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!isIA) Text(timeStr, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
            
            Surface(
                color = if (isIA) Color.Cyan.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isIA) 4.dp else 16.dp,
                    bottomEnd = if (isIA) 16.dp else 4.dp
                ),
                border = if (isIA) BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)) else null
            ) {
                Text(
                    text = comando.texto,
                    modifier = Modifier.padding(12.dp),
                    color = if (isIA) Color.Cyan else Color.White,
                    fontSize = 14.sp
                )
            }
            
            if (isIA) Text(timeStr, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        }

        if (isIA && comando.sugestoes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (sugestao in comando.sugestoes) {
                    AssistChip(
                        onClick = { onSuggestionClick(sugestao) },
                        label = { Text(text = sugestao, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = Color.Cyan,
                            containerColor = Color.Cyan.copy(alpha = 0.1f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color.Cyan.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}
