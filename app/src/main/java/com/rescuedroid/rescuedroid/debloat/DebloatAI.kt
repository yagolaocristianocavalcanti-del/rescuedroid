package com.rescuedroid.rescuedroid.debloat

import com.rescuedroid.rescuedroid.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor inteligente de sugestão para remoção de bloatwares.
 */
@Singleton
class DebloatAI @Inject constructor(
    private val riskEngine: DebloatRiskEngine
) {

    data class Suggestion(
        val pkg: String,
        val risk: RiskLevel,
        val reason: String,
        val shouldRemove: Boolean
    )

    fun analyze(packages: List<String>): List<Suggestion> {
        return packages.map { pkg ->
            val (risk, reason) = riskEngine.check(pkg)
            
            // Sugestão lógica baseada no risco e em palavras-chave
            val shouldRemove = risk == RiskLevel.SEGURO && (
                pkg.contains("facebook") || 
                pkg.contains("skype") || 
                pkg.contains("analytics") || 
                pkg.contains("weather") || 
                pkg.contains("amazon")
            )

            Suggestion(pkg, risk, reason, shouldRemove)
        }
    }
}
