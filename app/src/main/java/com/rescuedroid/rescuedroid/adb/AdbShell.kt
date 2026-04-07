package com.rescuedroid.rescuedroid.adb

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbShell @Inject constructor(
    private val adbRepository: AdbRepository
) {
    suspend fun run(cmd: String, serial: String? = null): String {
        return adbRepository.execute(cmd, serial = serial)
    }

    /**
     * Tenta executar o comando no dispositivo específico.
     */
    suspend fun runForDevice(serial: String, cmd: String): String {
        return adbRepository.execute(cmd, serial = serial)
    }
}
