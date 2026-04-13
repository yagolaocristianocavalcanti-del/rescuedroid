package com.rescuedroid.rescuedroid.debloat

import com.rescuedroid.rescuedroid.RiskLevel
import com.rescuedroid.rescuedroid.model.Action

object DebloatAnalyzer {

    private val safeList = listOf(
        "facebook", "netflix", "amazon", "tiktok", "instagram", "messenger", 
        "booking", "linkedin", "microsoft.office", "skype", "spotify"
    )

    private val criticalList = listOf(
        "systemui", "android.system", "com.android.phone", "com.android.settings",
        "com.google.android.gsf", "com.google.android.gms", "com.android.vending",
        "com.android.packageinstaller", "com.android.launcher"
    )

    private val warningList = listOf(
        "camera", "gallery", "calendar", "contacts", "deskclock", "calculator",
        "keyboard", "inputmethod"
    )

    fun analyze(pkg: String, isSystem: Boolean): Pair<RiskLevel, String> {
        val lower = pkg.lowercase()

        return when {
            criticalList.any { lower.contains(it) } ->
                RiskLevel.CRITICAL to "Componente essencial do sistema (Protegido)"

            safeList.any { lower.contains(it) } ->
                RiskLevel.SAFE to "Bloatware conhecido / App de terceiro"

            warningList.any { lower.contains(it) } ->
                RiskLevel.DANGEROUS to "App de utilidade básica (Pode afetar experiência)"

            isSystem ->
                RiskLevel.MODERATE to "App de sistema genérico"

            else ->
                RiskLevel.SAFE to "App de usuário instalado manualmente"
        }
    }

    fun suggestAction(risk: RiskLevel, isSystem: Boolean): Action {
        return when (risk) {
            RiskLevel.SAFE -> if (isSystem) Action.DISABLE else Action.UNINSTALL
            RiskLevel.MODERATE -> Action.KEEP
            RiskLevel.DANGEROUS -> Action.KEEP
            RiskLevel.CRITICAL -> Action.KEEP
        }
    }
}
