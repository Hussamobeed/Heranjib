package com.example.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsParserEngine {

    // Matches exactly 9 digits starting with 7
    // Using negative lookbehind and lookahead to ensure it's not part of a larger number
    private val phoneRegex = Regex("(?<!\\d)7\\d{8}(?!\\d)")
    
    // Matches numbers in the text
    private val amountRegex = Regex("(?<!\\d)\\d+(?!\\d)")

    suspend fun parse(rawMessage: String, timestamp: Long, validCategories: List<Int>): SmsParsedResult = withContext(Dispatchers.Default) {
        // Layer 1: Extract phone number
        val phoneMatches = phoneRegex.findAll(rawMessage).map { it.value }.toList()
        
        if (phoneMatches.isEmpty()) {
            return@withContext SmsParsedResult(
                phoneNumber = null,
                amount = null,
                rawMessage = rawMessage,
                timestamp = timestamp,
                isValid = false,
                reason = "No valid phone number found"
            )
        }
        
        if (phoneMatches.size > 1) {
            // Check if they are all the same number, if so, it's fine. Otherwise reject.
            val uniquePhones = phoneMatches.distinct()
            if (uniquePhones.size > 1) {
                return@withContext SmsParsedResult(
                    phoneNumber = null,
                    amount = null,
                    rawMessage = rawMessage,
                    timestamp = timestamp,
                    isValid = false,
                    reason = "Multiple different phone numbers found"
                )
            }
        }

        val phoneNumber = phoneMatches.first()

        // Layer 2 & 3: Extract amount
        // We find all numeric tokens
        val numericMatches = amountRegex.findAll(rawMessage).map { it.value.toIntOrNull() }.filterNotNull().toList()
        
        // Filter out the phone number from the numeric matches if it was captured
        // The phone number is 9 digits, amount is unlikely to be 9 digits starting with 7, but let's be safe
        // Actually phoneRegex is 9 digits, so if there is a 9 digit number starting with 7, it's the phone.
        val possibleAmounts = numericMatches.filter { it.toString() != phoneNumber }

        if (possibleAmounts.isEmpty()) {
            return@withContext SmsParsedResult(
                phoneNumber = phoneNumber,
                amount = null,
                rawMessage = rawMessage,
                timestamp = timestamp,
                isValid = false,
                reason = "No valid amount found"
            )
        }

        // Take amounts that match one of the valid categories
        val matchedAmounts = possibleAmounts.filter { validCategories.contains(it) }
        
        if (matchedAmounts.isEmpty()) {
            return@withContext SmsParsedResult(
                phoneNumber = phoneNumber,
                amount = null,
                rawMessage = rawMessage,
                timestamp = timestamp,
                isValid = false,
                reason = "No amount matching any existing voucher category found"
            )
        }

        // Take highest value from matched amounts (in case multiple categories match, pick the highest, though unlikely)
        val amount = matchedAmounts.maxOrNull() ?: 0

        // If amount is 0, reject
        if (amount <= 0) {
            return@withContext SmsParsedResult(
                phoneNumber = phoneNumber,
                amount = null,
                rawMessage = rawMessage,
                timestamp = timestamp,
                isValid = false,
                reason = "Amount must be greater than 0"
            )
        }

        return@withContext SmsParsedResult(
            phoneNumber = phoneNumber,
            amount = amount,
            rawMessage = rawMessage,
            timestamp = timestamp,
            isValid = true,
            reason = "Success"
        )
    }
}
