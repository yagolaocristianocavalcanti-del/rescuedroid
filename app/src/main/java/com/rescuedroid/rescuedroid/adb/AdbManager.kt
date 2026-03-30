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
import java.net.InetSocketAddress
import java.net.Socket

/**
 * AdbManager utilizando a biblioteca local com.cgutman.adblib
 */
object AdbManager {
    private const val TAG = "AdbManager"
    private const val REMOTE_PATH = "/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin"
    private const val CONNECT_TIMEOUT_MS = 2500L
    private const val HANDSHAKE_TIMEOUT_MS = 4000L
    
    var connection: AdbConnection? = null
    private var crypto: AdbCrypto? = null
    var lastErrorMessage: String = ""
        private set

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
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                adbConn.connect()
            }
            
            connection = adbConn
            Log.d(TAG, "Conectado via Wi-Fi a $host:$port")
            
            executeCommand("getprop ro.product.model")
            true
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Falha desconhecida na conexao ADB"
            Log.e(TAG, "Erro ao conectar via Wi-Fi: ${e.message}", e)
            false
        }
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext "Erro: Não conectado"
        try {
            val escapedCommand = command.replace("'", "'\\''")
            val remoteCommand = "shell:sh -c 'export PATH=$REMOTE_PATH:\$PATH; $escapedCommand 2>&1'"
            val stream: AdbStream = conn.open(remoteCommand)
            val output = StringBuilder()
            try {
                while (true) {
                    val data = stream.read() ?: break
                    output.append(String(data))
                }
            } catch (e: Exception) {
                // Fim da leitura
            } finally {
                stream.close()
            }
            output.toString().trim().ifEmpty { "Comando enviado." }
        } catch (e: Exception) {
            lastErrorMessage = e.message ?: "Falha ao executar comando ADB"
            Log.e(TAG, "Erro no comando: $command", e)
            "Erro: ${e.message}"
        }
    }

    suspend fun exec(command: String): String = executeCommand(command)

    suspend fun execSilent(command: String) {
        withContext(Dispatchers.IO) {
            try {
                connection?.open("shell:$command")?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Erro no execSilent: $command", e)
            }
        }
    }

    fun execAsync(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            execSilent(command)
        }
    }

    /**
     * Envia um arquivo para o dispositivo remoto de forma robusta.
     * Usa 'exec:' para evitar corrupção de binários do shell.
     */
    suspend fun pushFile(content: ByteArray, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext false
        try {
            Log.d(TAG, "Iniciando push de ${content.size} bytes para $remotePath")
            // Usamos 'exec:cat > path' que é mais seguro para binários que 'shell:cat'
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
        try {
            connection?.close()
            connection = null
            lastErrorMessage = ""
            Log.d(TAG, "Conexão ADB fechada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar conexão", e)
        }
    }
}
