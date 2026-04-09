package com.rescuedroid.rescuedroid.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun HackerModeOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "hacker")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanlines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanlineSpacing = 4.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.Green.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += scanlineSpacing
            }
        }

        // Matrix-like falling text (Simplified)
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(5) {
                Text(
                    text = generateRandomBinary(20),
                    color = Color.Green.copy(alpha = 0.15f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            "SYSTEM BREACH SIMULATED",
            color = Color.Green.copy(alpha = 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center)
        )
        
        Text(
            "☢️ MODO HACKER ☢️",
            color = Color.Green.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )
    }
}

private fun generateRandomBinary(length: Int): String {
    return (1..length).map { if (Random.nextBoolean()) "1" else "0" }.joinToString("")
}
