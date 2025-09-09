package com.miangame.citaqbridge
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "BOOT_COMPLETED recibido → arrancando servicio")
            val svc = Intent(context, TcpPrintBridgeService::class.java)
            context.startService(svc)
        }
    }
}