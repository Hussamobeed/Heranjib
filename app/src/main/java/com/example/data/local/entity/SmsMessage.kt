package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val fingerprint: String,
    val status: String,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val nextRetryTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
