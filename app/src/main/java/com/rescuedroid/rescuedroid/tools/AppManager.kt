package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbManager

object AppManager {
    suspend fun listApps(): List<String> {
        val res = AdbManager.exec("pm list packages")
        return res.split("\n")
            .map { it.replace("package:", "").trim() }
            .filter { it.isNotBlank() }
    }

    suspend fun uninstall(pkg: String) {
        AdbManager.execSilent("pm uninstall --user 0 $pkg")
    }

    suspend fun disable(pkg: String) {
        AdbManager.execSilent("pm disable-user --user 0 $pkg")
    }
}
