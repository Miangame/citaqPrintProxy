package com.miangame.citaqbridge

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object Logger {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)


    fun log(ctx: Context, msg: String) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val file = File(dir, "bridge.log")
            file.appendText("${fmt.format(Date())} | $msg")
        } catch (_: Exception) { /* no-op */ }
    }
}