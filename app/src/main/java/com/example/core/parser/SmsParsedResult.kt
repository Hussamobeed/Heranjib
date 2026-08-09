package com.example.core.parser

data class SmsParsedResult(
    val phoneNumber: String?,
    val amount: Int?,
    val rawMessage: String,
    val timestamp: Long,
    val isValid: Boolean,
    val reason: String? = null
)
