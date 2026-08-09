package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Settings
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    val settings: StateFlow<Settings?>
    
    val availableVouchersCount: StateFlow<Int>
    val todaySalesCount: StateFlow<Int>
    val todayRevenue: StateFlow<Double>
    val totalSentVouchers: StateFlow<Int>
    val todayFailedCount: StateFlow<Int>
    val pendingSyncCount: StateFlow<Int>
    val recentTransactions: StateFlow<List<com.example.data.local.entity.Transaction>>
    
    private val _topCustomersClosestToGift = MutableStateFlow(emptyList<CustomerLoyaltyStatus>())
    val topCustomersClosestToGift: StateFlow<List<CustomerLoyaltyStatus>> = _topCustomersClosestToGift

    init {
        val dao = AppDatabase.getDatabase(application).appDao()
        repository = AppRepository(dao)

        viewModelScope.launch {
            if (repository.getSettingsSync() == null) {
                repository.updateSettings(Settings())
            }
        }

        settings = repository.getSettings().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
        
        availableVouchersCount = repository.getAvailableVouchersCount().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )
        
        todaySalesCount = repository.getTodaySalesCount(todayDate)
            .map { it ?: 0 }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0
            )
            
        todayRevenue = repository.getTodayRevenue(todayDate)
            .map { it ?: 0.0 }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0.0
            )
            
        totalSentVouchers = repository.getTotalSentVouchers()
            .map { it ?: 0 }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0
            )
            
        todayFailedCount = repository.getTodayFailedSalesCountFromStats(todayDate)
            .map { it ?: 0 }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0
            )

        pendingSyncCount = dao.getUnsyncedTransactionsCount()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0
            )
            
        recentTransactions = repository.getRecentTransactions(3)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )
    }

    suspend fun checkCustomerLoyalty(phone: String): CustomerLoyaltyStatus {
        val settings = repository.getSettingsSync() ?: Settings()
        val enabled = settings.giftEnabled
        val threshold = settings.giftThreshold.takeIf { it > 0 } ?: 10

        val count = repository.getSuccessfulPurchasesCount(phone)
        val categories = repository.getCategoriesBoughtByUser(phone)
        val favoriteCategory = categories.firstOrNull()
        
        val progress = count % threshold
        val remaining = if (progress == 0 && count > 0) threshold else (threshold - progress)
        
        return CustomerLoyaltyStatus(
            phone = phone,
            successfulPurchases = count,
            giftEnabled = enabled,
            giftThreshold = threshold,
            remainingToNextGift = remaining,
            favoriteCategory = favoriteCategory,
            progress = progress
        )
    }

    fun setAutoSendEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = repository.getSettingsSync() ?: Settings(
                autoSend = false,
                smsTemplate = "",
                duplicateProtection = true
            )
            repository.updateSettings(currentSettings.copy(autoSend = enabled))
        }
    }
    
    fun loadTopCustomersClosestToGift() {
        viewModelScope.launch {
            val settings = repository.getSettingsSync() ?: Settings()
            val threshold = settings.giftThreshold.takeIf { it > 0 } ?: 10
            
            val allCounts = repository.getAllCustomerPurchaseCounts()
            
            val statuses = allCounts.map { customer ->
                val progress = customer.count % threshold
                val remaining = if (progress == 0 && customer.count > 0) threshold else (threshold - progress)
                
                CustomerLoyaltyStatus(
                    phone = customer.phone,
                    successfulPurchases = customer.count,
                    giftEnabled = settings.giftEnabled,
                    giftThreshold = threshold,
                    remainingToNextGift = remaining,
                    favoriteCategory = null, // don't load favorite category for all users to save time
                    progress = progress
                )
            }.sortedBy { it.remainingToNextGift }.take(10)
            
            _topCustomersClosestToGift.value = statuses
        }
    }
}

data class CustomerLoyaltyStatus(
    val phone: String,
    val successfulPurchases: Int,
    val giftEnabled: Boolean,
    val giftThreshold: Int,
    val remainingToNextGift: Int,
    val favoriteCategory: String?,
    val progress: Int
)
