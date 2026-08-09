package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.core.TransactionManager
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.di.DependencyContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AppRepository
    private lateinit var transactionManager: TransactionManager

    companion object {
        const val CHANNEL_ID = "SmsMonitoringChannel"
        const val NOTIFICATION_ID = 1
        
        fun startService(context: Context) {
            try {
                val intent = Intent(context, SmsMonitoringService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, SmsMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(this).appDao()
        repository = AppRepository(dao)
        transactionManager = DependencyContainer.getTransactionManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification(0, 0, 0)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, 
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        observeStats()
        startQueueProcessor()

        return START_STICKY
    }

    private fun startQueueProcessor() {
        serviceScope.launch {
            // Crash recovery on start
            transactionManager.recoverUnfinishedTransactions()

            while (isActive) {
                val processed = transactionManager.processNextSms()
                if (!processed) {
                    delay(5000) // Sleep when idle or waiting for next retry
                }
            }
        }
    }

    private var isLowStockAlertTriggered = false

    private fun observeStats() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        serviceScope.launch {
            combine(
                repository.getAvailableVouchersCount(),
                repository.getTodaySalesCount(today).map { it ?: 0 },
                repository.getTodayFailedSalesCountFromStats(today).map { it ?: 0 }
            ) { available: Int, successful: Int, failed: Int ->
                Triple(available, successful, failed)
            }.collect { (available, successful, failed) ->
                updateNotification(available, successful, failed)
                checkLowStockAlert(available)
            }
        }
    }

    private fun checkLowStockAlert(available: Int) {
        if (available <= 5 && !isLowStockAlertTriggered) {
            isLowStockAlertTriggered = true
            showLowStockNotification(available)
        } else if (available > 5) {
            isLowStockAlertTriggered = false
        }
    }

    private fun showLowStockNotification(available: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                "AlertChannel",
                "تنبيهات النظام",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(alertChannel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "AlertChannel")
            .setContentTitle("تنبيه انخفاض الرصيد")
            .setContentText("المتبقي من القسائم $available فقط! الرجاء إضافة المزيد.")
            .setSmallIcon(R.drawable.ic_ticket) // Assuming this icon exists; you can use android.R.drawable.ic_dialog_alert if not
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(2, notification)
    }

    private fun updateNotification(available: Int, successful: Int, failed: Int) {
        val notification = createNotification(available, successful, failed)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(available: Int, successful: Int, failed: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = "متاحة: $available | مبيعات: $successful | فشل: $failed"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مدير بطاقات الهوت سبوت")
            .setContentText("يتم مراقبة الرسائل الواردة")
            .setStyle(NotificationCompat.BigTextStyle().bigText("يتم مراقبة الرسائل الواردة\n$text"))
            .setSmallIcon(R.drawable.ic_ticket)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة مراقبة الرسائل",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة خدمة مدير قسائم الهوتسبوت"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
