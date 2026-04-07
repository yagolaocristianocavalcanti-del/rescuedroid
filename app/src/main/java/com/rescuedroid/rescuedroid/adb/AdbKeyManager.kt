package com.rescuedroid.rescuedroid.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import com.cgutman.adblib.AdbBase64
import com.cgutman.adblib.AdbCrypto
import java.io.File

class AdbKeyManager(context: Context) {

    private val privateKeyFile = File(context.filesDir, "adb_private.key")
    private val publicKeyFile = File(context.filesDir, "adb_public.key")

    /**
     * Implementação de Base64 exigida pela adblib para Android.
     */
    private val adbBase64 = object : AdbBase64 {
        override fun encodeToString(data: ByteArray): String {
            return Base64.encodeToString(data, Base64.NO_WRAP)
        }
    }

    fun getOrCreateCrypto(): AdbCrypto? {
        return try {
            if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
                generateAndSaveKeys()
            }
            // AdbCrypto.loadAdbCrypto lança IOException se houver erro nos arquivos
            AdbCrypto.loadAdbCrypto(adbBase64, privateKeyFile, publicKeyFile)
        } catch (e: Exception) {
            Log.e("AdbKeyManager", "Erro ao carregar chaves ADB: ${e.message}", e)
            null
        }
    }

    fun forceRegenerateCrypto(): AdbCrypto? {
        return try {
            privateKeyFile.delete()
            publicKeyFile.delete()
            generateAndSaveKeys()
            AdbCrypto.loadAdbCrypto(adbBase64, privateKeyFile, publicKeyFile)
        } catch (e: Exception) {
            Log.e("AdbKeyManager", "Erro ao regenerar chaves ADB: ${e.message}", e)
            null
        }
    }

    private fun generateAndSaveKeys() {
        try {
            Log.d("AdbKeyManager", "Gerando novo par de chaves compatível com ADB...")
            // A própria biblioteca tem um gerador que formata as chaves corretamente
            val crypto = AdbCrypto.generateAdbKeyPair(adbBase64)
            // Salva nos arquivos definidos
            crypto.saveAdbKeyPair(privateKeyFile, publicKeyFile)
        } catch (e: Exception) {
            Log.e("AdbKeyManager", "Erro ao gerar chaves RSA", e)
        }
    }
}
