package com.rescuedroid.rescuedroid.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object UsbAdbConnector {
    enum class ProgressEvent {
        REQUESTING_PERMISSION,
        PERMISSION_GRANTED,
        WAITING_DEVICE_SETTLE,
        DEVICE_READY,
        SAYING_HELLO,
        HANDSHAKE_START
    }

    enum class ConnectMode(
        val label: String,
        val handshakeAttempts: Int,
        val handshakeTimeoutMs: Long,
        val retryDelayMs: Long,
        val permissionTimeoutMs: Long,
        val reenumerationTimeoutMs: Long,
        val rotateKeyOnRetry: Boolean,
        val connectVersion: Int,
        val connectMaxData: Int
    ) {
        NORMAL(
            label = "normal",
            handshakeAttempts = 2,
            handshakeTimeoutMs = 30000L,
            retryDelayMs = 2000L,
            permissionTimeoutMs = 15000L,
            reenumerationTimeoutMs = 5000L,
            rotateKeyOnRetry = false,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        STRONG(
            label = "forte",
            handshakeAttempts = 4,
            handshakeTimeoutMs = 45000L,
            retryDelayMs = 3000L,
            permissionTimeoutMs = 20000L,
            reenumerationTimeoutMs = 8000L,
            rotateKeyOnRetry = false,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        HAMMER(
            label = "martelo",
            handshakeAttempts = 6,
            handshakeTimeoutMs = 60000L,
            retryDelayMs = 4000L,
            permissionTimeoutMs = 30000L,
            reenumerationTimeoutMs = 12000L,
            rotateKeyOnRetry = true,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        LEGACY(
            label = "legado",
            handshakeAttempts = 4,
            handshakeTimeoutMs = 60000L,
            retryDelayMs = 4000L,
            permissionTimeoutMs = 30000L,
            reenumerationTimeoutMs = 15000L,
            rotateKeyOnRetry = true,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = AdbProtocol.CONNECT_MAXDATA
        )
    }

    private const val ACTION_USB_PERMISSION = "com.rescuedroid.USB_PERMISSION"
    private const val TAG = "UsbAdbConnector"

    var lastErrorMessage: String = ""
        private set

    var lastAttemptMode: ConnectMode = ConnectMode.NORMAL
        private set

    fun findAdbCapableDevice(context: Context): UsbDevice? {
        return findAllAdbCapableDevices(context).firstOrNull()
    }

    fun findAllAdbCapableDevices(context: Context): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.filter { findAdbInterface(it) != null }
    }

    suspend fun connect(
        context: Context,
        device: UsbDevice,
        mode: ConnectMode = ConnectMode.NORMAL,
        onProgress: ((ProgressEvent) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        lastErrorMessage = ""
        lastAttemptMode = mode

        if (!usbManager.hasPermission(device)) {
            onProgress?.invoke(ProgressEvent.REQUESTING_PERMISSION)
            val permissionResult = requestPermission(context, usbManager, device, mode.permissionTimeoutMs)
            if (permissionResult != UsbPermissionResult.GRANTED) {
                lastErrorMessage = when (permissionResult) {
                    UsbPermissionResult.DENIED -> "Permissão USB negada"
                    UsbPermissionResult.TIMEOUT -> "Tempo de permissão esgotado"
                    else -> "Falha na permissão"
                }
                return@withContext false
            }
            onProgress?.invoke(ProgressEvent.PERMISSION_GRANTED)
            delay(1000) // Tempo para o dispositivo re-enumerar
        }

        val iface = findAdbInterface(device) ?: run {
            lastErrorMessage = "Interface ADB não encontrada"
            return@withContext false
        }
        onProgress?.invoke(ProgressEvent.DEVICE_READY)

        val keyManager = AdbKeyManager(context)
        var crypto = keyManager.getOrCreateCrypto() ?: return@withContext false

        repeat(mode.handshakeAttempts) { index ->
            val attempt = index + 1
            val usbConnection = usbManager.openDevice(device) ?: return@repeat
            
            var adbConnCreated = false
            try {
                if (usbConnection.claimInterface(iface, true)) {
                    val epIn = (0 until iface.endpointCount).map { iface.getEndpoint(it) }.first { it.direction == UsbConstants.USB_DIR_IN }
                    val epOut = (0 until iface.endpointCount).map { iface.getEndpoint(it) }.first { it.direction == UsbConstants.USB_DIR_OUT }

                    val channel = UsbChannel(usbConnection, epIn, epOut)
                    val adbConn = AdbConnection.create(channel, crypto, mode.connectVersion, mode.connectMaxData)
                    
                    onProgress?.invoke(ProgressEvent.HANDSHAKE_START)
                    try {
                        adbConn.connect(mode.handshakeTimeoutMs)
                        AdbManager.setUsbConnection(adbConn)
                        adbConnCreated = true
                        return@withContext true
                    } catch (e: Exception) {
                        lastErrorMessage = "Falha no Handshake: ${e.message}"
                        // Adblib chama channel.close() em caso de falha, que fecha o usbConnection.
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = "Erro USB: ${e.message}"
            } finally {
                // Se não criou a conexão global, garantimos que esta instância local de USB seja fechada
                // Mas apenas se o adbConn já não tiver fechado ela via channel.close()
                if (!adbConnCreated) {
                    try { usbConnection.releaseInterface(iface) } catch (e: Exception) {}
                    try { usbConnection.close() } catch (e: Exception) {}
                }
            }
            delay(mode.retryDelayMs)
        }
        false
    }

    private fun findAdbInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 0xFF && iface.interfaceSubclass == 0x42) return iface
        }
        return null
    }

    private enum class UsbPermissionResult { GRANTED, DENIED, TIMEOUT }

    private suspend fun requestPermission(
        context: Context,
        manager: UsbManager,
        device: UsbDevice,
        timeoutMs: Long
    ): UsbPermissionResult {
        val deferred = CompletableDeferred<UsbPermissionResult>()
        val unregisterCalled = AtomicBoolean(false)
        
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    deferred.complete(if (granted) UsbPermissionResult.GRANTED else UsbPermissionResult.DENIED)
                }
            }
        }

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            manager.requestPermission(device, permissionIntent)
            return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: UsbPermissionResult.TIMEOUT
        } finally {
            if (unregisterCalled.compareAndSet(false, true)) {
                try { context.unregisterReceiver(usbReceiver) } catch (e: Exception) {
                    Log.w(TAG, "Erro ao desregistrar receiver: ${e.message}")
                }
            }
        }
    }
}
