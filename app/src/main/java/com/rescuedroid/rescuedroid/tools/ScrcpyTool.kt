package com.rescuedroid.rescuedroid.tools

import android.content.Context
import com.rescuedroid.rescuedroid.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

object ScrcpyTool {
    suspend fun startServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Pega o jar dos assets
            val jarBytes = context.assets.open("scrcpy-server.jar").use { it.readBytes() }
            
            // 2. Envia para o celular remoto
            val pushed = AdbManager.pushFile(jarBytes, "/data/local/tmp/scrcpy-server.jar")
            if (!pushed) {
                Log.e("SCRCPY", "Falha ao enviar scrcpy-server.jar")
                return@withContext false
            }
            
            // 3. Comando para iniciar o servidor do scrcpy
            // Nota: Versão 2.0+ do scrcpy
            val cmd = "CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 2.0 tunnel_forward=true control=true cleanup=true"
            
            AdbManager.execAsync(cmd)
            true
        } catch (e: Exception) {
            Log.e("SCRCPY", "Erro ao iniciar: ${e.message}")
            false
        }
    }
}
