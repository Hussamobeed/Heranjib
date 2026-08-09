package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.SmsMonitoringService
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val dao = AppDatabase.getDatabase(context).appDao()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = dao.getSettingsSync()
                    if (settings == null || settings.autoSend) {
                        SmsMonitoringService.startService(context)
                        com.example.worker.ServiceRestartWorker.enqueue(context)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
