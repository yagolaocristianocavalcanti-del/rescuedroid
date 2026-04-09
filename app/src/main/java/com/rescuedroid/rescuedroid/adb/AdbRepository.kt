package com.rescuedroid.rescuedroid.adb

import android.content.Context
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbStream
import com.rescuedroid.rescuedroid.model.FastbootVar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbRepository @Inject constructor() {

    // val devices: Flow<List<AdbDevice>> = AdbDeviceWatcher.devices

    suspend fun connectUsbTurbo(context: Context, device: AdbDevice): Boolean = withContext(Dispatchers.IO) {
        // AdbManager.connect(context, device, UsbAdbConnector.ConnectMode.STRONG)
        true
    }

    suspend fun execute(command: String, serial: String? = null): String = withContext(Dispatchers.IO) {
        try {
            AdbManager.executeCommand(command)
        } catch (e: Exception) {
            "❌ Erro: ${e.message}"
        }
    }

    suspend fun pushFile(bytes: ByteArray, remotePath: String, serial: String? = null): Boolean {
        return AdbManager.pushFile(bytes, remotePath, null)
    }

    suspend fun openShell(command: String, serial: String? = null): AdbStream? = withContext(Dispatchers.IO) {
        AdbManager.openLongLivedShell(command, target = null)
    }

    suspend fun getFastbootVars(serial: String? = null): List<FastbootVar> = withContext(Dispatchers.IO) {
        val rawOutput = """
            (bootloader) version:0.5
            (bootloader) variant:MTP MSMNILE
            (bootloader) secure:yes
            (bootloader) serialno:12345678
        """.trimIndent()

        parseFastbootVars(rawOutput)
    }

    private fun parseFastbootVars(raw: String): List<FastbootVar> {
        return raw.lines().mapNotNull { line ->
            val clean = line.replace("(bootloader)", "").trim()
            if (clean.contains(":")) {
                val parts = clean.split(":", limit = 2)
                FastbootVar(parts[0].trim(), parts[1].trim())
            } else null
        }
    }

    fun disconnectAll() {
        AdbManager.disconnect()
    }
}
