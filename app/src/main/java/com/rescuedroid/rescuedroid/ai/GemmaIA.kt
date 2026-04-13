package com.rescuedroid.rescuedroid.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GemmaIA(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val modelPath = File(context.filesDir, "gemma.bin").absolutePath
    private val driveId = "1G4_Ykv9CMkl7J-6YcBnhAC1pdqCdnOeO"

    init {
        // Tenta preparar o modelo em uma thread separada
        Thread {
            prepareModel()
        }.start()
    }

    private fun prepareModel() {
        val file = File(modelPath)
        if (!file.exists()) {
            downloadModel(file)
        }

        if (file.exists() && llmInference == null) {
            try {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun downloadModel(targetFile: File) {
        try {
            // URL de download direto do Google Drive
            val downloadUrl = "https://drive.google.com/uc?export=download&id=$driveId&confirm=t"
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                url.openStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun responder(pergunta: String): String = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            prepareModel()
        }

        val prompt = """
            Você é a IA PicoClaw do RescueDroid. Seu objetivo é ajudar no resgate de dispositivos Android via ADB.
            Responda sempre em português brasileiro de forma técnica e amigável.
            Se o usuário pedir um comando, responda com o comando ADB correto.
            Para comandos específicos, tente incluir uma tag de comando como [CMD:ACAO].
            
            Usuário: $pergunta
            PicoClaw:
        """.trimIndent()

        return@withContext llmInference?.generateResponse(prompt) ?: "IA Offline (O modelo de 270MB está sendo baixado ou preparado...)"
    }
    
    fun close() {
        llmInference?.close()
    }
}
