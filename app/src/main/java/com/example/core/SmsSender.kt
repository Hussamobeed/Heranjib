package com.example.core

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class SmsSender(private val context: Context) {
    /** True confirms submission of every part to the SMS network, not delivery to the handset. */
    suspend fun sendSms(phoneNumber: String, message: String): Boolean {
        val action="${context.packageName}.SABA_SMS_${UUID.randomUUID()}"
        val completion=CompletableDeferred<Boolean>()
        val received=mutableSetOf<Int>()
        val manager=context.getSystemService(SmsManager::class.java)
        val parts=manager.divideMessage(message)
        val receiver=object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if(intent?.action!=action)return
                synchronized(received) {
                    if(resultCode!=Activity.RESULT_OK)completion.complete(false)
                    else {received.add(intent.getIntExtra("part",-1));if(received.size==parts.size)completion.complete(true)}
                }
            }
        }
        var registered=false
        val callbacks=ArrayList<PendingIntent>()
        return try {
            ContextCompat.registerReceiver(context,receiver,IntentFilter(action),ContextCompat.RECEIVER_NOT_EXPORTED)
            registered=true
            parts.indices.forEach { index -> callbacks.add(PendingIntent.getBroadcast(context,index,Intent(action).setPackage(context.packageName).putExtra("part",index),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)) }
            if(parts.size>1)manager.sendMultipartTextMessage(phoneNumber,null,parts,callbacks,null)
            else manager.sendTextMessage(phoneNumber,null,message,callbacks.first(),null)
            withTimeoutOrNull(90000){completion.await()} ?: false
        } catch (_:Exception) { false }
        finally {callbacks.forEach{it.cancel()};if(registered)context.unregisterReceiver(receiver)}
    }
}
