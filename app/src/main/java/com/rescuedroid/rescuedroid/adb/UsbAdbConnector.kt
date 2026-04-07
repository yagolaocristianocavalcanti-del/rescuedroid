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
            handshakeAttempts = 1,
            handshakeTimeoutMs = 7000L,
            retryDelayMs = 1200L,
            permissionTimeoutMs = 12000L,
            reenumerationTimeoutMs = 4000L,
            rotateKeyOnRetry = false,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        STRONG(
            label = "forte",
            handshakeAttempts = 3,
            handshakeTimeoutMs = 10000L,
            retryDelayMs = 2200L,
            permissionTimeoutMs = 18000L,
            reenumerationTimeoutMs = 7000L,
            rotateKeyOnRetry = false,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        HAMMER(
            label = "martelo",
            handshakeAttempts = 5,
            handshakeTimeoutMs = 13000L,
            retryDelayMs = 3000L,
            permissionTimeoutMs = 22000L,
            reenumerationTimeoutMs = 10000L,
            rotateKeyOnRetry = true,
            connectVersion = AdbProtocol.CONNECT_VERSION,
            connectMaxData = 16384
        ),
        LEGACY(
            label = "legado",
            handshakeAttempts = 3,
            handshakeTimeoutMs = 16000L,
            retryDelayMs = 3600L,
            permissionTimeoutMs = 22000L,
            reenumerationTimeoutMs = 12000L,
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
            Log.d(TAG, "Solicitando permissao USB no modo ${mode.label}...")
            val permissionResult = requestPermission(context, usbManager, device, mode.permissionTimeoutMs)
            if (permissionResult != UsbPermissionResult.GRANTED) {
                lastErrorMessage = when (permissionResult) {
                    UsbPermissionResult.DENIED -> "Permissao USB negada pelo usuario"
                    UsbPermissionResult.TIMEOUT -> "Popup de permissao USB nao respondeu"
                    UsbPermissionResult.GRANTED -> ""
                }
                Log.e(TAG, lastErrorMessage)
                return@withContext false
            }

            // Muitos aparelhos re-enumeram o USB logo apos o OK do popup.
            onProgress?.invoke(ProgressEvent.PERMISSION_GRANTED)
            onProgress?.invoke(ProgressEvent.WAITING_DEVICE_SETTLE)
            waitForDeviceSettling(usbManager, device, mode.reenumerationTimeoutMs)
        } else {
            onProgress?.invoke(ProgressEvent.PERMISSION_GRANTED)
        }

        val iface = findAdbInterface(device)
        if (iface == null) {
            lastErrorMessage = "Nenhuma interface ADB encontrada no dispositivo USB"
            return@withContext false
        }
        onProgress?.invoke(ProgressEvent.DEVICE_READY)

        val keyManager = AdbKeyManager(context)
        var crypto = keyManager.getOrCreateCrypto() ?: run {
            lastErrorMessage = "Nao foi possivel carregar ou gerar a chave ADB"
            return@withContext false
        }

        repeat(mode.handshakeAttempts) { index ->
            val attempt = index + 1
            if (mode.rotateKeyOnRetry && attempt > 1) {
                crypto = keyManager.forceRegenerateCrypto() ?: crypto
                Log.w(TAG, "Regenerando chave ADB para tentativa $attempt/${mode.handshakeAttempts}")
            }

            val currentDevice = waitForCurrentDevice(usbManager, device, mode.reenumerationTimeoutMs)
            if (currentDevice == null) {
                lastErrorMessage = "O dispositivo USB foi reconectado, removido ou trocou de identificador"
                if (attempt == mode.handshakeAttempts) return@withContext false
                delay(mode.retryDelayMs)
                return@repeat
            }

            val currentInterface = findAdbInterface(currentDevice)
            if (currentInterface == null) {
                lastErrorMessage = "Nenhuma interface ADB encontrada no dispositivo USB"
                if (attempt == mode.handshakeAttempts) return@withContext false
                delay(mode.retryDelayMs)
                return@repeat
            }

            val usbConnection = openDeviceWithRetries(
                context = context,
                usbManager = usbManager,
                originalDevice = device,
                initialDevice = currentDevice,
                permissionTimeoutMs = mode.permissionTimeoutMs,
                timeoutMs = mode.reenumerationTimeoutMs,
                retryDelayMs = 350L
            )
            if (usbConnection == null) {
                if (lastErrorMessage.isBlank()) {
                    lastErrorMessage = "Permissao USB concedida, mas o Android nao liberou acesso ao dispositivo"
                }
                return@withContext false
            }

            var adbConnInstance: AdbConnection? = null
            try {
                val claimed = claimInterfaceWithRetries(
                    usbConnection = usbConnection,
                    currentInterface = currentInterface,
                    timeoutMs = mode.reenumerationTimeoutMs,
                    retryDelayMs = 300L
                )
                if (!claimed) {
                    lastErrorMessage = "Nao foi possivel assumir a interface USB ADB"
                    usbConnection.close()
                    if (attempt == mode.handshakeAttempts) return@withContext false
                    delay(mode.retryDelayMs)
                    return@repeat
                }

                val epIn = (0 until currentInterface.endpointCount)
                    .map { currentInterface.getEndpoint(it) }
                    .firstOrNull {
                        it.direction == UsbConstants.USB_DIR_IN &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    }
                val epOut = (0 until currentInterface.endpointCount)
                    .map { currentInterface.getEndpoint(it) }
                    .firstOrNull {
                        it.direction == UsbConstants.USB_DIR_OUT &&
                            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    }

                if (epIn == null || epOut == null) {
                    lastErrorMessage = "Endpoints USB ADB nao encontrados"
                    usbConnection.close()
                    return@withContext false
                }

                val channel = UsbChannel(usbConnection, epIn, epOut)
                val adbConn = AdbConnection.create(
                    channel,
                    crypto,
                    mode.connectVersion,
                    mode.connectMaxData
                )
                adbConnInstance = adbConn
                onProgress?.invoke(ProgressEvent.SAYING_HELLO)
                
                // Pequeno delay para estabilizacao eletrica/protocolo antes do handshake
                delay(400L)
                
                onProgress?.invoke(ProgressEvent.HANDSHAKE_START)
                
                // Tenta o handshake com um retry interno rapido se falhar por timeout
                var connected = false
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < mode.handshakeTimeoutMs && !connected) {
                    try {
                        // Passamos o timeout restante para o connect interno
                        val remaining = mode.handshakeTimeoutMs - (System.currentTimeMillis() - startTime)
                        if (remaining <= 0) break
                        
                        adbConn.connect(remaining.coerceAtLeast(3000L))
                        connected = true
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        Log.w(TAG, "Tentativa de handshake falhou: $msg")
                        
                        // Se o erro for de escrita ou I/O, abortamos este canal imediatamente
                        if (msg.contains("escrita", ignoreCase = true) || 
                            msg.contains("read", ignoreCase = true) || 
                            e is java.io.IOException) {
                            throw e 
                        }
                        
                        // No modo MARTELO, se falhar por timeout de handshake, tentamos novamente rápido
                        // sem fechar o canal USB se possível, ou deixamos o loop externo cuidar disso.
                        if (mode == ConnectMode.HAMMER) {
                             delay(500)
                        } else {
                             break // Outros modos saem para o retry externo (que reabre o USB)
                        }
                    }
                    
                    if (connected) {
                        Log.d(TAG, "Handshake concluido com sucesso!")
                        break // Sai do loop de handshake interno
                    }
                }

                if (connected) {
                    AdbManager.setUsbConnection(adbConn)
                    Log.d(TAG, "Conexão USB estabelecida com sucesso no modo ${mode.label}")
                    // IMPORTANTE: Não fechamos usbConnection aqui pois adbConn agora a possui via UsbChannel
                    return@withContext true 
                }

                lastErrorMessage =
                    "Permissão USB concedida, mas o handshake ADB falhou no outro aparelho (tentativa $attempt/${mode.handshakeAttempts}, modo ${mode.label})"
            } catch (e: Exception) {
                lastErrorMessage =
                    e.message ?: "Erro no handshake USB (tentativa $attempt/${mode.handshakeAttempts}, modo ${mode.label})"
                Log.e(TAG, "Erro no handshake USB: ${e.message}", e)
            } finally {
                // Só fechamos se não tivermos tido sucesso em estabelecer a conexão global
                // ou se a conexão que acabamos de criar NÃO for a mesma que está no AdbManager
                val currentConn = AdbManager.usbConnection
                if (currentConn == null || currentConn.isClosed || currentConn !== adbConnInstance) {
                    runCatching { usbConnection.releaseInterface(currentInterface) }
                    runCatching { usbConnection.close() }
                }
            }

            if (attempt < mode.handshakeAttempts) {
                delay(mode.retryDelayMs)
            }
        }

        false
    }

    private fun findAdbInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        // Itera sobre todas as interfaces para encontrar explicitamente a do ADB (Vendor Class 0xFF)
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                iface.interfaceSubclass == 0x42 &&
                iface.interfaceProtocol == 0x01
            ) {
                return iface
            }
        }
        return null
    }

    private fun resolveCurrentDevice(usbManager: UsbManager, originalDevice: UsbDevice): UsbDevice? {
        usbManager.deviceList.values.firstOrNull { it.deviceName == originalDevice.deviceName }?.let {
            return it
        }
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == originalDevice.vendorId &&
                it.productId == originalDevice.productId &&
                findAdbInterface(it) != null
        }
    }

    private suspend fun waitForDeviceSettling(
        usbManager: UsbManager,
        originalDevice: UsbDevice,
        timeoutMs: Long
    ) {
        waitForCurrentDevice(usbManager, originalDevice, timeoutMs)
        delay(500L)
    }

    private suspend fun waitForCurrentDevice(
        usbManager: UsbManager,
        originalDevice: UsbDevice,
        timeoutMs: Long
    ): UsbDevice? {
        val startedAt = System.currentTimeMillis()
        var lastSeen: UsbDevice? = null
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val device = resolveCurrentDevice(usbManager, originalDevice)
            if (device != null) {
                lastSeen = device
                if (findAdbInterface(device) != null) {
                    return device
                }
            }
            delay(250L)
        }
        return lastSeen
    }

    private suspend fun openDeviceWithRetries(
        context: Context,
        usbManager: UsbManager,
        originalDevice: UsbDevice,
        initialDevice: UsbDevice,
        permissionTimeoutMs: Long,
        timeoutMs: Long,
        retryDelayMs: Long
    ) = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var deviceCandidate: UsbDevice? = initialDevice
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val activeDevice = deviceCandidate ?: resolveCurrentDevice(usbManager, originalDevice)
            if (activeDevice != null) {
                if (!usbManager.hasPermission(activeDevice)) {
                    lastErrorMessage = "O dispositivo USB foi reenumerado e precisa de permissao novamente"
                    val permissionResult = requestPermission(
                        context = context,
                        manager = usbManager,
                        device = activeDevice,
                        timeoutMs = permissionTimeoutMs
                    )
                    if (permissionResult != UsbPermissionResult.GRANTED) {
                        lastErrorMessage = when (permissionResult) {
                            UsbPermissionResult.DENIED -> "Permissao USB negada pelo usuario"
                            UsbPermissionResult.TIMEOUT -> "Popup de permissao USB nao respondeu"
                            UsbPermissionResult.GRANTED -> ""
                        }
                        delay(retryDelayMs)
                        deviceCandidate = resolveCurrentDevice(usbManager, originalDevice)
                        continue
                    }
                    delay(300L)
                }
                try {
                    usbManager.openDevice(activeDevice)?.let { return@withContext it }
                    lastErrorMessage = "Permissao USB concedida, mas o Android ainda nao liberou acesso ao dispositivo"
                } catch (e: IllegalArgumentException) {
                    lastErrorMessage = "O Android recusou abrir o dispositivo USB atual; tente reconectar o cabo"
                    Log.e(TAG, lastErrorMessage, e)
                } catch (e: SecurityException) {
                    lastErrorMessage = "O Android reenumerou o USB e removeu a permissao do app para o dispositivo atual"
                    Log.e(TAG, lastErrorMessage, e)
                }
            } else {
                lastErrorMessage = "O dispositivo USB foi reconectado, removido ou trocou de identificador"
            }
            delay(retryDelayMs)
            deviceCandidate = resolveCurrentDevice(usbManager, originalDevice)
        }
        null
    }

    private suspend fun claimInterfaceWithRetries(
        usbConnection: android.hardware.usb.UsbDeviceConnection,
        currentInterface: android.hardware.usb.UsbInterface,
        timeoutMs: Long,
        retryDelayMs: Long
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (usbConnection.claimInterface(currentInterface, true)) {
                return true
            }
            delay(retryDelayMs)
        }
        return false
    }

    private enum class UsbPermissionResult { GRANTED, DENIED, TIMEOUT }

    private suspend fun requestPermission(
        context: Context,
        manager: UsbManager,
        device: UsbDevice,
        timeoutMs: Long
    ): UsbPermissionResult {
        val deferred = CompletableDeferred<UsbPermissionResult>()
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        deferred.complete(
                            if (granted) UsbPermissionResult.GRANTED else UsbPermissionResult.DENIED
                        )
                    }
                    context.unregisterReceiver(this)
                }
            }
        }

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent =
            PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        manager.requestPermission(device, permissionIntent)
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: UsbPermissionResult.TIMEOUT
    }
}
