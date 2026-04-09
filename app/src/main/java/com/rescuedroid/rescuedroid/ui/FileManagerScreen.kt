package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
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
import com.rescuedroid.rescuedroid.model.ArquivoAdb

@Composable
fun FileManagerScreen(vm: MainViewModel) {
    val arquivos by vm.arquivosAtuais.collectAsStateWithLifecycle()
    val path by vm.caminhoAtual.collectAsStateWithLifecycle()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF0A0A0A)).padding(8.dp).fillMaxWidth()) {
            IconButton(onClick = { vm.voltarDiretorio() }) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Cyan) 
            }
            Text(path, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { vm.listarArquivos(path) }) { 
                Icon(Icons.Default.Refresh, null, tint = Color.Green) 
            }
        }
        
        HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = Color(0xFF222222))
        
        LazyColumn(Modifier.weight(1f)) {
            items(arquivos.sortedWith(compareByDescending<ArquivoAdb> { it.eDiretorio }.thenBy { it.nome })) { arquivo ->
                ArquivoItem(arquivo, 
                    onClick = { 
                        val newPath = if (path.endsWith("/")) "$path${arquivo.nome}" else "$path/${arquivo.nome}"
                        if (arquivo.eDiretorio) vm.listarArquivos("$newPath/") 
                    },
                    onDelete = { vm.deleteFile(if (path.endsWith("/")) "$path${arquivo.nome}" else "$path/${arquivo.nome}") }
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ActionButton("ROOT EXPLORER", onClick = { vm.listarArquivos("/") })
            Spacer(Modifier.width(8.dp))
            ActionButton("SDCARD", onClick = { vm.listarArquivos("/sdcard/") })
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
