package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

object LogcatTool {
    fun start(scope: CoroutineScope, callback: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val conn = AdbManager.connection ?: return@launch
            try {
                val stream = conn.open("shell:logcat")
                while (!stream.isClosed) {
                    val data = stream.read()
                    if (data.isNotEmpty()) {
                        callback(String(data))
                    }
                    delay(100) // Pequeno delay para não sobrecarregar
                }
            } catch (e: Exception) {
                callback("LOGCAT PARADO: ${e.message}")
            }
        }
    }
}
