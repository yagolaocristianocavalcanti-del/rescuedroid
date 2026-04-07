package com.rescuedroid.rescuedroid.debloat

import com.rescuedroid.rescuedroid.adb.AdbShell
import com.rescuedroid.rescuedroid.RiskLevel
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de remoção automática em lote (Auto-Debloat).
 */
@Singleton
class AutoDebloat @Inject constructor(
    private val adbShell: AdbShell,
    private val riskEngine: DebloatRiskEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run(serial: String, packages: List<String>, onProgress: (String) -> Unit) {
        scope.launch {
            if (packages.isEmpty()) {
                onProgress("⚠️ Nenhum app para remover")
                return@launch
            }
            onProgress("🧹 Iniciando limpeza automática...")
            var count = 0
            packages.forEach { pkg ->
                val (risk, _) = riskEngine.check(pkg)
                
                // Só remove automaticamente o que é considerado SEGURO (Risco Baixo)
                if (risk == RiskLevel.SEGURO) {
                    onProgress("🚀 Removendo: $pkg")
                    adbShell.runForDevice(serial, "pm uninstall --user 0 $pkg")
                    count++
                } else {
                    onProgress("⏸️ Ignorado: $pkg (Risco)")
                }
            }
            onProgress("✅ Limpeza concluída! $count apps removidos.")
        }
    }
}
