package com.rescuedroid.rescuedroid.fastboot

/**
 * Gerenciador de comandos Fastboot para baixo nível.
 */
object FastbootManager {

    /**
     * Retorna o comando para listar dispositivos em modo Fastboot.
     */
    fun devices(): String {
        return "fastboot devices"
    }

    /**
     * Retorna o comando ADB para reiniciar o dispositivo no modo bootloader.
     */
    fun rebootBootloader(): String {
        return "adb reboot bootloader"
    }

    /**
     * Retorna o comando Fastboot para destravar o bootloader.
     */
    fun unlock(): String {
        return "fastboot oem unlock"
    }

    /**
     * Retorna o comando para reiniciar o dispositivo normalmente a partir do Fastboot.
     */
    fun rebootNormal(): String {
        return "fastboot reboot"
    }
}
