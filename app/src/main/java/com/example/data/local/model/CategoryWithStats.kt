package com.example.data.local.model

import androidx.room.Embedded
import com.example.data.local.entity.VoucherCategory

data class CategoryWithStats(
    @Embedded val category: VoucherCategory,
    val totalCount: Int,
    val availableCount: Int,
    val sentCount: Int
)
