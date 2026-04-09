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
        // Redes Sociais e Trackers
        "com.facebook.appmanager", "com.facebook.services", "com.facebook.system",
        "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
        "com.tiktok.musically", "com.zhiliaoapp.musically", "com.snapchat.android",
        
        // Samsung Bloatware
        "com.samsung.android.bixby.agent", "com.samsung.android.bixby.wakeup",
        "com.samsung.android.app.spay", "com.samsung.android.spay",
        "com.samsung.android.aremoji", "com.samsung.android.arzone",
        "com.samsung.android.authfw", "com.samsung.android.game.gamehome",
        "com.samsung.android.wellbeing", "com.samsung.android.da.daagent",
        "com.samsung.android.service.livedrawing", "com.sec.android.app.sbrowser",
        
        // Xiaomi / MIUI Bloatware
        "com.miui.analytics", "com.miui.msa.global", "com.miui.cloudservice",
        "com.miui.videoplayer", "com.miui.player", "com.xiaomi.midrop",
        "com.xiaomi.mipicks", "com.xiaomi.glgm", "com.xiaomi.payment",
        
        // Google (Opcionais/Substituíveis)
        "com.google.android.videos", "com.google.android.music",
        "com.google.android.apps.tachyon", "com.google.android.apps.wellbeing",
        "com.google.android.apps.photosgo", "com.google.android.apps.mapslite",
        "com.google.android.youtube.tv", "com.google.android.apps.magazines",
        
        // Microsoft (Parcerias)
        "com.microsoft.skydrive", "com.microsoft.office.officehub",
        "com.microsoft.office.outlook", "com.microsoft.office.word",
        "com.linkedin.android", "com.skype.raider",
        
        // Streaming e Apps Pré-instalados
        "com.netflix.mediaclient", "com.netflix.partner.activation",
        "com.spotify.music", "com.amazon.mShop.android.shopping",
        "com.amazon.mp3", "com.amazon.kindle", "com.deezer.android",
        
        // Operadoras Brasileiras
        "br.com.claro.minhaclaro", "br.com.vivo.meuvivo", "br.com.timbrasil.meutim",
        "com.oi.minhaoi", "com.portoseguro.conecta", "com.nextel.meunextel",
        "br.com.claro.flex", "br.com.oi.reminhaoi",
        
        // Bancos e E-commerce (Seguro remover se não usa)
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
