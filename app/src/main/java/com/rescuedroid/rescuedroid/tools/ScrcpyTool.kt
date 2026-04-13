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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrcpyTool @Inject constructor(
    private val adbRepository: AdbRepository
) {
    private val TAG = "SCRCPY"
    private val REMOTE_JAR = "/data/local/tmp/scrcpy-server.jar"

    enum class Quality(
        val maxSize: String,
        val bitRate: String,
        val fps: String,
        val label: String
    ) {
        ULTRA_LIGHT("480", "1M", "15", "Econômico"),
        LIGHT("720", "2M", "30", "Leve 720p"),
        STANDARD("1024", "4M", "30", "Padrão"),
        HD("1080", "8M", "60", "HD 1080p"),
        FULL_HD("1920", "20M", "60", "FULL HD+")
    }

    private var process: Process? = null
    
    @Volatile
    private var serverStream: AdbStream? = null
    
    @Volatile
    var activeConnection: AdbConnection? = null
        private set

    var currentQuality: Quality = Quality.STANDARD

    suspend fun startMirror(
        deviceSerial: String,
        quality: Quality = Quality.HD,
        context: Context,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            // Mata processos antigos
            stopMirror()
            
            val cmd = mutableListOf(
                "scrcpy",
                "--serial=$deviceSerial",
                "--max-size=${quality.maxSize}",
                "--video-bit-rate=${quality.bitRate}",
                "--max-fps=${quality.fps}",
                "--stay-awake",
                "--turn-screen-off",
                "--no-audio",
                "--keyboard=uhid"
            )
            
            // Nota: Isso assume que o binário 'scrcpy' está no PATH do sistema onde o app roda (se for Desktop/Emulator com suporte)
            // No Android real, o scrcpy geralmente roda via server.jar injetado.
            // Vou manter a lógica de injeção de JAR que já existia, mas adaptando os parâmetros.
            
            startServer(context, quality)
            onSuccess()
        } catch (e: Exception) {
            onError("Erro: ${e.message}")
        }
    }

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

    fun stopMirror() {
        stopServer()
        process?.destroyForcibly()
        process = null
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
