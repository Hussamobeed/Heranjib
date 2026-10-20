package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "app_logs")
data class AppLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val message: String,
    @ColumnInfo(defaultValue = "0") val sabaSynced: Boolean = false,
    val isSynced: Boolean = false
)

