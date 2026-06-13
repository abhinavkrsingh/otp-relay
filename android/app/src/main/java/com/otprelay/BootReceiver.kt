package com.otprelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Restarts the foreground service automatically after a phone reboot
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"  // Vivo/HTC
        ) {
            context.startForegroundService(
                Intent(context, RelayForegroundService::class.java)
            )
        }
    }
}
