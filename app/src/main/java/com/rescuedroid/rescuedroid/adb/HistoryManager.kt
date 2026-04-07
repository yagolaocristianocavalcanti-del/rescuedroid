package com.rescuedroid.rescuedroid.adb

import android.content.Context
import android.content.SharedPreferences

object HistoryManager {
    private const val PREFS_NAME = "rescuedroid_history"
    private const val KEY_LAST_IP = "last_adb_ip"
    private const val KEY_LAST_MODEL = "last_device_model"
    private const val KEY_LAST_SERIAL = "last_device_serial"
    private const val KEY_KNOWN_DEVICES = "known_devices"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConnection(context: Context, ip: String, model: String, serial: String = "") {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_LAST_IP, ip)
            .putString(KEY_LAST_MODEL, model)
            .putString(KEY_LAST_SERIAL, serial)
            .apply()
            
        // Adiciona à lista de conhecidos (formato IP|Modelo|Serial)
        val known = getKnownDevices(context).toMutableSet()
        known.add("$ip|$model|$serial")
        prefs.edit().putStringSet(KEY_KNOWN_DEVICES, known).apply()
    }

    fun getLastIp(context: Context): String = getPrefs(context).getString(KEY_LAST_IP, "") ?: ""
    fun getLastModel(context: Context): String = getPrefs(context).getString(KEY_LAST_MODEL, "Desconhecido") ?: "Desconhecido"
    fun getLastSerial(context: Context): String = getPrefs(context).getString(KEY_LAST_SERIAL, "") ?: ""

    fun getKnownDevices(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_KNOWN_DEVICES, emptySet()) ?: emptySet()
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
