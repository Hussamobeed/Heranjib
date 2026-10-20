package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AppLog
import com.example.data.local.entity.Settings
import com.example.data.local.entity.SmsMessage
import com.example.data.local.entity.Transaction
import com.example.data.local.entity.Voucher
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM vouchers WHERE status != syncedStatus ORDER BY id LIMIT :limit")
    suspend fun getVouchersForSync(limit: Int): List<Voucher>
    @Query("UPDATE vouchers SET syncedStatus = :status WHERE id = :id AND status = :status")
    suspend fun ackVoucher(id: Long, status: Int)
    @Query("UPDATE vouchers SET remoteId = :remoteId WHERE id = :id AND remoteId IS NULL")
    suspend fun setRemoteId(id: Long, remoteId: String)
    @Query("SELECT * FROM vouchers WHERE voucherNumber = :username LIMIT 1")
    suspend fun findVoucherByUsername(username: String): Voucher?
    @Query("SELECT * FROM transactions WHERE sabaSynced = 0 ORDER BY id LIMIT :limit")
    suspend fun getTransactionsForSync(limit: Int): List<com.example.data.local.entity.Transaction>
    @Query("UPDATE transactions SET sabaSynced = 1 WHERE id = :id")
    suspend fun ackTransaction(id: Long)
    @Query("SELECT * FROM app_logs WHERE sabaSynced = 0 ORDER BY id LIMIT :limit")
    suspend fun getLogsForSaba(limit: Int): List<com.example.data.local.entity.AppLog>
    @Query("UPDATE app_logs SET sabaSynced = 1 WHERE id = :id")
    suspend fun ackSabaLog(id: Long)
    @Query("UPDATE vouchers SET status = 3 WHERE id = :id AND status = 0")
    suspend fun reserveVoucher(id: Long): Int

    @Query("SELECT * FROM settings LIMIT 1")
    fun getSettings(): Flow<Settings?>

    @Query("SELECT * FROM settings LIMIT 1")
    suspend fun getSettingsSync(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: Settings)

    @Update
    suspend fun updateSettings(settings: Settings)

    @Query("SELECT COUNT(*) FROM vouchers WHERE status = 0")
    fun getAvailableVouchersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsStatus = 'SENT' AND transactionDate = :date")
    fun getTodaySuccessfulSalesCount(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsStatus = 'FAILED' AND transactionDate = :date")
    fun getTodayFailedSalesCount(date: String): Flow<Int>

    // DailyStatistic Methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyStatistic(statistic: com.example.data.local.entity.DailyStatistic)

    @Query("SELECT * FROM daily_statistics WHERE date = :date AND categoryId = :categoryId LIMIT 1")
    suspend fun getDailyStatistic(date: String, categoryId: Long): com.example.data.local.entity.DailyStatistic?

    @Query("SELECT SUM(totalSalesCount) FROM daily_statistics WHERE date = :date")
    fun getTodaySalesCount(date: String): Flow<Int?>

    @Query("SELECT SUM(totalRevenue) FROM daily_statistics WHERE date = :date")
    fun getTodayRevenue(date: String): Flow<Double?>

    @Query("SELECT SUM(totalSentVouchers) FROM daily_statistics")
    fun getTotalSentVouchers(): Flow<Int?>

    @Query("SELECT SUM(totalFailedSMS) FROM daily_statistics WHERE date = :date")
    fun getTodayFailedSalesCountFromStats(date: String): Flow<Int?>

    @Query("SELECT * FROM transactions ORDER BY id DESC LIMIT :limit")
    fun getRecentTransactionsFlow(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    // Synchronization Methods
    @Query("SELECT COUNT(*) FROM transactions WHERE isSynced = 0")
    fun getUnsyncedTransactionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM daily_statistics WHERE isSynced = 0")
    fun getUnsyncedDailyStatisticsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM app_logs WHERE isSynced = 0")
    fun getUnsyncedLogsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM voucher_categories WHERE isSynced = 0")
    fun getUnsyncedCategoriesCount(): Flow<Int>
    
    @Query("SELECT * FROM transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<com.example.data.local.entity.Transaction>

    @Update
    suspend fun updateTransaction(transaction: com.example.data.local.entity.Transaction)

    @Query("SELECT * FROM daily_statistics WHERE isSynced = 0")
    suspend fun getUnsyncedDailyStatistics(): List<com.example.data.local.entity.DailyStatistic>

    @Update
    suspend fun updateDailyStatistic(statistic: com.example.data.local.entity.DailyStatistic)

    @Query("SELECT * FROM app_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<com.example.data.local.entity.AppLog>

    @Update
    suspend fun updateLog(log: com.example.data.local.entity.AppLog)

    @Query("SELECT * FROM voucher_categories WHERE isSynced = 0")
    suspend fun getUnsyncedCategories(): List<com.example.data.local.entity.VoucherCategory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsMessage(smsMessage: SmsMessage): Long

    @Update
    suspend fun updateSmsMessage(smsMessage: SmsMessage)

    @Query("SELECT * FROM sms_messages WHERE status IN ('PENDING', 'RETRYING') AND nextRetryTime <= :currentTime ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPendingSms(currentTime: Long): SmsMessage?

    @Query("SELECT * FROM sms_messages WHERE status = 'PROCESSING'")
    suspend fun getProcessingSms(): List<SmsMessage>

    // AppLog Methods
    @Insert
    suspend fun insertLog(log: AppLog)

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppLog>>
    
    // Transaction & Voucher Methods
    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long
    
    @Update
    suspend fun updateVoucher(voucher: Voucher)
    // Category & Voucher Management
    @Insert
    suspend fun insertCategory(category: com.example.data.local.entity.VoucherCategory): Long

    @Update
    suspend fun updateCategory(category: com.example.data.local.entity.VoucherCategory)

    @Query("""
        SELECT 
            c.id AS id, c.name AS name, c.createdDate AS createdDate, c.isSynced AS isSynced,
            COUNT(v.id) AS totalCount,
            SUM(CASE WHEN v.status = 0 THEN 1 ELSE 0 END) AS availableCount,
            SUM(CASE WHEN v.status = 1 THEN 1 ELSE 0 END) AS sentCount
        FROM voucher_categories c
        LEFT JOIN vouchers v ON c.id = v.categoryId
        GROUP BY c.id
        ORDER BY c.createdDate DESC
    """)
    suspend fun getCategoriesForExport(): List<com.example.data.local.model.CategoryWithStats>

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getTransactionsForExport(): List<com.example.data.local.entity.Transaction>

    @Query("SELECT * FROM daily_statistics ORDER BY date DESC")
    suspend fun getDailyStatisticsForExport(): List<com.example.data.local.entity.DailyStatistic>

    @Query("SELECT * FROM app_logs ORDER BY id DESC")
    suspend fun getLogsForExport(): List<com.example.data.local.entity.AppLog>

    @androidx.room.Delete
    suspend fun deleteCategory(category: com.example.data.local.entity.VoucherCategory)

    @Query("SELECT COUNT(*) FROM vouchers WHERE categoryId = :categoryId")
    suspend fun getVouchersCountInCategory(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVouchers(vouchers: List<Voucher>): List<Long>

    @androidx.room.Delete
    suspend fun deleteVoucher(voucher: Voucher)

    @Query("SELECT * FROM vouchers WHERE categoryId = :categoryId ORDER BY status ASC, importedAt DESC")
    fun getVouchersByCategory(categoryId: Long): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers WHERE categoryId = :categoryId AND (voucherNumber LIKE '%' || :query || '%' OR customerPhone LIKE '%' || :query || '%') ORDER BY status ASC, importedAt DESC")
    fun searchVouchers(categoryId: Long, query: String): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers WHERE categoryId = :categoryId AND status = :status")
    fun getVouchersByStatus(categoryId: Long, status: Int): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers WHERE categoryId = :categoryId")
    suspend fun getVouchersListByCategory(categoryId: Long): List<Voucher>

    @Query("SELECT * FROM voucher_categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): com.example.data.local.entity.VoucherCategory?

    @Query("SELECT * FROM voucher_categories")
    suspend fun getAllCategoriesList(): List<com.example.data.local.entity.VoucherCategory>

    @Query("SELECT * FROM voucher_categories")
    fun getAllCategoriesFlow(): kotlinx.coroutines.flow.Flow<List<com.example.data.local.entity.VoucherCategory>>

    @Query("SELECT * FROM vouchers WHERE categoryId = :categoryId AND status = 0 ORDER BY importedAt ASC LIMIT 1")
    suspend fun getFirstAvailableVoucher(categoryId: Long): Voucher?

    @androidx.room.Transaction
    @Query("""
        SELECT 
            c.id AS id, c.name AS name, c.createdDate AS createdDate, c.isSynced AS isSynced,
            COUNT(v.id) AS totalCount,
            SUM(CASE WHEN v.status = 0 THEN 1 ELSE 0 END) AS availableCount,
            SUM(CASE WHEN v.status = 1 THEN 1 ELSE 0 END) AS sentCount
        FROM voucher_categories c
        LEFT JOIN vouchers v ON c.id = v.categoryId
        GROUP BY c.id
        ORDER BY c.createdDate DESC
    """)
    fun getCategoriesWithStats(): Flow<List<com.example.data.local.model.CategoryWithStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<com.example.data.local.entity.VoucherCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<com.example.data.local.entity.Transaction>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsMessages(messages: List<com.example.data.local.entity.SmsMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<com.example.data.local.entity.AppLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyStatistics(statistics: List<com.example.data.local.entity.DailyStatistic>)

    @Query("DELETE FROM voucher_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM vouchers")
    suspend fun clearVouchers()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Query("DELETE FROM app_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM daily_statistics")
    suspend fun clearDailyStatistics()

    @Query("DELETE FROM sms_messages")
    suspend fun clearSmsMessages()

    @Query("SELECT * FROM vouchers")
    suspend fun getAllVouchersForBackup(): List<Voucher>

    @Query("SELECT * FROM sms_messages")
    suspend fun getAllSmsMessagesForBackup(): List<com.example.data.local.entity.SmsMessage>

    @Query("SELECT COUNT(*) FROM transactions WHERE customerPhone = :phone AND smsStatus = 'SENT' AND amount > 0")
    suspend fun getSuccessfulPurchasesCount(phone: String): Int

    @Query("SELECT categoryName FROM transactions WHERE customerPhone = :phone AND smsStatus = 'SENT' AND amount > 0 GROUP BY categoryName ORDER BY COUNT(*) DESC")
    suspend fun getCategoriesBoughtByUser(phone: String): List<String>
    @Query("SELECT customerPhone as phone, COUNT(*) as count FROM transactions WHERE customerPhone IS NOT NULL AND customerPhone != '' AND smsStatus = 'SENT' AND amount > 0 GROUP BY customerPhone HAVING COUNT(*) > 0")
    suspend fun getAllCustomerPurchaseCounts(): List<com.example.data.local.model.CustomerPurchaseCount>
}

