package com.example.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integration.SabaLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CardDevice(val id: String, val name: String)
data class CardSearchState(
    val loading: Boolean = false, val devices: List<CardDevice> = emptyList(),
    val devicesLoaded: Boolean = false, val result: JSONObject? = null, val error: String? = null
)

class CardSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val link = SabaLink(application)
    private val mutable = MutableStateFlow(CardSearchState())
    val state = mutable.asStateFlow()
    init { loadDevices() }

    private fun loadDevices() {
        if (!link.configured) {
            mutable.value = mutable.value.copy(result = null, error = "اربط الهاتف بـ SABA من الإعدادات أولاً.", devicesLoaded = true)
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { link.request("card-devices", JSONObject()) }
                val list = response.optJSONArray("devices")
                val devices = (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }.map { CardDevice(it.getString("id"), it.getString("name")) }
                mutable.value = mutable.value.copy(devices = devices, devicesLoaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Saved reports remain searchable even if loading the device list fails.
                mutable.value = mutable.value.copy(devicesLoaded = true)
            }
        }
    }

    fun search(raw: String, deviceId: String) {
        if (mutable.value.loading) return
        val username = raw.trim().map { when (it) {
            in '٠'..'٩' -> '0' + (it - '٠')
            in '۰'..'۹' -> '0' + (it - '۰')
            else -> it
        }}.joinToString("")
        if (username.isEmpty() || username.length > 64 || username.any { it.code < 32 || it.code == 127 }) {
            mutable.value = mutable.value.copy(result = null, error = "أدخل رقم الكرت أو اسم المستخدم، حتى 64 حرفاً.")
            return
        }
        if (!link.configured) {
            mutable.value = mutable.value.copy(result = null, error = "اربط الهاتف بـ SABA من الإعدادات أولاً.")
            return
        }
        mutable.value = mutable.value.copy(loading = true, result = null, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { link.request("card-search", JSONObject().put("username", username).put("connectionId", deviceId)) }
                mutable.value = mutable.value.copy(loading = false, result = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = error.message ?: "تعذر البحث. أعد المحاولة.")
            }
        }
    }
}
