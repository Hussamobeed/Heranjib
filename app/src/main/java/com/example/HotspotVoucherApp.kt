package com.example

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.SyncWorker
import java.util.concurrent.TimeUnit

class HotspotVoucherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).cancelUniqueWork("AppScriptPeriodicSync")
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SabaPeriodicSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

