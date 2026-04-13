package com.rescuedroid.rescuedroid.model

import android.graphics.drawable.Drawable
import com.rescuedroid.rescuedroid.RiskLevel

enum class Action {
    KEEP, DISABLE, UNINSTALL
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val risk: RiskLevel,
    val reason: String,
    val suggestedAction: Action
)

data class FastbootVar(val name: String, val value: String)

data class ArquivoAdb(
    val nome: String, 
    val tamanho: String, 
    val permissao: String, 
    val eDiretorio: Boolean
)

data class ScriptLocal(
    val nome: String, 
    val conteudo: String
)

enum class LogLevel(val code: String, val label: String, val color: Long) {
    VERBOSE("V", "VERBOSE", 0xFF9E9E9E),
    DEBUG("D", "DEBUG", 0xFF00BCD4),
    INFO("I", "INFO", 0xFF4CAF50),
    WARN("W", "AVISO", 0xFFFFEB3B),
    ERROR("E", "ERRO", 0xFFF44336),
    FATAL("F", "FATAL", 0xFFB71C1C)
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val time: String,
    val pid: String,
    val original: String
)
