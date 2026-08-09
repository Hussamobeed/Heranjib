package com.example.di

import android.content.Context
import com.example.core.TransactionManager
import com.example.data.local.AppDatabase

object DependencyContainer {
    @Volatile
    private var transactionManager: TransactionManager? = null
    
    fun getTransactionManager(context: Context): TransactionManager {
        return transactionManager ?: synchronized(this) {
            val db = AppDatabase.getDatabase(context)
            val manager = TransactionManager(db, context.applicationContext)
            transactionManager = manager
            manager
        }
    }
}
