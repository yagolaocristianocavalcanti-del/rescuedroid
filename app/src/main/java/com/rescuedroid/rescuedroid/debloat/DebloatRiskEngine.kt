package com.rescuedroid.rescuedroid.debloat

import com.rescuedroid.rescuedroid.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebloatRiskEngine @Inject constructor() {

    data class Analysis(
        val risk: RiskLevel,
        val reason: String,
        val score: Int,
        val suggestedAction: String
    )

    private val APPS_CRITICOS = listOf(
        "com.android.systemui", "com.android.launcher3", "android", "com.google.android.gms",
        "com.google.android.packageinstaller", "com.android.settings"
    )
    
    private val BLOATWARE_CONHECIDO = listOf(
        "com.facebook.appmanager", "com.facebook.services", "com.facebook.system",
        "com.facebook.katana", "com.tiktok.musically", "com.zhiliaoapp.musically",
        "com.oppo.market", "com.samsung.android.bixby.agent", "com.netflix.mediaclient",
        "com.android.vending.billing", "com.google.android.videos", "com.google.android.music",
        "br.com.claro.minhaclaro", "br.com.vivo.meuvivo", "br.com.timbrasil.meutim",
        "com.oi.minhaoi", "com.portoseguro.conecta", "com.nextel.meunextel",
        "com.bb.android", "br.com.itau.personnalite", "com.nu.production", "com.santander.app",
        "br.com.bradesco.classic", "com.itau.itaucartoes", "com.caixa.mobile",
        "com.magazinelea.magalu", "com.casasbahia.app", "com.americanas.app",
        "com.mercadolivre", "com.shopee.br", "com.alibaba.aliexpressbrasil"
    )

    fun check(pkg: String): Analysis {
        val p = pkg.lowercase()
        return when {
            APPS_CRITICOS.any { p.contains(it) } -> 
                Analysis(RiskLevel.CRITICO, "🚨 APP ESSENCIAL DO SISTEMA", 0, "NÃO TOCAR!")
            
            BLOATWARE_CONHECIDO.any { p.contains(it) } -> 
                Analysis(RiskLevel.SEGURO, "🗑️ BLOATWARE CONHECIDO", 95, "REMOVER!")
            
            p.contains("service") || p.contains("framework") -> 
                Analysis(RiskLevel.PERIGOSO, "⚠️ SERVIÇO INTERNO", 30, "DESATIVAR")
            
            p.contains("google") && !p.contains("vending") && !p.contains("play.games") -> 
                Analysis(RiskLevel.SEGURO, "✅ GOOGLE OPCIONAL", 60, "PODE DESATIVAR")

            p.contains("carrier") || p.contains("telephony") ->
                Analysis(RiskLevel.PERIGOSO, "📡 SERVIÇO DE OPERADORA", 40, "CUIDADO")
            
            else -> Analysis(RiskLevel.PERIGOSO, "❓ DESCONHECIDO", 50, "ANALISAR")
        }
    }
}
