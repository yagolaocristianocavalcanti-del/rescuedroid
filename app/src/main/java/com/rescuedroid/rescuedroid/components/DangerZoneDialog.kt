package com.rescuedroid.rescuedroid.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DangerZoneDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("EXECUTAR AÇÃO INSANA", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("abortar", color = Color.Gray)
            }
        },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text(title.uppercase(), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = { 
            Text(message, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace) 
        },
        containerColor = Color.Black,
        shape = RoundedCornerShape(0.dp)
    )
}
