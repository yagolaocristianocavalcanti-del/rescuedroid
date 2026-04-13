package com.rescuedroid.rescuedroid.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GemmaIA(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val modelName = "gemma3-270m-it-q8.bin"
    private val modelPath = File(context.filesDir, modelName).absolutePath
    private val driveId = "1G4_Ykv9CMkl7J-6YcBnhAC1pdqCdnOeO"

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    init {
        // Tenta preparar o modelo em uma thread separada
        Thread {
            prepareModel()
        }.start()
    }

    private fun prepareModel() {
        val file = File(modelPath)
        
        // 1. Verifica primeiro se o arquivo já existe no destino final (área privada do app)
        if (file.exists()) {
            loadLlmInference()
            return
        }

        // 2. Tenta procurar na pasta Downloads pública do Android
        // Adicionamos vários caminhos comuns para garantir que encontramos o arquivo
        val possibleDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download")
        )
        
        val possibleNames = listOf("gemma3-270m-it-q8.bin", "gemma.bin")
        
        for (dir in possibleDirs) {
            for (name in possibleNames) {
                val publicGemmaFile = File(dir, name)
                if (publicGemmaFile.exists() && publicGemmaFile.canRead()) {
                    try {
                        // Se achou no download, copia para a pasta privada do app
                        publicGemmaFile.inputStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        loadLlmInference()
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 3. Se não achou em lugar nenhum, baixa do Drive
        downloadModel(file)
        if (file.exists()) {
            loadLlmInference()
        }
    }

    private fun loadLlmInference() {
        if (llmInference == null) {
            try {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                _isReady.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun downloadModel(targetFile: File) {
        try {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            
            val downloadUrl = "https://drive.google.com/uc?export=download&id=$driveId&confirm=t"
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val fileLength = connection.contentLength.toLong()
                url.openStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (fileLength > 0) {
                                _downloadProgress.value = totalBytesRead.toFloat() / fileLength.toFloat()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isDownloading.value = false
            _downloadProgress.value = 1f
        }
    }

    suspend fun responder(pergunta: String): String = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            prepareModel()
        }

        val prompt = """
            Você é a IA do RescueDroid. Seu objetivo é ajudar no resgate de dispositivos Android via ADB.
            Responda sempre em português brasileiro de forma técnica e amigável.
            Se o usuário pedir um comando, responda com o comando ADB correto.
            Para comandos específicos, tente incluir uma tag de comando como [CMD:ACAO].
            
            Usuário: $pergunta
            IA:
        """.trimIndent()

        return@withContext llmInference?.generateResponse(prompt) ?: "IA Offline (O modelo de 270MB está sendo baixado ou preparado...)"
    }
    
    fun close() {
        llmInference?.close()
    }
}
