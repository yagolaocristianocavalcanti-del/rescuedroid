package com.rescuedroid.rescuedroid.ai

object IAEscuta {
    fun parseComando(texto: String): IACmd? {
        val cmd = texto.lowercase().trim()
        
        return when {
            // CONEXÕES
            cmd.contains("conecta usb") || cmd.contains("usb turbo") -> IACmd.UsbTurbo
            cmd.contains("wifi") || cmd.contains("conecta rede") || cmd.contains("conectar wifi") -> IACmd.ConnectWifi
            cmd.contains("ip") -> IACmd.SetIp(cmd.extractIp())
            
            // DEBLOAT
            cmd.contains("remove") || cmd.contains("tira lixo") || cmd.contains("debloat") -> {
                val pkg = if (cmd.contains("facebook")) "facebook" else ""
                if (pkg.isNotEmpty()) IACmd.Debloat(pkg) else IACmd.DebloatAllSafe
            }
            cmd.contains("limpa tudo") -> IACmd.DebloatAllSafe
            
            // PERMISSÕES
            cmd.contains("permissão") || cmd.contains("storage") || cmd.contains("armazenamento") -> IACmd.RequestStorage
            
            // FAST ACTIONS
            cmd.contains("screenshot") || cmd.contains("print") -> IACmd.Screenshot
            cmd.contains("desbloqueia") || cmd.contains("desbloquear") -> IACmd.Unlock
            cmd.contains("tela ligada") || cmd.contains("sempre on") -> IACmd.ScreenTimeout
            
            // HACKER
            cmd.contains("hacker") || cmd.contains("modo hacker") -> IACmd.ToggleHacker
            
            // ADB TOOLS
            cmd.contains("reset adb") || cmd.contains("limpar chaves") || cmd.contains("resetar chaves") -> IACmd.ResetAdbKeys
            
            else -> null
        }
    }
    
    private fun String.extractIp(): String {
        val ipRegex = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
        return ipRegex.find(this)?.value ?: ""
    }
}

sealed class IACmd {
    object UsbTurbo : IACmd()
    object ConnectWifi : IACmd()
    data class SetIp(val ip: String) : IACmd()
    data class Debloat(val pkg: String) : IACmd()
    object DebloatAllSafe : IACmd()
    object RequestStorage : IACmd()
    object Screenshot : IACmd()
    object Unlock : IACmd()
    object ScreenTimeout : IACmd()
    object ToggleHacker : IACmd()
    object ResetAdbKeys : IACmd()
}
