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
            if (settings.id == 0) {
                dao.insertSettings(settings)
            } else {
                dao.updateSettings(settings)
            }
        }
    }

    fun fetchUsersFromLink(link: String) {
        viewModelScope.launch {
            _fetchStatus.emit("جاري الجلب...")
            withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(link)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    
                    // Allow redirect (Apps Script often redirects to googleusercontent)
                    connection.instanceFollowRedirects = true

                    var responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as HttpURLConnection
                        responseCode = connection.responseCode
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = InputStreamReader(connection.inputStream)
                        val responseText = reader.readText()
                        reader.close()

                        val jsonArray = JSONArray(responseText)
                        var addedCount = 0

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val voucherNumber = obj.optString("username").trim()
                            val categoryStr = obj.optString("category").trim()

                            if (voucherNumber.isNotEmpty() && categoryStr.isNotEmpty()) {
                                // Find or create category
                                var category = dao.getCategoryByName(categoryStr)
                                if (category == null) {
                                    val newCategory = VoucherCategory(name = categoryStr, createdDate = System.currentTimeMillis())
                                    val catId = dao.insertCategory(newCategory)
                                    category = newCategory.copy(id = catId)
                                }

                                val voucher = Voucher(
                                    categoryId = category.id,
                                    voucherNumber = voucherNumber,
                                    status = 0 // Available
                                )
                                
                                try {
                                    dao.insertVouchers(listOf(voucher))
                                    addedCount++
                                } catch (e: Exception) {
                                    // Ignore unique constraint violation
                                }
                            }
                        }
                        _fetchStatus.emit("تم إضافة $addedCount كرت بنجاح")
                    } else {
                        _fetchStatus.emit("فشل في الاتصال، كود: $responseCode")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _fetchStatus.emit("حدث خطأ: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
            }
        }
    }
}
