package com.rescuedroid.rescuedroid.adb

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Observa dispositivos ADB conectados em tempo real.
 */
object AdbDeviceWatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val devices: StateFlow<List<AdbDevice>> = _devices

    fun start(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            while (isActive) {
                try {
                    // Obtém a lista de dispositivos através do AdbManager
                    val connectedDevices = AdbManager.listDevices(appContext)
                    _devices.value = connectedDevices
                } catch (_: Exception) {}

                delay(2000)
            }
        }
    }
}
