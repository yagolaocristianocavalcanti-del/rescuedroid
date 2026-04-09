package com.rescuedroid.rescuedroid.adb

class UsbAdaptiveTimeout {
    private var failureCount = 0
    private var successCount = 0
    private var currentTimeout = 10000
    
    fun recordFailure() {
        failureCount++
        if (failureCount > 3) {
            // Aumenta timeout progressivamente
            currentTimeout = (currentTimeout * 1.5).toInt()
            failureCount = 0
        }
    }
    
    fun recordSuccess() {
        successCount++
        if (successCount > 10) {
            // Diminui timeout se estiver bom
            currentTimeout = (currentTimeout * 0.9).toInt()
            currentTimeout = currentTimeout.coerceAtLeast(5000)
            successCount = 0
        }
    }
    
    fun getTimeout(): Int = currentTimeout
}
