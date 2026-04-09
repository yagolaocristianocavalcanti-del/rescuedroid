package com.rescuedroid.rescuedroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isFromIA: String, // "true" ou "false" para facilitar persistência simples
    val suggestions: String, // JSON ou string separada por vírgula
    val timestamp: Long = System.currentTimeMillis()
)
