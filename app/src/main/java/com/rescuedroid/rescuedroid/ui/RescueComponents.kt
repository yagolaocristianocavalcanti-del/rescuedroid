package com.rescuedroid.rescuedroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rescuedroid.rescuedroid.components.HackerModeOverlay
import com.rescuedroid.rescuedroid.viewmodel.AdbConnectionState

/**
 * Componentes visuais "travados" para manter a identidade visual OLED/PRO do RescueDroid.
 */

@Composable
fun StatusLed(estado: AdbConnectionState) {
    val cor = when(estado) {
        AdbConnectionState.CONECTADO, AdbConnectionState.CONECTADO_WIFI -> Color.Green
        AdbConnectionState.CONECTANDO -> Color.Yellow
        else -> Color.Red
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(cor, CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun BlueActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModeButton(label: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isSelected) Color(0xFFFFD54F) else Color.Gray
        ),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFD54F) else Color.DarkGray),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text("$icon $label", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KeyButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
