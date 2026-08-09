package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.di.DependencyContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                pendingResult.finish()
                return
            }
            
            val sender = messages[0]?.displayOriginatingAddress ?: ""
            val fullMessage = StringBuilder()
            for (message in messages) {
                fullMessage.append(message?.displayMessageBody ?: "")
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val transactionManager = DependencyContainer.getTransactionManager(context)
                    transactionManager.receiveSms(sender, fullMessage.toString(), System.currentTimeMillis())
                    com.example.service.SmsMonitoringService.startService(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
