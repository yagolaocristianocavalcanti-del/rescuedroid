package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbManager

object DeviceTools {

    suspend fun getModel(): String {
        return AdbManager.exec("getprop ro.product.model")
    }

    suspend fun getAndroid(): String {
        return AdbManager.exec("getprop ro.build.version.release")
    }

    suspend fun getBattery(): String {
        return AdbManager.exec("dumpsys battery")
    }

    suspend fun reboot() {
        AdbManager.execSilent("reboot")
    }

    suspend fun rebootRecovery() {
        AdbManager.execSilent("reboot recovery")
    }

    suspend fun rebootBootloader() {
        AdbManager.execSilent("reboot bootloader")
    }
}
