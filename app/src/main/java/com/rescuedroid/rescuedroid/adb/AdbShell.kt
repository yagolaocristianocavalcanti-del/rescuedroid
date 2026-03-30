package com.rescuedroid.rescuedroid.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbShell {
    suspend fun run(cmd: String): String = withContext(Dispatchers.IO) {
        AdbManager.executeCommand(cmd)
    }
}
