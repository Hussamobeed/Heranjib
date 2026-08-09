package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vouchers",
    indices = [Index(value = ["categoryId", "voucherNumber"], unique = true)]
)
data class Voucher(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val voucherNumber: String,
    val status: Int = 0, // 0 = Available, 1 = Sent
    val customerPhone: String? = null,
    val sentDate: String? = null,
    val sentTime: String? = null,
    val importedAt: Long = System.currentTimeMillis()
)
