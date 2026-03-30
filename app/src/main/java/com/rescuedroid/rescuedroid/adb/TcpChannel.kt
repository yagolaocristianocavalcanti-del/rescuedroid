package com.rescuedroid.rescuedroid.adb

import com.cgutman.adblib.AdbChannel
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class TcpChannel(private val socket: Socket) : AdbChannel {
    private val inputStream: InputStream = socket.getInputStream()
    private val outputStream: OutputStream = socket.getOutputStream()

    override fun read(buffer: ByteArray) {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val res = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (res < 0) throw Exception("Stream closed")
            totalRead += res
        }
    }

    override fun write(buffer: ByteArray) {
        outputStream.write(buffer)
        outputStream.flush()
    }

    override fun close() {
        socket.close()
    }
}
