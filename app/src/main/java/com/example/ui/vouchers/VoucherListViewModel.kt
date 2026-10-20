package com.example.ui.vouchers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Voucher
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.InputStream

class VoucherListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val _vouchers = MutableStateFlow<List<Voucher>>(emptyList())
    val vouchers: StateFlow<List<Voucher>> = _vouchers

    var categoryId: Long = -1

    init {
        val dao = AppDatabase.getDatabase(application).appDao()
        repository = AppRepository(dao)
    }

    fun loadVouchers(catId: Long) {
        categoryId = catId
        viewModelScope.launch {
            repository.getVouchersByCategory(categoryId).collectLatest { list ->
                _vouchers.value = list
            }
        }
    }

    fun searchVouchers(query: String) {
        if (categoryId == -1L) return
        viewModelScope.launch {
            if (query.isBlank()) {
                repository.getVouchersByCategory(categoryId).collectLatest { list ->
                    _vouchers.value = list
                }
            } else {
                repository.searchVouchers(categoryId, query).collectLatest { list ->
                    _vouchers.value = list
                }
            }
        }
    }

    fun filterVouchers(status: Int) {
        if (categoryId == -1L) return
        viewModelScope.launch {
            if (status == -1) {
                repository.getVouchersByCategory(categoryId).collectLatest { list ->
                    _vouchers.value = list
                }
            } else {
                repository.getVouchersByStatus(categoryId, status).collectLatest { list ->
                    _vouchers.value = list
                }
            }
        }
    }

    suspend fun importVouchersFromList(lines: List<String>): ImportResult {
        check(!com.example.integration.SabaLink(getApplication()).configured) { "إضافة المخزون تتم من SABA بعد ربط الهاتف" }
        var validCount = 0
        var invalidCount = 0
        val vouchersToInsert = mutableListOf<Voucher>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                invalidCount++
                continue
            }
            vouchersToInsert.add(
                Voucher(
                    categoryId = categoryId,
                    voucherNumber = trimmed,
                    status = 0
                )
            )
            validCount++
        }

        val insertedIds = repository.insertVouchers(vouchersToInsert)
        val addedCount = insertedIds.count { it != -1L }
        val duplicateCount = validCount - addedCount

        return ImportResult(
            total = lines.size,
            added = addedCount,
            duplicates = duplicateCount,
            invalid = invalidCount
        )
    }

    suspend fun importVouchers(inputStream: InputStream): ImportResult {
        val lines = inputStream.bufferedReader().useLines { it.toList() }
        return importVouchersFromList(lines)
    }

    fun updateVoucherStatus(voucher: Voucher, newStatus: Int, phone: String? = null, date: String? = null, time: String? = null) {
        viewModelScope.launch {
            if (com.example.integration.SabaLink(getApplication()).configured && newStatus == 0) return@launch
            repository.updateVoucher(
                voucher.copy(
                    status = newStatus,
                    customerPhone = phone,
                    sentDate = date,
                    sentTime = time
                )
            )
        }
    }

    fun deleteVoucher(voucher: Voucher) {
        viewModelScope.launch {
            if (com.example.integration.SabaLink(getApplication()).configured) return@launch
            repository.deleteVoucher(voucher)
        }
    }
    
    suspend fun getVouchersList(): List<Voucher> {
        return repository.getVouchersListByCategory(categoryId)
    }
}

data class ImportResult(
    val total: Int,
    val added: Int,
    val duplicates: Int,
    val invalid: Int
)

