package com.miangame.citaqbridge
import android.app.ActivityManager
import android.content.Context


fun Context.isBridgeRunning(): Boolean {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    for (s in am.getRunningServices(Int.MAX_VALUE)) {
        if (s.service.className == TcpPrintBridgeService::class.qualifiedName) return true
    }
    return false
}