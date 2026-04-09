package com.rescuedroid.rescuedroid.tools

import android.content.Context
import android.util.Log
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbStream
import com.rescuedroid.rescuedroid.adb.AdbManager
import com.rescuedroid.rescuedroid.adb.AdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrcpyTool @Inject constructor(
    private val adbRepository: AdbRepository
) {
    private val TAG = "SCRCPY"
    private val REMOTE_JAR = "/data/local/tmp/scrcpy-server.jar"

    enum class Quality(val bitRate: Long, val maxSize: Int, val fps: Int, val label: String) {
        ULTRA(20_000_000, 1920, 60, "FULL HD+"),
        HIGH(8_000_000, 1280, 60, "HD 1080p"),
        STANDARD(4_000_000, 1024, 30, "Padrão"),
        LIGHT(2_000_000, 720, 30, "Leve 720p"),
        ULTRA_LIGHT(1_000_000, 480, 15, "Econômico")
    }

    @Volatile
    private var serverStream: AdbStream? = null
    
    @Volatile
    var activeConnection: AdbConnection? = null
        private set

    var currentQuality: Quality = Quality.STANDARD

    suspend fun startServer(
        context: Context, 
        quality: Quality = currentQuality, 
        codec: String = "h265",
        target: AdbConnection? = AdbManager.activeConnection
    ): Boolean = withContext(Dispatchers.IO) {
        var retryCount = 0
        val maxRetries = 10
        
        while (retryCount < maxRetries) {
            try {
                // Limpa instâncias anteriores de forma mais agressiva
                stopServer()
                if (target == null) {
                    Log.e(TAG, "Nenhuma conexão ADB ativa para iniciar o espelhamento")
                    return@withContext false
                }
                activeConnection = target

                AdbManager.executeCommand("pkill -f scrcpy-server", target = target)
                delay(200)

                currentQuality = quality

                val jarBytes = context.assets.open("scrcpy-server.jar").use { it.readBytes() }
                val pushed = AdbManager.pushFile(jarBytes, REMOTE_JAR, target = target)
                if (!pushed) {
                    Log.e(TAG, "Falha ao enviar scrcpy-server.jar (Tentativa ${retryCount + 1}/$maxRetries)")
                    retryCount++
                    delay(500)
                    continue
                }

                val cmd = buildServerCommand(quality, codec)
                val stream = AdbManager.openLongLivedShell(cmd, target = target)
                if (stream == null) {
                    Log.e(TAG, "Falha ao abrir shell para scrcpy (Tentativa ${retryCount + 1}/$maxRetries)")
                    retryCount++
                    delay(500)
                    continue
                }

                serverStream = stream
                delay(1000)
                Log.d(TAG, "scrcpy-server iniciado com qualidade ${quality.name} e codec $codec")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar servidor scrcpy (Tentativa ${retryCount + 1}/$maxRetries): ${e.message}")
                retryCount++
                delay(1000)
            }
        }
        
        Log.e(TAG, "Falha crítica: Excedido limite de 10 tentativas para iniciar o servidor scrcpy")
        false
    }

    fun stopServer() {
        val current = serverStream
        serverStream = null
        runCatching { current?.close() }
    }
    
    fun clearActiveConnection() {
        activeConnection = null
    }

    private fun buildServerCommand(quality: Quality, codec: String): String {
        return buildString {
            append("CLASSPATH=$REMOTE_JAR ")
            append("app_process / com.genymobile.scrcpy.Server 2.1 ")
            append("log_level=info ")
            append("max_size=${quality.maxSize} ")
            append("video_bit_rate=${quality.bitRate} ")
            append("max_fps=${quality.fps} ")
            append("video_codec=$codec ") 
            append("i_frame_interval=10 ")
            append("tunnel_forward=true ")
            append("control=true ")
            append("cleanup=true ")
            append("stay_awake=true ")
            append("audio=false ")
            append("send_dummy_byte=true ")
            append("send_device_meta=true ")
            append("send_codec_meta=false ")
            append("send_frame_meta=false ")
        }
    }
    
    suspend fun restartWithCodec(context: Context, codec: String): Boolean {
        return startServer(
            context, 
            currentQuality, 
            codec,
            target = activeConnection
        )
    }

    suspend fun restartWithLegacyCodec(context: Context): Boolean {
        return startServer(
            context,
            Quality.LIGHT,
            "h264",
            target = activeConnection
        )
    }
}
