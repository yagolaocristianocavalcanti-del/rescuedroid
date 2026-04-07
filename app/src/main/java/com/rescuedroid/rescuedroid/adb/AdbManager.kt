package com.rescuedroid.rescuedroid.adb

import android.content.Context
import android.util.Log
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.cgutman.adblib.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * AdbManager utilizando a biblioteca local com.cgutman.adblib
 */
enum class DeviceStatus {
    ONLINE, OFFLINE, UNAUTHORIZED, CONNECTING, DISCONNECTED
}

data class AdbDevice(
    val serial: String,
    val status: DeviceStatus,
    val model: String? = null,
    val type: String = "USB", // "USB" ou "WIFI"
    val connection: AdbConnection? = null,
    val usbDevice: android.hardware.usb.UsbDevice? = null
)

object AdbManager {
    private const val TAG = "AdbManager"
    private const val REMOTE_PATH = "/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin"
    private const val CONNECT_TIMEOUT_MS = 5000L
    private const val HANDSHAKE_TIMEOUT_MS = 10000L
    private const val DEFAULT_COMMAND_TIMEOUT_MS = 5000L
    
    @Volatile
    var wifiConnection: AdbConnection? = null
        private set
    
    @Volatile
    var usbConnection: AdbConnection? = null
        private set

    /**
     * Retorna a conexão USB se disponível, caso contrário a Wi-Fi.
     */
    val activeConnection: AdbConnection? 
        get() = usbConnection ?: wifiConnection

    private var crypto: AdbCrypto? = null
    var lastErrorMessage: String = ""
        private set

    suspend fun connect(context: Context, device: AdbDevice, mode: UsbAdbConnector.ConnectMode = UsbAdbConnector.ConnectMode.NORMAL): Boolean = withContext(Dispatchers.IO) {
        if (device.type == "USB" && device.usbDevice != null) {
            return@withContext UsbAdbConnector.connect(context, device.usbDevice, mode)
        } else if (device.type == "WIFI") {
            val parts = device.serial.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 5555
            return@withContext connect(context, host, port)
        }
        false
    }

    suspend fun connect(context: Context, host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            lastErrorMessage = ""
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS.toInt())
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS.toInt()
            
            val channel = TcpChannel(socket)
            val keyManager = AdbKeyManager(context)
            crypto = keyManager.getOrCreateCrypto() ?: run {
                lastErrorMessage = "Nao foi possivel carregar ou gerar a chave ADB"
                return@withContext false
            }
            
            val adbConn = AdbConnection.create(channel, crypto)
            withTimeout(HANDSHAKE_TIMEOUT_MS + 2000L) {
                adbConn.connect(HANDSHAKE_TIMEOUT_MS)
            }

            setWifiConnection(adbConn)
            Log.d(TAG, "Conectado via Wi-Fi a $host:$port")
            
            executeCommand("getprop ro.product.model", target = adbConn)
            true
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Falha desconhecida na conexao ADB"
            Log.e(TAG, "Erro ao conectar via Wi-Fi: ${e.message}", e)
            false
        }
    }

    suspend fun executeCommand(
        command: String, 
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
        target: AdbConnection? = activeConnection
    ): String = withContext(Dispatchers.IO) {
        val conn = target ?: return@withContext "Erro: Não conectado"
        try {
            val remoteCommand = if (command.startsWith("host:")) command else buildShellCommand(command)
            val output = StringBuilder()
            val completed = withTimeoutOrNull(timeoutMs) {
                val stream: AdbStream = conn.open(remoteCommand)
                try {
                    while (true) {
                        val data = try { stream.read() } catch (e: Exception) { null } ?: break
                        output.append(String(data))
                    }
                } finally {
                    runCatching { stream.close() }
                }
                true
            }
            if (completed != true) {
                lastErrorMessage = "Tempo limite ao executar comando ADB"
                return@withContext "Erro: tempo limite ao executar comando"
            }
            output.toString().trim().ifEmpty { "Comando enviado." }
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Falha ao executar comando ADB"
            Log.e(TAG, "Erro no comando: $command", e)
            "Erro: ${e.message}"
        }
    }

    suspend fun exec(command: String, target: AdbConnection? = activeConnection): String = executeCommand(command, target = target)

    suspend fun openLongLivedShell(
        command: String,
        target: AdbConnection? = activeConnection
    ): AdbStream? = withContext(Dispatchers.IO) {
        val conn = target ?: return@withContext null
        try {
            conn.open(buildShellCommand(command))
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Falha ao abrir shell persistente"
            Log.e(TAG, "Erro ao abrir shell persistente: $command", e)
            null
        }
    }

    suspend fun execSilent(command: String, target: AdbConnection? = activeConnection) {
        withContext(Dispatchers.IO) {
            try {
                target?.open("shell:$command")?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Erro no execSilent: $command", e)
            }
        }
    }

    fun execAsync(command: String, target: AdbConnection? = activeConnection) {
        CoroutineScope(Dispatchers.IO).launch {
            execSilent(command, target)
        }
    }

    /**
     * Envia um arquivo para o dispositivo remoto de forma robusta.
     * Usa 'exec:' para evitar corrupção de binários do shell.
     */
    suspend fun pushFile(
        content: ByteArray, 
        remotePath: String,
        target: AdbConnection? = activeConnection
    ): Boolean = withContext(Dispatchers.IO) {
        val conn = target ?: return@withContext false
        try {
            Log.d(TAG, "Iniciando push de ${content.size} bytes para $remotePath")
            val stream: AdbStream = conn.open("exec:cat > \"$remotePath\"")
            stream.write(content)
            stream.close()
            Log.d(TAG, "Push concluído com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer push: ${e.message}")
            false
        }
    }

    fun disconnect() {
        disconnectWifi()
        disconnectUsb()
    }

    fun disconnect(device: AdbDevice) {
        runCatching { device.connection?.close() }
        if (device.connection === wifiConnection) wifiConnection = null
        if (device.connection === usbConnection) usbConnection = null
    }

    fun disconnectWifi() {
        runCatching { wifiConnection?.close() }
        wifiConnection = null
    }

    fun disconnectUsb() {
        runCatching { usbConnection?.close() }
        usbConnection = null
    }

    fun setWifiConnection(newConnection: AdbConnection) {
        val old = wifiConnection
        if (old === newConnection) return
        runCatching { old?.close() }
        wifiConnection = newConnection
    }

    suspend fun getDeviceModel(target: AdbConnection? = activeConnection): String {
        return executeCommand("getprop ro.product.model", target = target).trim()
    }

    suspend fun execute(command: String, device: AdbDevice?): String {
        val conn = device?.connection ?: activeConnection
        return executeCommand(command, target = conn)
    }

    suspend fun listDevices(context: Context): List<AdbDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<AdbDevice>()
        
        // 1. Wi-Fi
        wifiConnection?.let {
            val model = runCatching { getDeviceModel(it) }.getOrNull() ?: "Dispositivo Wi-Fi"
            devices.add(AdbDevice(serial = "Wi-Fi", status = DeviceStatus.ONLINE, model = model, type = "WIFI", connection = it))
        }

        // 2. USB
        val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        usbManager.deviceList.values.forEach { usbDevice ->
            val isAdb = (0 until usbDevice.interfaceCount).any { i ->
                val itf = usbDevice.getInterface(i)
                itf.interfaceClass == 255 && itf.interfaceSubclass == 66 && itf.interfaceProtocol == 1
            }
            
            if (isAdb) {
                val model = usbDevice.productName ?: usbDevice.deviceName
                val isThisConnected = usbConnection != null // Simplificação: assume que se há conexão USB, é este
                devices.add(AdbDevice(
                    serial = usbDevice.deviceName, 
                    status = if (isThisConnected) DeviceStatus.ONLINE else DeviceStatus.DISCONNECTED, 
                    model = model, 
                    type = "USB", 
                    connection = if (isThisConnected) usbConnection else null,
                    usbDevice = usbDevice
                ))
            }
        }
        devices
    }

    fun setUsbConnection(newConnection: AdbConnection) {
        val old = usbConnection
        if (old === newConnection) return
        runCatching { old?.close() }
        usbConnection = newConnection
    }

    fun replaceConnection(newConnection: AdbConnection) {
        // Método legado, agora usamos setUsbConnection ou setWifiConnection explicitamente
        setUsbConnection(newConnection)
    }

    suspend fun openLogcatStream(target: AdbConnection? = activeConnection) = kotlinx.coroutines.flow.flow {
        val stream = openLongLivedShell("logcat -v long", target) ?: return@flow
        try {
            val buffer = StringBuilder()
            while (true) {
                val data = try { stream.read() } catch (e: Exception) { null } ?: break
                buffer.append(String(data))
                
                var newlineIndex = buffer.indexOf("\n")
                while (newlineIndex != -1) {
                    val line = buffer.substring(0, newlineIndex)
                    emit(line)
                    buffer.delete(0, newlineIndex + 1)
                    newlineIndex = buffer.indexOf("\n")
                }
            }
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun buildShellCommand(command: String): String {
        if (command.startsWith("shell:") || command.startsWith("exec:") || command.startsWith("tcpip:")) {
            return command
        }
        val sanitized = command.replace('\n', ' ').trim()
        return "shell:export PATH=$REMOTE_PATH:\$PATH; $sanitized"
    }
}
