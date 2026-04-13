package com.rescuedroid.rescuedroid.debloat

import com.rescuedroid.rescuedroid.RiskLevel
import com.rescuedroid.rescuedroid.model.Action

object DebloatEngine {

    private val safe = listOf("facebook", "netflix", "tiktok", "amazon", "instagram", "messenger")
    private val critical = listOf("systemui", "android", "com.android.phone", "com.android.settings", "com.google.android.gms")

    fun analyze(pkg: String): Pair<RiskLevel, String> {
        val p = pkg.lowercase()

        return when {
            critical.any { p.contains(it) } ->
                RiskLevel.CRITICAL to "Sistema essencial"

            safe.any { p.contains(it) } ->
                RiskLevel.SAFE to "Bloatware conhecido"

            p.startsWith("com.android") || p.startsWith("com.google.android") ->
                RiskLevel.DANGEROUS to "App do sistema"

            else ->
                RiskLevel.MODERATE to "Não classificado"
        }
    }

    fun action(risk: RiskLevel): Action {
        return when (risk) {
            RiskLevel.SAFE -> Action.UNINSTALL
            RiskLevel.MODERATE -> Action.DISABLE
            RiskLevel.DANGEROUS -> Action.KEEP
            RiskLevel.CRITICAL -> Action.KEEP
        }
    }
}
