package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.ProxyPreferences
import com.example.service.ProxyForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = ProxyPreferences(context)
            val config = prefs.loadConfig()
            if (config.autoStartOnBoot) {
                ProxyForegroundService.startService(context)
            }
        }
    }
}
