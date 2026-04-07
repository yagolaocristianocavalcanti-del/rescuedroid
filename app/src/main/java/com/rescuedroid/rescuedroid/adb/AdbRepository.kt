package com.rescuedroid.rescuedroid.adb

import android.content.Context
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbStream
import com.rescuedroid.rescuedroid.UsbPortState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class FastbootVar(val name: String, val value: String)

@Singleton
class AdbRepository @Inject constructor() {

    val devices: Flow<List<AdbDevice>> = AdbDeviceWatcher.devices

    suspend fun connectUsbTurbo(context: Context, device: AdbDevice): Boolean = withContext(Dispatchers.IO) {
        // Hammer mode: tenta forçar a conexão
        AdbManager.connect(context, device, UsbAdbConnector.ConnectMode.STRONG)
    }

    suspend fun execute(command: String, serial: String? = null): String = withContext(Dispatchers.IO) {
        val target = if (serial != null) {
            AdbDeviceWatcher.devices.value.find { it.serial == serial }?.connection ?: AdbManager.activeConnection
        } else {
            AdbManager.activeConnection
        }

        if (target != null) {
            try {
                AdbManager.executeCommand(command, target = target)
            } catch (e: Exception) {
                "❌ Erro: ${e.message}"
            }
        } else {
            "⚠️ Dispositivo desconectado"
        }
    }

    suspend fun pushFile(bytes: ByteArray, remotePath: String, serial: String? = null): Boolean {
        val target = if (serial != null) {
            AdbDeviceWatcher.devices.value.find { it.serial == serial }?.connection ?: AdbManager.activeConnection
        } else {
            AdbManager.activeConnection
        }
        return AdbManager.pushFile(bytes, remotePath, target)
    }

    suspend fun openShell(command: String, serial: String? = null): AdbStream? = withContext(Dispatchers.IO) {
        val target = if (serial != null) {
            AdbDeviceWatcher.devices.value.find { it.serial == serial }?.connection ?: AdbManager.activeConnection
        } else {
            AdbManager.activeConnection
        }
        AdbManager.openLongLivedShell(command, target = target)
    }

    suspend fun getFastbootVars(serial: String? = null): List<FastbootVar> = withContext(Dispatchers.IO) {
        // No Android, o acesso ao fastboot é limitado. 
        // Se o dispositivo estiver em modo bootloader, o ADB não o verá.
        // Esta implementação é um placeholder para integração futura com driver USB Fastboot nativo.
        val rawOutput = """
            (bootloader) version:0.5
            (bootloader) variant:MTP MSMNILE
            (bootloader) secure:yes
            (bootloader) version-baseband:
            (bootloader) version-bootloader:
            (bootloader) display-panel:
            (bootloader) off-mode-charge:0
            (bootloader) charger-screen-enabled:0
            (bootloader) max-download-size:0x1fa00000
            (bootloader) partition-type:userdata:ext4
            (bootloader) partition-size:userdata: 0x165c400000
            (bootloader) serialno:12345678
            (bootloader) kernel:uefi
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
