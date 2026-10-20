package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "vouchers",
    indices = [Index(value = ["categoryId", "voucherNumber"], unique = true), Index(value = ["remoteId"], unique = true)]
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
    val remoteId: String? = null,
    @ColumnInfo(defaultValue = "''") val password: String = "",
    @ColumnInfo(defaultValue = "''") val profile: String = "",
    @ColumnInfo(defaultValue = "-1") val syncedStatus: Int = -1,
    val importedAt: Long = System.currentTimeMillis()
)

