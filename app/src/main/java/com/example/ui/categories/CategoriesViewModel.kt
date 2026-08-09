package com.example.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.VoucherCategory
import com.example.data.local.model.CategoryWithStats
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    val categoriesWithStats: StateFlow<List<CategoryWithStats>>

    init {
        val dao = AppDatabase.getDatabase(application).appDao()
        repository = AppRepository(dao)

        categoriesWithStats = repository.getCategoriesWithStats().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(
                VoucherCategory(
                    name = name,
                    createdDate = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateCategory(category: VoucherCategory) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    suspend fun deleteCategoryIfEmpty(category: VoucherCategory): Boolean {
        val count = repository.getVouchersCountInCategory(category.id)
        if (count == 0) {
            repository.deleteCategory(category)
            return true
        }
        return false
    }
}
