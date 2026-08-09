package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val autoSend: Boolean = true,
    val smsTemplate: String = "Your voucher is {voucher_code}",
    val duplicateProtection: Boolean = true,
    val autoSync: Boolean = false,
    val webAppUrl: String? = "https://script.google.com/macros/s/AKfycbw3VPS8m4SAq0W_LEH2NfpLxLnkmEWoG8aBpjrygf8goErQAv3B2ipJrYPvxbk6wj2tdg/exec",
    val syncWifiOnly: Boolean = true,
    val lastSyncTime: String? = null,
    val giftEnabled: Boolean = true,
    val giftThreshold: Int = 10,
    val giftCategoryPreference: String = "MOST_BOUGHT"
)
