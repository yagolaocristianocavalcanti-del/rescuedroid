package com.rescuedroid.rescuedroid.control

import com.rescuedroid.rescuedroid.adb.AdbShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine de controle remoto estilo AnyDesk via injeção de eventos ADB.
 */
@Singleton
class RemoteControl @Inject constructor(
    private val adbShell: AdbShell
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun tap(serial: String, x: Int, y: Int) {
        scope.launch {
            adbShell.runForDevice(serial, "input tap $x $y")
        }
    }

    fun swipe(serial: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300) {
        scope.launch {
            adbShell.runForDevice(serial, "input swipe $x1 $y1 $x2 $y2 $durationMs")
        }
    }

    fun text(serial: String, text: String) {
        scope.launch {
            // Escapa aspas para evitar erros no shell
            val escaped = text.replace("'", "\\'")
            adbShell.runForDevice(serial, "input text '$escaped'")
        }
    }

    fun keyEvent(serial: String, keyCode: Int) {
        scope.launch {
            adbShell.runForDevice(serial, "input keyevent $keyCode")
        }
    }
}
