package com.miangame.citaqbridge

import android.content.Context
import android.content.SharedPreferences


class SettingsRepository(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("citaq_bridge_prefs", Context.MODE_PRIVATE)


    var port: Int
        get() = prefs.getInt(KEY_PORT, 9100)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()


    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSTART, value).apply()


    var lastPrintAt: Long
        get() = prefs.getLong(KEY_LASTPRINT, 0L)
        set(value) = prefs.edit().putLong(KEY_LASTPRINT, value).apply()


    companion object {
        private const val KEY_PORT = "port"
        private const val KEY_AUTOSTART = "auto_start"
        private const val KEY_LASTPRINT = "last_print"
    }
}