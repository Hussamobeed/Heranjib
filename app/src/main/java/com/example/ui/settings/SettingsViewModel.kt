package com.example.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Settings
import com.example.data.local.entity.Voucher
import com.example.data.local.entity.VoucherCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).appDao()

    private val _settings = MutableStateFlow<Settings?>(null)
    val settings: StateFlow<Settings?> = _settings

    private val _fetchStatus = MutableSharedFlow<String>()
    val fetchStatus: SharedFlow<String> = _fetchStatus

    private val _categories = MutableStateFlow<List<VoucherCategory>>(emptyList())
    val categories: StateFlow<List<VoucherCategory>> = _categories

    init {
        viewModelScope.launch {
            dao.getSettings().collect {
                _settings.value = it ?: Settings()
            }
        }
        viewModelScope.launch {
            dao.getAllCategoriesFlow().collect {
                _categories.value = it
            }
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            val existing = dao.getSettingsSync()
            val targetId = if (existing != null && existing.id > 0) existing.id else 1
            dao.insertSettings(settings.copy(id = targetId))
        }
    }

}
