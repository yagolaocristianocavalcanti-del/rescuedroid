package com.rescuedroid.rescuedroid.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.util.Log
import com.cgutman.adblib.AdbConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object UsbAdbConnector {
    private const val ACTION_USB_PERMISSION = "com.rescuedroid.USB_PERMISSION"
    private const val TAG = "UsbAdbConnector"
    private const val USB_PERMISSION_TIMEOUT_MS = 8000L
    private const val USB_HANDSHAKE_TIMEOUT_MS = 4000L
    var lastErrorMessage: String = ""
        private set

    suspend fun connect(context: Context, device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        lastErrorMessage = ""
        
        if (!usbManager.hasPermission(device)) {
            Log.d(TAG, "Solicitando permissão para o dispositivo USB...")
            val permissionGranted = requestPermission(context, usbManager, device)
            if (!permissionGranted) {
                Log.e(TAG, "Permissão USB negada pelo usuário")
                lastErrorMessage = "Permissao USB negada ou popup nao respondido"
                return@withContext false
            }
        }

        try {
            val usbConnection = usbManager.openDevice(device) ?: return@withContext false
            val iface = findAdbInterface(device) ?: run {
                lastErrorMessage = "Nenhuma interface ADB encontrada no dispositivo USB"
                usbConnection.close()
                return@withContext false
            }
            usbConnection.claimInterface(iface, true)

            val epIn = (0 until iface.endpointCount)
                .map { iface.getEndpoint(it) }
                .firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            val epOut = (0 until iface.endpointCount)
                .map { iface.getEndpoint(it) }
                .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }

            if (epIn == null || epOut == null) {
                lastErrorMessage = "Endpoints USB ADB nao encontrados"
                usbConnection.close()
                return@withContext false
            }

            val channel = UsbChannel(usbConnection, epIn, epOut)
            val keyManager = AdbKeyManager(context)
            val crypto = keyManager.getOrCreateCrypto() ?: run {
                lastErrorMessage = "Nao foi possivel carregar ou gerar a chave ADB"
                usbConnection.close()
                return@withContext false
            }

            // Usando a conexão local que aceita o canal USB
            val adbConn = AdbConnection.create(channel, crypto)
            val connected = withTimeoutOrNull(USB_HANDSHAKE_TIMEOUT_MS) {
                adbConn.connect()
                true
            } ?: false

            if (!connected) {
                lastErrorMessage = "Tempo limite no handshake USB ADB"
                usbConnection.close()
                return@withContext false
            }
            
            AdbManager.connection = adbConn
            Log.d(TAG, "Conexão USB estabelecida com sucesso")
            true
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Erro no handshake USB"
            Log.e(TAG, "Erro no handshake USB: ${e.message}", e)
            false
        }
    }

    private fun findAdbInterface(device: UsbDevice) =
        (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull {
                it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    it.interfaceSubclass == 0x42 &&
                    it.interfaceProtocol == 0x01
            }

    private suspend fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        deferred.complete(granted)
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
        
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        manager.requestPermission(device, permissionIntent)
        return withTimeoutOrNull(USB_PERMISSION_TIMEOUT_MS) { deferred.await() } ?: false
    }
}
