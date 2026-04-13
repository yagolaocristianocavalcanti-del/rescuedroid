package com.rescuedroid.rescuedroid.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GemmaIA(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val modelName = "gemma_active.bin"
    private val modelPath = File(context.filesDir, modelName).absolutePath
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val aiCache = mutableMapOf<String, String>()

    @Volatile
    private var isPreparing = false

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _statusMessage = MutableStateFlow("Aguardando modelo...")
    val statusMessage = _statusMessage.asStateFlow()

    init {
        scope.launch {
            safePrepare()
        }
    }

    fun checkModelManual() {
        scope.launch {
            safePrepare()
        }
    }

    fun importModelFromUri(uri: Uri) {
        scope.launch {
            try {
                _statusMessage.value = "Importando modelo..."
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(modelPath).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!loadLlmInference()) {
                    File(modelPath).delete()
                }
            } catch (e: Exception) {
                _statusMessage.value = "Erro na importação."
                e.printStackTrace()
            }
        }
    }

    private suspend fun safePrepare() {
        if (isPreparing || _isReady.value) return
        isPreparing = true
        prepareModel()
        isPreparing = false
    }

    private suspend fun prepareModel() = withContext(Dispatchers.IO) {
        val file = File(modelPath)
        
        // Se já tem um modelo carregado que funciona, não faz nada
        if (_isReady.value) return@withContext

        _statusMessage.value = "Buscando modelos compatíveis..."

        val possibleDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download")
        )
        
        val possibleNames = listOf(
            "gemma3-270m-it-q8.bin",
            "gemma-3-270m-it-int8.task",
            "gemma2-2b-it-int8-web.task.bin",
            "gemma-2b-it-gpu-int4.bin",
            "gemma.bin",
            "gemma2-270m-it-int8.bin"
        )

        // Coleta todos os arquivos encontrados
        val foundFiles = mutableListOf<File>()
        for (dir in possibleDirs) {
            for (name in possibleNames) {
                val f = File(dir, name)
                if (f.exists() && f.canRead() && f.length() > 50_000_000) {
                    foundFiles.add(f)
                }
            }
        }

        // Ordena por tamanho (menor para o maior)
        val sortedFiles = foundFiles.sortedBy { it.length() }

        if (sortedFiles.isEmpty()) {
            _statusMessage.value = "IA Offline: Nenhum modelo encontrado."
            return@withContext
        }

        // Testa um por um
        for (candidate in sortedFiles) {
            _statusMessage.value = "Testando ${candidate.name}..."
            try {
                candidate.inputStream().use { input ->
                    File(modelPath).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (loadLlmInference()) {
                    _statusMessage.value = "IA Online (${candidate.name})"
                    return@withContext
                } else {
                    File(modelPath).delete()
                }
            } catch (e: Exception) {
                File(modelPath).delete()
            }
        }

        _statusMessage.value = "IA Offline: Modelos encontrados são inválidos."
    }

    private fun loadLlmInference(): Boolean {
        try {
            llmInference?.close()
        } catch (e: Exception) {}
        llmInference = null
        _isReady.value = false
        
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            _isReady.value = true
            _statusMessage.value = "IA Online"
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun responder(pergunta: String): String = withContext(Dispatchers.IO) {
        if (aiCache.containsKey(pergunta)) return@withContext aiCache[pergunta]!!
        
        if (llmInference == null) {
            return@withContext "IA Offline: Motor não carregado."
        }

        val prompt = "Você é um especialista em Android. Responda em português brasileiro: $pergunta"
        val response = try {
            llmInference?.generateResponse(prompt) ?: "Sem resposta."
        } catch (e: Exception) {
            "Erro na geração."
        }
        
        aiCache[pergunta] = response
        return@withContext response
    }

    suspend fun analisarApp(pkg: String): String = withContext(Dispatchers.IO) {
        val cacheKey = "analisar_$pkg"
        if (aiCache.containsKey(cacheKey)) return@withContext aiCache[cacheKey]!!
        val response = responder("O pacote '$pkg' é seguro para debloat? Classifique o risco.")
        aiCache[cacheKey] = response
        return@withContext response
    }
    
    fun close() {
        llmInference?.close()
    }
}
