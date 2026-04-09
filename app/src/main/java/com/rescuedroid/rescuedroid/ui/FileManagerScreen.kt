package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuedroid.rescuedroid.viewmodel.MainViewModel
import com.rescuedroid.rescuedroid.model.ArquivoAdb
import kotlinx.coroutines.delay

@Composable
fun FileManagerScreen(vm: MainViewModel) {
    val arquivos by vm.arquivosAtuais.collectAsStateWithLifecycle()
    val path by vm.caminhoAtual.collectAsStateWithLifecycle()
    val isConnected by vm.isConnected.collectAsStateWithLifecycle()
    
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Efeito para resetar o scroll ao mudar de pasta e gerenciar loading
    LaunchedEffect(path) {
        isLoading = true
        listState.scrollToItem(0)
        delay(300) // Pequeno delay para a UI respirar
        isLoading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar de Navegação
        Surface(
            color = Color(0xFF0A0A0A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF222222)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = { vm.voltarDiretorio() }) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Cyan) 
                }
                
                Text(
                    text = path, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 11.sp, 
                    modifier = Modifier.weight(1f), 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        color = Color.Cyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { vm.listarArquivos(path) }) { 
                        Icon(Icons.Default.Refresh, null, tint = Color.Green) 
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))

        if (!isConnected) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("⚠️ DISPOSITIVO DESCONECTADO", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else if (arquivos.isEmpty() && !isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                    Text("Pasta vazia ou sem permissão", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(
                    items = arquivos.sortedWith(compareByDescending<ArquivoAdb> { it.eDiretorio }.thenBy { it.nome }),
                    key = { it.nome + it.eDiretorio } // Melhor performance e animações
                ) { arquivo ->
                    ArquivoItem(arquivo, 
                        onClick = { 
                            if (arquivo.eDiretorio) {
                                val newPath = if (path.endsWith("/")) "$path${arquivo.nome}/" else "$path/${arquivo.nome}/"
                                vm.listarArquivos(newPath)
                            }
                        },
                        onDelete = { 
                            val fullPath = if (path.endsWith("/")) "$path${arquivo.nome}" else "$path/${arquivo.nome}"
                            vm.deleteFile(fullPath) 
                        }
                    )
                    HorizontalDivider(color = Color(0xFF111111), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            ActionButton("ROOT /", modifier = Modifier.weight(1f), onClick = { vm.listarArquivos("/") })
            Spacer(Modifier.width(8.dp))
            ActionButton("SDCARD", modifier = Modifier.weight(1f), onClick = { vm.listarArquivos("/sdcard/") })
        }
    }
}

@Composable
fun ArquivoItem(arquivo: ArquivoAdb, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (arquivo.eDiretorio) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            null,
            tint = if (arquivo.eDiretorio) Color.Cyan else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(arquivo.nome, color = Color.White, fontSize = 13.sp)
            Text("${arquivo.tamanho} | ${arquivo.permissao}", color = Color.Gray, fontSize = 10.sp)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.DarkGray, modifier = Modifier.size(18.dp)) }
    }
}
