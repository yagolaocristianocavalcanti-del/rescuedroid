package com.rescuedroid.rescuedroid.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.cgutman.adblib.AdbConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UsbAdbConnector {
    private const val ACTION_USB_PERMISSION = "com.rescuedroid.USB_PERMISSION"
    private const val TAG = "UsbAdbConnector"

    suspend fun connect(context: Context, device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        
        if (!usbManager.hasPermission(device)) {
            Log.d(TAG, "Solicitando permissão para o dispositivo USB...")
            val permissionGranted = requestPermission(context, usbManager, device)
            if (!permissionGranted) {
                Log.e(TAG, "Permissão USB negada pelo usuário")
                return@withContext false
            }
        }

        try {
            val usbConnection = usbManager.openDevice(device) ?: return@withContext false
            val iface = device.getInterface(0)
            usbConnection.claimInterface(iface, true)

            val epIn = iface.getEndpoint(0)
            val epOut = iface.getEndpoint(1)

            val channel = UsbChannel(usbConnection, epIn, epOut)
            val keyManager = AdbKeyManager(context)
            val crypto = keyManager.getOrCreateCrypto() ?: return@withContext false

            // Usando a conexão local que aceita o canal USB
            val adbConn = AdbConnection.create(channel, crypto)
            adbConn.connect()
            
            AdbManager.connection = adbConn
            Log.d(TAG, "Conexão USB estabelecida com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro no handshake USB: ${e.message}", e)
            false
        }
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
        context.registerReceiver(usbReceiver, filter)

        manager.requestPermission(device, permissionIntent)
        return deferred.await()
    }
}
