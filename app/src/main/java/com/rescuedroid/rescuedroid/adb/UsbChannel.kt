package com.rescuedroid.rescuedroid.adb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import com.cgutman.adblib.AdbChannel
import java.io.IOException
import kotlin.math.min

class UsbChannel(
    private val connection: UsbDeviceConnection,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
    private val isLegacyDevice: Boolean = false
) : AdbChannel {

    companion object {
        private const val TAG = "UsbChannel"
        
        // Timeouts adaptativos
        private const val USB_TIMEOUT_NORMAL = 10000    // Devices modernos
        private const val USB_TIMEOUT_LEGACY = 30000    // Android 4-6
        private const val USB_TIMEOUT_CRITICAL = 60000  // Muito lento
        
        // Retry strategy
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 500
        
        // Buffer strategy
        private const val CHUNK_SIZE_NORMAL = 16384     // 16KB
        private const val CHUNK_SIZE_LEGACY = 4096      // 4KB - Mais seguro
    }

    private val usb_timeout = if (isLegacyDevice) USB_TIMEOUT_LEGACY else USB_TIMEOUT_NORMAL
    private val chunk_size = if (isLegacyDevice) CHUNK_SIZE_LEGACY else CHUNK_SIZE_NORMAL
    private var consecutiveFailures = 0
    
    override fun read(buffer: ByteArray) {
        var totalRead = 0
        var retries = 0
        
        while (totalRead < buffer.size) {
            try {
                // Calcula quanto ler nesta iteração
                val toRead = min(chunk_size, buffer.size - totalRead)
                
                // Aumenta timeout se estiver falhando
                val currentTimeout = when {
                    consecutiveFailures > 2 -> USB_TIMEOUT_CRITICAL
                    isLegacyDevice -> USB_TIMEOUT_LEGACY
                    else -> usb_timeout
                }
                
                val res = connection.bulkTransfer(
                    epIn,
                    buffer,
                    totalRead,
                    toRead,
                    currentTimeout
                )
                
                when {
                    res > 0 -> {
                        // Sucesso! Reseta contador de falhas
                        totalRead += res
                        consecutiveFailures = 0
                        retries = 0
                        Log.d(TAG, "✅ Leu $res bytes (total: $totalRead/${buffer.size})")
                    }
                    res == 0 -> {
                        // Timeout - Retry com backoff exponencial
                        if (retries < MAX_RETRIES) {
                            retries++
                            val delayMs = RETRY_DELAY_MS * retries
                            Log.w(TAG, "⏱️ Timeout na leitura - Retry $retries/$MAX_RETRIES (aguardando ${delayMs}ms)")
                            Thread.sleep(delayMs.toLong())
                        } else {
                            consecutiveFailures++
                            if (consecutiveFailures >= 3) {
                                throw IOException("❌ Múltiplos timeouts USB. Cabo pode estar solto ou device desconectado.")
                            }
                            // Tenta aumentar timeout
                            Log.w(TAG, "⚠️ Timeout persistente - Aumentando timeout para próxima tentativa")
                        }
                    }
                    else -> {
                        // Erro crítico (-1, -2, etc)
                        consecutiveFailures++
                        throw IOException(
                            "❌ Erro crítico na leitura USB (código: $res)\n" +
                            "• Verifique se o cabo está bem conectado\n" +
                            "• Tente modo MARTELO na conexão\n" +
                            "• Reinicie o dispositivo\n" +
                            "Detalhes: ${getErrorDescription(res)}"
                        )
                    }
                }
                
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Leitura USB foi interrompida", e)
            }
        }
        
        Log.d(TAG, "✅ Leitura completa: ${buffer.size} bytes")
    }

    override fun write(buffer: ByteArray) {
        var totalWritten = 0
        var retries = 0
        
        while (totalWritten < buffer.size) {
            try {
                val toWrite = min(chunk_size, buffer.size - totalWritten)
                
                val currentTimeout = when {
                    consecutiveFailures > 2 -> USB_TIMEOUT_CRITICAL
                    isLegacyDevice -> USB_TIMEOUT_LEGACY
                    else -> usb_timeout
                }
                
                val res = connection.bulkTransfer(
                    epOut,
                    buffer,
                    totalWritten,
                    toWrite,
                    currentTimeout
                )
                
                when {
                    res > 0 -> {
                        totalWritten += res
                        consecutiveFailures = 0
                        retries = 0
                        Log.d(TAG, "✅ Escreveu $res bytes (total: $totalWritten/${buffer.size})")
                    }
                    res == 0 -> {
                        if (retries < MAX_RETRIES) {
                            retries++
                            val delayMs = RETRY_DELAY_MS * retries
                            Log.w(TAG, "⏱️ Timeout na escrita - Retry $retries/$MAX_RETRIES")
                            Thread.sleep(delayMs.toLong())
                        } else {
                            consecutiveFailures++
                            if (consecutiveFailures >= 3) {
                                throw IOException("❌ Múltiplos timeouts na escrita USB")
                            }
                        }
                    }
                    else -> {
                        consecutiveFailures++
                        throw IOException(
                            "❌ Erro na escrita USB (código: $res)\n" +
                            "${getErrorDescription(res)}"
                        )
                    }
                }
                
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Escrita USB foi interrompida", e)
            }
        }
        
        Log.d(TAG, "✅ Escrita completa: ${buffer.size} bytes")
    }

    override fun close() {
        try {
            connection.close()
            Log.d(TAG, "🔌 Conexão USB fechada")
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao fechar conexão USB: ${e.message}")
        }
    }
    
    private fun getErrorDescription(code: Int): String = when (code) {
        -1 -> "Erro geral USB ou dispositivo desconectado"
        -2 -> "Device não encontrado ou sem permissão"
        -3 -> "Timeout excessivo"
        -4 -> "Erro de buffer"
        else -> "Código de erro desconhecido: $code"
    }
}
