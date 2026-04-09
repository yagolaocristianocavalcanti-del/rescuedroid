package com.rescuedroid.rescuedroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_devices")
data class KnownDevice(
    @PrimaryKey val serial: String,
    val name: String,
    val lastConnected: Long = System.currentTimeMillis(),
    val connectionCount: Int = 1,
    val isFavorite: Boolean = false,
    val notes: String? = null
)
