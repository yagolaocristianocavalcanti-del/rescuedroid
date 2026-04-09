package com.rescuedroid.rescuedroid.adb

import android.hardware.usb.UsbDevice
import android.util.Log

/**
 * Detecta características USB do dispositivo para otimizar conexão
 */
object UsbDetector {
    
    private const val TAG = "UsbDetector"
    
    enum class UsbSpeed(val bandwidth: Int) {
        USB_1_1(1500),      // 1.5 Mbps - MUITO LENTO
        USB_2_0(480000),    // 480 Mbps - Padrão
        USB_3_0(5000000),   // 5 Gbps - Rápido
        USB_3_1(10000000),  // 10 Gbps - Muito rápido
        UNKNOWN(0)          // Desconhecido
    }
    
    enum class DeviceClass {
        ANDROID_4_5,        // Android 4.0 - 5.x (MUITO ANTIGO)
        ANDROID_6_7,        // Android 6.0 - 7.x (ANTIGO)
        ANDROID_8_9,        // Android 8.0 - 9.x (MODERNO)
        ANDROID_10_PLUS,    // Android 10+ (NOVO)
        UNKNOWN             // Desconhecido
    }
    
    data class UsbInfo(
        val speed: UsbSpeed,
        val deviceClass: DeviceClass,
        val isAdbCapable: Boolean,
        val hasIssues: Boolean,
        val recommendations: List<String>,
        val optimalMode: String
    ) {
        fun toReadableString(): String = buildString {
            appendLine("📱 Informações USB:")
            appendLine("  • Velocidade: ${speed.name} (${speed.bandwidth / 1000.0} Mbps)")
            appendLine("  • Android Version: ${deviceClass.name}")
            appendLine("  • ADB Capable: ${if (isAdbCapable) "✅ SIM" else "❌ NÃO"}")
            
            if (hasIssues) {
                appendLine("\n⚠️ Problemas Detectados:")
                recommendations.forEach { appendLine("  • $it") }
            }
            
            appendLine("\n🎯 Modo Recomendado: $optimalMode")
        }
    }
    
    /**
     * Analisa um dispositivo USB e retorna informações detalhadas
     */
    fun analyze(device: UsbDevice): UsbInfo {
        val speed = detectUsbSpeed(device)
        val androidVersion = detectAndroidVersion(device)
        val isAdbCapable = hasAdbInterface(device)
        val hasIssues = speed == UsbSpeed.USB_1_1 || androidVersion == DeviceClass.ANDROID_4_5
        val recommendations = generateRecommendations(speed, androidVersion)
        val optimalMode = selectOptimalMode(speed, androidVersion)
        
        Log.i(TAG, "🔍 Device Analizado: ${device.deviceName}")
        Log.i(TAG, "   USB Speed: $speed | Android: $androidVersion | ADB: $isAdbCapable")
        
        return UsbInfo(
            speed = speed,
            deviceClass = androidVersion,
            isAdbCapable = isAdbCapable,
            hasIssues = hasIssues,
            recommendations = recommendations,
            optimalMode = optimalMode
        )
    }
    
    /**
     * Detecta a velocidade USB baseada na versão do protocolo
     */
    private fun detectUsbSpeed(device: UsbDevice): UsbSpeed {
        // device.version no Android retorna a versão do dispositivo, que codifica a velocidade USB
        val version = try {
            val v = device.version
            // Heurística simples se version vier como string
            when {
                v.startsWith("3.") -> 0x0300
                v.startsWith("2.") -> 0x0200
                v.startsWith("1.") -> 0x0110
                else -> 0x0200
            }
        } catch (e: Exception) {
            0x0200
        }
        
        val speed = when {
            // USB 3.1+
            (version and 0xFF00) shr 8 >= 0x03 && (version and 0x00FF) >= 0x10 -> UsbSpeed.USB_3_1
            
            // USB 3.0
            (version and 0xFF00) shr 8 >= 0x03 -> UsbSpeed.USB_3_0
            
            // USB 2.0
            (version and 0xFF00) shr 8 >= 0x02 -> UsbSpeed.USB_2_0
            
            // USB 1.1 ou menor
            (version and 0xFF00) shr 8 >= 0x01 -> UsbSpeed.USB_1_1
            
            else -> UsbSpeed.UNKNOWN
        }
        
        Log.d(TAG, "📡 USB Speed detectada: $speed (version: 0x${String.format("%04X", version)})")
        return speed
    }
    
    /**
     * Estima a versão do Android baseada na API level
     */
    private fun detectAndroidVersion(device: UsbDevice): DeviceClass {
        val deviceInfo = buildString {
            append("${device.manufacturerName ?: "Unknown"} ")
            append("${device.productName ?: "Unknown"} ")
        }.lowercase()
        
        // Heurísticas por fabricante/modelo
        val androidVersion = when {
            deviceInfo.contains("samsung") && 
            (deviceInfo.contains("gt-i9300") || deviceInfo.contains("gt-i9500") || deviceInfo.contains("sm-g900")) -> 
                DeviceClass.ANDROID_4_5
            
            deviceInfo.contains("xiaomi") && deviceInfo.contains("2014") -> DeviceClass.ANDROID_4_5
            
            deviceInfo.contains("old") || deviceInfo.contains("legacy") || deviceInfo.contains("ancient") ->
                DeviceClass.ANDROID_4_5
            
            else -> DeviceClass.ANDROID_10_PLUS
        }
        
        Log.d(TAG, "📊 Android Version estimada: $androidVersion (${device.manufacturerName} ${device.productName})")
        return androidVersion
    }
    
    private fun hasAdbInterface(device: UsbDevice): Boolean {
        return try {
            (0 until device.interfaceCount).any { i ->
                val iface = device.getInterface(i)
                iface.interfaceClass == 0xFF && iface.interfaceSubclass == 0x42
            }.also {
                Log.d(TAG, "🔌 Interface ADB: ${if (it) "✅ Encontrada" else "❌ Não encontrada"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar interface ADB: ${e.message}")
            false
        }
    }
    
    private fun generateRecommendations(speed: UsbSpeed, androidVersion: DeviceClass): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (speed) {
            UsbSpeed.USB_1_1 -> {
                recommendations.add("⚠️ USB 1.1 detectado - Conexão muito lenta")
                recommendations.add("💡 Use modo MARTELO (6 tentativas)")
                recommendations.add("🔌 Verifique se o cabo está em bom estado")
                recommendations.add("🖥️ Tente em porta USB traseira do computador")
            }
            UsbSpeed.USB_2_0 -> {
                if (androidVersion == DeviceClass.ANDROID_4_5) {
                    recommendations.add("📱 Device antigo detectado (Android 4-5)")
                    recommendations.add("💡 Use modo FORTE ou MARTELO")
                    recommendations.add("⏱️ Aumente os timeouts")
                }
            }
            UsbSpeed.USB_3_0, UsbSpeed.USB_3_1 -> {
                recommendations.add("✅ USB 3.0+ - Ótima conexão esperada")
                recommendations.add("💚 Modo NORMAL deveria funcionar")
            }
            else -> {
                recommendations.add("❓ Velocidade USB desconhecida")
                recommendations.add("💡 Tente modo FORTE para ser seguro")
            }
        }
        
        return recommendations
    }
    
    fun selectOptimalMode(speed: UsbSpeed, androidVersion: DeviceClass): String {
        return when {
            speed == UsbSpeed.USB_1_1 -> "MARTELO 🔨"
            androidVersion == DeviceClass.ANDROID_4_5 && speed == UsbSpeed.USB_2_0 -> "FORTE 💪"
            androidVersion == DeviceClass.ANDROID_4_5 -> "FORTE 💪"
            androidVersion == DeviceClass.ANDROID_6_7 -> "FORTE 💪"
            androidVersion in listOf(DeviceClass.ANDROID_8_9, DeviceClass.ANDROID_10_PLUS) &&
            speed in listOf(UsbSpeed.USB_3_0, UsbSpeed.USB_3_1) -> "NORMAL ✅"
            else -> "FORTE 💪"
        }
    }
    
    fun speedToConnectMode(speed: UsbSpeed): UsbAdbConnector.ConnectMode {
        return when (speed) {
            UsbSpeed.USB_1_1 -> UsbAdbConnector.ConnectMode.HAMMER
            UsbSpeed.USB_2_0 -> UsbAdbConnector.ConnectMode.STRONG
            UsbSpeed.USB_3_0, UsbSpeed.USB_3_1 -> UsbAdbConnector.ConnectMode.NORMAL
            else -> UsbAdbConnector.ConnectMode.STRONG
        }
    }
}
