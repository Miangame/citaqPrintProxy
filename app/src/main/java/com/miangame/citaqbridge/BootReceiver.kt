package com.miangame.citaqbridge
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val settings = SettingsRepository(context)
            if (settings.autoStartOnBoot) {
                val svc = Intent(context, TcpPrintBridgeService::class.java)
                svc.action = TcpPrintBridgeService.ACTION_START
                svc.putExtra(TcpPrintBridgeService.EXTRA_PORT, settings.port)
                context.startService(svc)
            }
        }
    }
}