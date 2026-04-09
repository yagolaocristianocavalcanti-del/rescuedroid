package com.rescuedroid.rescuedroid.adb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.cgutman.adblib.AdbChannel
import java.io.IOException

class UsbChannel(
    private val connection: UsbDeviceConnection,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint
) : AdbChannel {

    companion object {
        private const val USB_TIMEOUT = 10000 // 10 segundos para cada bloco de transferência
    }

    override fun read(buffer: ByteArray) {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val res = connection.bulkTransfer(epIn, buffer, totalRead, buffer.size - totalRead, USB_TIMEOUT)
            if (res < 0) {
                throw IOException("Falha crítica na leitura USB (res=$res). O cabo pode estar instável.")
            }
            totalRead += res
        }
    }

    override fun write(buffer: ByteArray) {
        var totalWritten = 0
        while (totalWritten < buffer.size) {
            val res = connection.bulkTransfer(epOut, buffer, totalWritten, buffer.size - totalWritten, USB_TIMEOUT)
            if (res < 0) {
                throw IOException("Falha crítica na escrita USB (res=$res). Verifique a conexão física.")
            }
            totalWritten += res
        }
    }

    override fun close() {
        // O AdbConnection chama o close do canal ao falhar ou terminar.
        // Fechamos a conexão USB aqui para garantir que os recursos nativos sejam liberados.
        try {
            connection.close()
        } catch (e: Exception) {
            // Ignora erros ao fechar
        }
    }
}
