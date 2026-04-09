package com.rescuedroid.rescuedroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "package_cache")
data class PackageCache(
    @PrimaryKey val packageName: String,
    val isSafe: Boolean,
    val reason: String,
    val iconType: String = "default", // e.g., "safe", "warning", "danger"
    val lastUpdated: Long = System.currentTimeMillis()
)
