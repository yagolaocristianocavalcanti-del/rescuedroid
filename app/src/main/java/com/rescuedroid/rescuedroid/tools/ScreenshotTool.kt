package com.rescuedroid.rescuedroid.tools

import com.rescuedroid.rescuedroid.adb.AdbManager

object ScreenshotTool {

    suspend fun capture() {
        AdbManager.execSilent("screencap -p /sdcard/rescuedroid.png")
    }
}
