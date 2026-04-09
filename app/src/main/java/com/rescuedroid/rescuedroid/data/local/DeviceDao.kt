package com.rescuedroid.rescuedroid.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY lastConnected DESC")
    fun getAllDevices(): Flow<List<KnownDevice>>

    @Query("SELECT * FROM known_devices WHERE serial = :serial LIMIT 1")
    suspend fun getDeviceBySerial(serial: String): KnownDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: KnownDevice)

    @Update
    suspend fun update(device: KnownDevice)

    @Query("UPDATE known_devices SET name = :name WHERE serial = :serial")
    suspend fun updateNickname(serial: String, name: String)

    @Delete
    suspend fun delete(device: KnownDevice)
}
