package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppManager @Inject constructor(
    private val adbRepository: AdbRepository
) {
    suspend fun listApps(
        filter: String = "all", // "all", "system", "user"
        serial: String? = null
    ): List<String> {
        val cmd = when (filter) {
            "system" -> "pm list packages -s"
            "user" -> "pm list packages -3"
            else -> "pm list packages"
        }
        val res = adbRepository.execute(cmd, serial = serial)
        return res.split("\n")
            .map { it.replace("package:", "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("❌") && !it.startsWith("⚠️") }
    }

    suspend fun uninstall(pkg: String, serial: String? = null) {
        adbRepository.execute("pm uninstall --user 0 $pkg", serial = serial)
    }

    suspend fun disable(pkg: String, serial: String? = null) {
        adbRepository.execute("pm disable-user --user 0 $pkg", serial = serial)
    }
}
