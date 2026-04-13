package com.rescuedroid.rescuedroid

import com.rescuedroid.rescuedroid.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class StressPerformanceTest {

    @Test
    fun testLogcatParallelWithMirrorSimulated() = runBlocking {
        println("🚀 Iniciando Teste de Estresse: Logcat + Comandos Paralelos")
        
        val logcatJob = launch(Dispatchers.IO) {
            var count = 0
            AdbManager.openLogcatStream().collect { line ->
                count++
                if (count % 100 == 0) {
                    println("📜 Logcat: $count linhas processadas...")
                }
                if (count >= 1000) return@collect
            }
        }

        val commandsJob = launch(Dispatchers.IO) {
            repeat(10) { i ->
                val start = System.currentTimeMillis()
                val res = AdbManager.executeCommand("getprop ro.product.model")
                val end = System.currentTimeMillis()
                println("⚡ Comando $i ($res) levou ${end - start}ms")
                delay(200)
            }
        }

        commandsJob.join()
        logcatJob.cancel()
        println("✅ Teste de Estresse concluído.")
    }
}
