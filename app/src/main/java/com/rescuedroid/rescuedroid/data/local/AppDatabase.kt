package com.rescuedroid.rescuedroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class, KnownDevice::class, PackageCache::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun deviceDao(): DeviceDao
    abstract fun packageDao(): PackageDao
}
