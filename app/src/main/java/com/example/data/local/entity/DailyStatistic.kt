package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_statistics", primaryKeys = ["date", "categoryId"])
data class DailyStatistic(
    val date: String,
    val categoryId: Long,
    val totalSalesCount: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalSentVouchers: Int = 0,
    val totalFailedSMS: Int = 0,
    val lastUpdated: Long = 0L,
    val isSynced: Boolean = false
)
