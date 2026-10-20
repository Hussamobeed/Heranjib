package com.example.core

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppLog
import com.example.data.local.entity.SmsMessage
import com.example.data.local.entity.Transaction
import com.example.data.local.entity.Voucher
import com.example.data.local.entity.VoucherCategory
import com.example.data.local.entity.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionManager(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dao = database.appDao()
    private val mutex = LedgerGate.sales

    // 1. Transaction Lock
    suspend fun processNextSms(): Boolean {
        return mutex.withLock {
            val sms = dao.getNextPendingSms(System.currentTimeMillis()) ?: return@withLock false

            var attemptedVoucher: Voucher? = null
            try {
                dao.updateSmsMessage(sms.copy(status = "PROCESSING"))
                log("INFO", "بدء معالجة الرسالة من: ${sms.sender}")

                // 1. Security Check: Validate sender ID (Ignore regular mobile numbers starting with 7 and 9 digits)
                val isRegularMobile = sms.sender.startsWith("7") && sms.sender.length == 9
                if (isRegularMobile) {
                    log("WARNING", "تم تجاهل الرسالة. المرسل رقم شخصي: ${sms.sender}")
                    dao.updateSmsMessage(sms.copy(status = "REJECTED", errorMessage = "Ignored personal mobile number"))
                    return@withLock true
                }

                // Fetch valid categories to ensure we only match exact category amounts
                val validCategories = dao.getAllCategoriesList().mapNotNull { it.name.toIntOrNull() }

                // 2. Parse SMS
                val parsedResult = com.example.core.parser.SmsParserEngine.parse(sms.body, sms.timestamp, validCategories)
                
                if (!parsedResult.isValid) {
                    log("WARNING", "فشل تحليل الرسالة - السبب: ${parsedResult.reason}")
                    dao.updateSmsMessage(sms.copy(status = "REJECTED", errorMessage = parsedResult.reason))
                    return@withLock true
                }

                val amountStr = parsedResult.amount.toString()
                
                // Helper to record failed transaction
                suspend fun recordFailedTransaction(errorReason: String, voucherNum: String = "N/A", catName: String = amountStr, catId: Long? = null) {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    
                    database.withTransaction {
                        dao.insertTransaction(
                            Transaction(
                                customerPhone = parsedResult.phoneNumber ?: "N/A",
                                amount = parsedResult.amount?.toDouble() ?: 0.0,
                                voucherNumber = voucherNum,
                                categoryName = catName,
                                smsStatus = if (attemptedVoucher != null) "UNKNOWN" else "FAILED",
                                transactionDate = date,
                                transactionTime = time,
                                originalSms = sms.body
                            )
                        )
                        
                        if (catId != null) {
                            var stat = dao.getDailyStatistic(date, catId)
                            if (stat == null) {
                                stat = com.example.data.local.entity.DailyStatistic(date = date, categoryId = catId)
                            }
                            dao.insertDailyStatistic(
                                stat.copy(
                                    totalFailedSMS = stat.totalFailedSMS + 1,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    triggerSyncWorker()
                }

                // 3. Match amount to Voucher Category
                val category = dao.getCategoryByName(amountStr)
                if (category == null) {
                    log("WARNING", "فشل المعاملة. الفئة غير موجودة للمبلغ: $amountStr")
                    dao.updateSmsMessage(sms.copy(status = "FAILED", errorMessage = "Category not found"))
                    recordFailedTransaction("Category not found")
                    return@withLock true
                }

                // 4. Find FIRST Available voucher (status = 0)
                val voucher = dao.getFirstAvailableVoucher(category.id)
                if (voucher == null) {
                    log("ERROR", "لا توجد قسائم متاحة للفئة: $amountStr")
                    dao.updateSmsMessage(sms.copy(status = "FAILED", errorMessage = "No available vouchers"))
                    recordFailedTransaction("No available vouchers", catName = category.name, catId = category.id)
                    return@withLock true
                }

                database.withTransaction {
                    check(dao.reserveVoucher(voucher.id) == 1) { "الكرت لم يعد متاحاً" }
                }
                attemptedVoucher = voucher
                log("INFO", "تم العثور على قسيمة (${voucher.voucherNumber}). جاري الإرسال...")

                // 5. Send SMS
                val smsText = voucherMessage(voucher)
                val smsSender = SmsSender(context)
                val isSent = smsSender.sendSms(parsedResult.phoneNumber!!, smsText)

                if (isSent) {
                    val sentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    val sentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    
                    val transaction = Transaction(
                        customerPhone = parsedResult.phoneNumber!!,
                        amount = parsedResult.amount!!.toDouble(),
                        voucherNumber = voucher.voucherNumber,
                        categoryName = category.name,
                        smsStatus = "SENT",
                        transactionDate = sentDate,
                        transactionTime = sentTime,
                        originalSms = sms.body
                    )

                    // 6. Update voucher status and log transaction ONLY after success
                    finalizeTransactionAtomically(
                        sms = sms,
                        voucher = voucher.copy(
                            status = 1,
                            customerPhone = parsedResult.phoneNumber,
                            sentDate = sentDate,
                            sentTime = sentTime
                        ),
                        transaction = transaction,
                        categoryId = category.id
                    )

                    // Check if they hit a multiple of threshold purchases
                    try {
                        val settings = dao.getSettingsSync() ?: com.example.data.local.entity.Settings()
                        if (settings.giftEnabled) {
                            val purchaseCount = dao.getSuccessfulPurchasesCount(parsedResult.phoneNumber!!)
                            val threshold = settings.giftThreshold.takeIf { it > 0 } ?: 10
                            if (purchaseCount > 0 && purchaseCount % threshold == 0) {
                                sendLoyaltyGift(parsedResult.phoneNumber!!, settings)
                            }
                        }
                    } catch (giftEx: Exception) {
                        log("ERROR", "خطأ أثناء معالجة نظام الهدايا: ${giftEx.message}")
                    }
                } else {
                    log("ERROR", "فشل إرسال رسالة القسيمة إلى: ${parsedResult.phoneNumber}")
                    recordFailedTransaction("SMS sending failed", voucherNum = voucher.voucherNumber, catName = category.name, catId = category.id)
                    database.withTransaction {
                        dao.updateVoucher(voucher.copy(status = 2))
                        dao.updateSmsMessage(sms.copy(status = "REVIEW", errorMessage = "لم يتأكد إرسال SMS. الكرت محجوز للمراجعة ولا يعاد بيعه"))
                    }
                    triggerSyncWorker()
                }
                
                true
            } catch (e: Exception) {
                val attempted = attemptedVoucher
                if (attempted != null) {
                    database.withTransaction {
                        val current = dao.findVoucherByUsername(attempted.voucherNumber)
                        if (current?.status == 3) dao.updateVoucher(current.copy(status = 2))
                        dao.updateSmsMessage(sms.copy(status = "REVIEW", errorMessage = "نتيجة الإرسال غير محسومة. راجع الكرت ${attempted.voucherNumber}"))
                    }
                    log("ERROR", "تحتاج مراجعة: ${attempted.voucherNumber} — ${e.message}")
                    triggerSyncWorker()
                } else handleFailure(sms, e.message ?: "خطأ غير معروف")
                true
            }
        }
    }

    // 2. Atomic Processing
    suspend fun finalizeTransactionAtomically(
        sms: SmsMessage,
        voucher: Voucher,
        transaction: Transaction,
        categoryId: Long
    ) {
        database.withTransaction {
            dao.updateVoucher(voucher.copy(status = 1))
            dao.insertTransaction(transaction)
            dao.updateSmsMessage(sms.copy(status = "PROCESSED"))
            dao.insertLog(AppLog(type = "SUCCESS", message = "تم إرسال القسيمة بنجاح وإتمام المعاملة: ${sms.sender}"))
            
            // Update precomputed statistics
            val date = transaction.transactionDate
            var stat = dao.getDailyStatistic(date, categoryId)
            if (stat == null) {
                stat = com.example.data.local.entity.DailyStatistic(date = date, categoryId = categoryId)
            }
            dao.insertDailyStatistic(
                stat.copy(
                    totalSalesCount = stat.totalSalesCount + 1,
                    totalRevenue = stat.totalRevenue + transaction.amount,
                    totalSentVouchers = stat.totalSentVouchers + 1,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
        triggerSyncWorker()
    }

    private suspend fun triggerSyncWorker() {
        com.example.worker.SyncWorker.enqueue(context)
    }

    // 3. Duplicate Protection
    suspend fun receiveSms(sender: String, body: String, timestamp: Long) {
        val fingerprint = "$sender|$body|$timestamp".hashCode().toString()
        
        val sms = SmsMessage(
            sender = sender,
            body = body,
            timestamp = timestamp,
            fingerprint = fingerprint,
            status = "PENDING"
        )
        
        val id = dao.insertSmsMessage(sms)
        if (id == -1L) {
            log("WARNING", "تم تجاهل رسالة مكررة من: $sender")
        } else {
            log("INFO", "تم استلام رسالة جديدة من: $sender وإضافتها للطابور")
        }
    }

    // 4. Processing Log
    suspend fun log(type: String, message: String) {
        dao.insertLog(AppLog(type = type, message = message))
    }

    // 5. Retry Queue
    suspend fun handleFailure(sms: SmsMessage, error: String) {
        log("ERROR", "فشل معالجة الرسالة: $error")
        if (sms.retryCount < 3) {
            val nextRetry = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes
            dao.updateSmsMessage(
                sms.copy(
                    status = "RETRYING",
                    retryCount = sms.retryCount + 1,
                    errorMessage = error,
                    nextRetryTime = nextRetry
                )
            )
            log("INFO", "تمت إضافة الرسالة لصف الإعادة. المحاولة ${sms.retryCount + 1}/3")
        } else {
            dao.updateSmsMessage(
                sms.copy(
                    status = "FAILED",
                    errorMessage = "تجاوز الحد الأقصى للمحاولات: $error"
                )
            )
            log("ERROR", "فشل نهائي للرسالة من: ${sms.sender}")
        }
    }

    // 6. Crash Recovery
    suspend fun recoverUnfinishedTransactions() {
        mutex.withLock {
            database.withTransaction {
                for (voucher in dao.getAllVouchersForBackup().filter { it.status == 3 }) {
                    dao.updateVoucher(voucher.copy(status = 2))
                }
                for (sms in dao.getProcessingSms()) {
                    dao.updateSmsMessage(sms.copy(status = "REVIEW", errorMessage = "انقطع التطبيق أثناء المعالجة. راجع العملية قبل أي إعادة إرسال"))
                }
            }
        }
    }

    private fun voucherMessage(voucher: Voucher): String = buildString {
        append("رقم كرت شبكة هران نت هو: ${voucher.voucherNumber}")
        if (voucher.password.isNotEmpty() && voucher.password != voucher.voucherNumber) append("\nكلمة المرور: ${voucher.password}")
    }

    // 7. Loyalty Gift System for Nth Purchase
    private suspend fun sendLoyaltyGift(phone: String, settings: com.example.data.local.entity.Settings) {
        log("INFO", "العميل $phone أكمل ${dao.getSuccessfulPurchasesCount(phone)} مشتريات! جاري إعداد كرت الهدية المجاني...")
        
        var category: VoucherCategory? = null
        if (settings.giftCategoryPreference != "MOST_BOUGHT") {
            category = dao.getCategoryByName(settings.giftCategoryPreference)
        }
        
        if (category == null) {
            val categoriesBought = dao.getCategoriesBoughtByUser(phone)
            val mostFrequentName = categoriesBought.firstOrNull()
            if (mostFrequentName != null) {
                category = dao.getCategoryByName(mostFrequentName)
            }
        }
        
        var giftVoucher: Voucher? = null
        if (category != null) {
            giftVoucher = dao.getFirstAvailableVoucher(category.id)
        }
        
        // Fallback 1: Look in other categories bought by this user
        if (giftVoucher == null) {
            val categoriesBought = dao.getCategoriesBoughtByUser(phone)
            for (catName in categoriesBought) {
                val cat = dao.getCategoryByName(catName)
                if (cat != null) {
                    val v = dao.getFirstAvailableVoucher(cat.id)
                    if (v != null) {
                        giftVoucher = v
                        category = cat
                        break
                    }
                }
            }
        }
        
        // Fallback 2: Look in any category in the system
        if (giftVoucher == null) {
            val allCats = dao.getAllCategoriesList()
            for (cat in allCats) {
                val v = dao.getFirstAvailableVoucher(cat.id)
                if (v != null) {
                    giftVoucher = v
                    category = cat
                    break
                }
            }
        }
        
        if (giftVoucher == null || category == null) {
            log("WARNING", "العميل $phone يستحق كرت هدية مجاني لولائه، ولكن لا تتوفر أي كروت حالياً في المخزون لإهدائها!")
            return
        }
        
        log("INFO", "تم العثور على كرت هدية من الفئة المفضلة (${category.name}) والرقم التسلسلي (${giftVoucher.voucherNumber}). جاري إرساله...")
        
        val reservedGift = giftVoucher
        check(dao.reserveVoucher(reservedGift.id) == 1) { "كرت الهدية لم يعد متاحاً" }
        // Wait for 5 seconds before sending the gift SMS to prevent the network from dropping it due to rapid consecutive messages to the same number
        kotlinx.coroutines.delay(5000)

        // Custom Arabic text with highly polished customer relationship tone
        val giftSmsText = "شكراً لولائك. هذه هدية من شبكة هران نت.\n" + voucherMessage(reservedGift)
        
        val smsSender = SmsSender(context)
        val isSent = smsSender.sendSms(phone, giftSmsText)
        
        if (isSent) {
            val sentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val sentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            
            val giftTransaction = Transaction(
                customerPhone = phone,
                amount = 0.0,
                voucherNumber = giftVoucher.voucherNumber,
                categoryName = "${category.name} (هدية)",
                smsStatus = "GIFT_SENT",
                transactionDate = sentDate,
                transactionTime = sentTime,
                originalSms = "LOYALTY_GIFT_${settings.giftThreshold}_PURCHASES"
            )
            
            database.withTransaction {
                dao.updateVoucher(
                    giftVoucher.copy(
                        status = 1,
                        customerPhone = phone,
                        sentDate = sentDate,
                        sentTime = sentTime
                    )
                )
                dao.insertTransaction(giftTransaction)
                dao.insertLog(AppLog(type = "SUCCESS", message = "تم إرسال كرت الهدية المجاني بنجاح للعميل المميز $phone (الفئة: ${category.name})"))
                
                // Update stats for the sent gift
                var stat = dao.getDailyStatistic(sentDate, category.id)
                if (stat == null) {
                    stat = com.example.data.local.entity.DailyStatistic(date = sentDate, categoryId = category.id)
                }
                dao.insertDailyStatistic(
                    stat.copy(
                        totalSentVouchers = stat.totalSentVouchers + 1,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
            triggerSyncWorker()
        } else {
            dao.updateVoucher(reservedGift.copy(status = 2))
            log("ERROR", "لم يتأكد إرسال الهدية للعميل $phone. الكرت ${reservedGift.voucherNumber} محجوز للمراجعة")
            triggerSyncWorker()
        }
    }
}

