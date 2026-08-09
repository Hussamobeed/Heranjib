package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val dao = database.appDao()

        try {
            val settings = dao.getSettingsSync()
            val webAppUrl = "https://script.google.com/macros/s/AKfycbw3VPS8m4SAq0W_LEH2NfpLxLnkmEWoG8aBpjrygf8goErQAv3B2ipJrYPvxbk6wj2tdg/exec"
            
            // Log start
            dao.insertLog(AppLog(type = "INFO", message = "Starting data sync..."))

            // Sync Transactions
            val transactions = dao.getUnsyncedTransactions()
            for (tx in transactions) {
                val payload = JSONObject().apply {
                    put("TransactionID", tx.id.toString())
                    put("Date", tx.transactionDate)
                    put("Time", tx.transactionTime)
                    put("CustomerPhone", tx.customerPhone)
                    put("Amount", tx.amount)
                    put("VoucherCategory", tx.categoryName)
                    put("VoucherNumber", tx.voucherNumber)
                    put("SMSStatus", tx.smsStatus)
                    put("OriginalSMS", tx.originalSms)
                }

                val success = sendData(webAppUrl, "transaction", payload)
                if (success) {
                    dao.updateTransaction(tx.copy(isSynced = true))
                }
            }

            // Sync Logs
            val logs = dao.getUnsyncedLogs()
            for (log in logs) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val date = Date(log.timestamp)
                
                val payload = JSONObject().apply {
                    put("Date", dateFormat.format(date))
                    put("Time", timeFormat.format(date))
                    put("Type", log.type)
                    put("Message", log.message)
                }

                val success = sendData(webAppUrl, "log", payload)
                if (success) {
                    dao.updateLog(log.copy(isSynced = true))
                }
            }
            
            // Note: Categories and Daily Statistics are synced via Transactions on the server side in Apps Script,
            // but we can mark them synced locally to clear the pending counts if they exist.
            val categories = dao.getUnsyncedCategories()
            for (cat in categories) {
                 dao.updateCategory(cat.copy(isSynced = true))
            }
            val stats = dao.getUnsyncedDailyStatistics()
            for (stat in stats) {
                 dao.updateDailyStatistic(stat.copy(isSynced = true))
            }
            
            // Update last sync time
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            settings?.let {
                dao.updateSettings(it.copy(lastSyncTime = currentTime))
            }
            
            dao.insertLog(AppLog(type = "INFO", message = "Data sync completed successfully."))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            dao.insertLog(AppLog(type = "ERROR", message = "Data sync failed: ${e.message}"))
            Result.retry()
        }
    }

    private fun sendData(webAppUrl: String, type: String, payload: JSONObject): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(webAppUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val requestBody = JSONObject().apply {
                put("type", type)
                put("payload", payload)
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            return responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            connection?.disconnect()
        }
    }
}
