package com.rescuedroid.rescuedroid.adb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.cgutman.adblib.AdbChannel

class UsbChannel(
    private val connection: UsbDeviceConnection,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint
) : AdbChannel {

    override fun read(buffer: ByteArray) {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val res = connection.bulkTransfer(epIn, buffer, totalRead, buffer.size - totalRead, 0)
            if (res < 0) throw Exception("Erro na leitura USB")
            totalRead += res
        }
    }

    override fun write(buffer: ByteArray) {
        var totalWritten = 0
        while (totalWritten < buffer.size) {
            val res = connection.bulkTransfer(epOut, buffer, totalWritten, buffer.size - totalWritten, 0)
            if (res < 0) throw Exception("Erro na escrita USB")
            totalWritten += res
        }
    }

    override fun close() {
        connection.close()
    }
}
