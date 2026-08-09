package com.example.data.repository

import com.example.data.local.dao.AppDao
import com.example.data.local.entity.Settings
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val appDao: AppDao
) {
    fun getSettings(): Flow<Settings?> = appDao.getSettings()

    suspend fun getSettingsSync(): Settings? = appDao.getSettingsSync()

    suspend fun updateSettings(settings: Settings) {
        if (appDao.getSettingsSync() == null) {
            appDao.insertSettings(settings)
        } else {
            appDao.updateSettings(settings)
        }
    }

    fun getAvailableVouchersCount(): Flow<Int> = appDao.getAvailableVouchersCount()
    
    // Use precomputed stats
    fun getTodaySalesCount(date: String): Flow<Int?> = appDao.getTodaySalesCount(date)
    fun getTodayRevenue(date: String): Flow<Double?> = appDao.getTodayRevenue(date)
    fun getTotalSentVouchers(): Flow<Int?> = appDao.getTotalSentVouchers()
    fun getTodayFailedSalesCountFromStats(date: String): Flow<Int?> = appDao.getTodayFailedSalesCountFromStats(date)
    fun getRecentTransactions(limit: Int) = appDao.getRecentTransactionsFlow(limit)
    fun getAllTransactions() = appDao.getAllTransactionsFlow()
    
    fun getPendingSyncCount(): Flow<Int> = kotlinx.coroutines.flow.combine(
        appDao.getUnsyncedTransactionsCount(),
        appDao.getUnsyncedDailyStatisticsCount(),
        appDao.getUnsyncedLogsCount(),
        appDao.getUnsyncedCategoriesCount()
    ) { t, d, l, c -> t + d + l + c }


    // Category Management
    suspend fun insertCategory(category: com.example.data.local.entity.VoucherCategory): Long = appDao.insertCategory(category)
    suspend fun updateCategory(category: com.example.data.local.entity.VoucherCategory) = appDao.updateCategory(category)
    suspend fun deleteCategory(category: com.example.data.local.entity.VoucherCategory) = appDao.deleteCategory(category)
    suspend fun getVouchersCountInCategory(categoryId: Long): Int = appDao.getVouchersCountInCategory(categoryId)
    fun getCategoriesWithStats(): Flow<List<com.example.data.local.model.CategoryWithStats>> = appDao.getCategoriesWithStats()

    // Voucher Management
    suspend fun insertVouchers(vouchers: List<com.example.data.local.entity.Voucher>): List<Long> = appDao.insertVouchers(vouchers)
    suspend fun deleteVoucher(voucher: com.example.data.local.entity.Voucher) = appDao.deleteVoucher(voucher)
    suspend fun updateVoucher(voucher: com.example.data.local.entity.Voucher) = appDao.updateVoucher(voucher)
    fun getVouchersByCategory(categoryId: Long): Flow<List<com.example.data.local.entity.Voucher>> = appDao.getVouchersByCategory(categoryId)
    fun searchVouchers(categoryId: Long, query: String): Flow<List<com.example.data.local.entity.Voucher>> = appDao.searchVouchers(categoryId, query)
    fun getVouchersByStatus(categoryId: Long, status: Int): Flow<List<com.example.data.local.entity.Voucher>> = appDao.getVouchersByStatus(categoryId, status)
    suspend fun getVouchersListByCategory(categoryId: Long): List<com.example.data.local.entity.Voucher> = appDao.getVouchersListByCategory(categoryId)

    suspend fun getSuccessfulPurchasesCount(phone: String): Int = appDao.getSuccessfulPurchasesCount(phone)
    suspend fun getCategoriesBoughtByUser(phone: String): List<String> = appDao.getCategoriesBoughtByUser(phone)
}
