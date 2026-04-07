package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbDevice
import com.rescuedroid.rescuedroid.adb.AdbShell
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor para execução automática de scripts de otimização/preparação.
 */
@Singleton
class AutoScriptEngine @Inject constructor(
    private val adbShell: AdbShell
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun runDefault(device: AdbDevice) {
        scope.launch {
            // Aumenta timeout de tela para facilitar debug (10 minutos)
            adbShell.runForDevice(device.serial, "settings put system screen_off_timeout 600000")
            // Evita que o dispositivo entre em suspensão quando carregando
            adbShell.runForDevice(device.serial, "svc power stayon true")
            // Simula pressionar a tecla Menu/Desbloquear
            adbShell.runForDevice(device.serial, "input keyevent 82")
        }
    }
}
