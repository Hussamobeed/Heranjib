package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerPhone: String,
    val amount: Double,
    val voucherNumber: String,
    val categoryName: String,
    val smsStatus: String,
    val transactionDate: String,
    val transactionTime: String,
    val originalSms: String,
    val isSynced: Boolean = false
)
