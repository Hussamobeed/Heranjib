package com.example.core

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppLog
import com.example.data.local.entity.DailyStatistic
import com.example.data.local.entity.Settings
import com.example.data.local.entity.SmsMessage
import com.example.data.local.entity.Transaction
import com.example.data.local.entity.Voucher
import com.example.data.local.entity.VoucherCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CsvExportManager(private val context: Context, private val database: AppDatabase) {

    private fun escapeCsvField(field: Any?): String {
        if (field == null) return ""
        var str = field.toString()
        // Escape newlines and carriage returns to keep each CSV record strictly on a single line
        str = str.replace("\n", "\\n").replace("\r", "\\r")
        if (str.contains(",") || str.contains("\"")) {
            return "\"" + str.replace("\"", "\"\"") + "\""
        }
        return str
    }

    private fun unescapeCsvField(field: String): String {
        return field.replace("\\n", "\n").replace("\\r", "\r")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val currentField = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    currentField.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(unescapeCsvField(currentField.toString()))
                currentField.setLength(0)
            } else {
                currentField.append(c)
            }
            i++
        }
        result.add(unescapeCsvField(currentField.toString()))
        return result
    }

    suspend fun exportData(): File? = withContext(Dispatchers.IO) {
        try {
            val dao = database.appDao()
            val cacheDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
            
            // 1. Export Voucher Categories
            val catFile = File(cacheDir, "Voucher_Categories.csv")
            catFile.writeText("id,name,createdDate,isSynced\n")
            dao.getAllCategoriesList().forEach { cat ->
                catFile.appendText("${cat.id},${escapeCsvField(cat.name)},${cat.createdDate},${cat.isSynced}\n")
            }

            // 2. Export Vouchers
            val vouchersFile = File(cacheDir, "Vouchers.csv")
            vouchersFile.writeText("id,categoryId,voucherNumber,status,customerPhone,sentDate,sentTime,importedAt\n")
            dao.getAllVouchersForBackup().forEach { v ->
                vouchersFile.appendText("${v.id},${v.categoryId},${escapeCsvField(v.voucherNumber)},${v.status},${escapeCsvField(v.customerPhone)},${escapeCsvField(v.sentDate)},${escapeCsvField(v.sentTime)},${v.importedAt}\n")
            }

            // 3. Export Transactions
            val txFile = File(cacheDir, "Transactions.csv")
            txFile.writeText("id,customerPhone,amount,voucherNumber,categoryName,smsStatus,transactionDate,transactionTime,originalSms,isSynced\n")
            dao.getTransactionsForExport().forEach { tx ->
                txFile.appendText("${tx.id},${escapeCsvField(tx.customerPhone)},${tx.amount},${escapeCsvField(tx.voucherNumber)},${escapeCsvField(tx.categoryName)},${escapeCsvField(tx.smsStatus)},${escapeCsvField(tx.transactionDate)},${escapeCsvField(tx.transactionTime)},${escapeCsvField(tx.originalSms)},${tx.isSynced}\n")
            }

            // 4. Export Daily Statistics
            val statsFile = File(cacheDir, "Daily_Statistics.csv")
            statsFile.writeText("date,categoryId,totalSalesCount,totalRevenue,totalSentVouchers,totalFailedSMS,lastUpdated,isSynced\n")
            dao.getDailyStatisticsForExport().forEach { stat ->
                statsFile.appendText("${escapeCsvField(stat.date)},${stat.categoryId},${stat.totalSalesCount},${stat.totalRevenue},${stat.totalSentVouchers},${stat.totalFailedSMS},${stat.lastUpdated},${stat.isSynced}\n")
            }

            // 5. Export Settings
            val settingsFile = File(cacheDir, "Settings.csv")
            settingsFile.writeText("id,autoSend,smsTemplate,duplicateProtection,autoSync,webAppUrl,syncWifiOnly,lastSyncTime\n")
            val settings = dao.getSettingsSync()
            if (settings != null) {
                settingsFile.appendText("${settings.id},${settings.autoSend},${escapeCsvField(settings.smsTemplate)},${settings.duplicateProtection},${settings.autoSync},${escapeCsvField(settings.webAppUrl)},${settings.syncWifiOnly},${escapeCsvField(settings.lastSyncTime)}\n")
            }

            // 6. Export Application Logs
            val logsFile = File(cacheDir, "Application_Logs.csv")
            logsFile.writeText("id,timestamp,type,message,isSynced\n")
            dao.getLogsForExport().forEach { log ->
                logsFile.appendText("${log.id},${log.timestamp},${escapeCsvField(log.type)},${escapeCsvField(log.message)},${log.isSynced}\n")
            }

            // 7. Export Sms Messages
            val smsFile = File(cacheDir, "Sms_Messages.csv")
            smsFile.writeText("id,sender,body,timestamp,fingerprint,status,retryCount,errorMessage,nextRetryTime,createdAt\n")
            dao.getAllSmsMessagesForBackup().forEach { msg ->
                smsFile.appendText("${msg.id},${escapeCsvField(msg.sender)},${escapeCsvField(msg.body)},${msg.timestamp},${escapeCsvField(msg.fingerprint)},${escapeCsvField(msg.status)},${msg.retryCount},${escapeCsvField(msg.errorMessage)},${msg.nextRetryTime},${msg.createdAt}\n")
            }

            // Zip all CSV files
            val zipFile = File(context.cacheDir, "Hotspot_Voucher_Full_Backup.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                listOf(catFile, vouchersFile, txFile, statsFile, settingsFile, logsFile, smsFile).forEach { file ->
                    if (file.exists()) {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            // Clean up temporary CSVs
            cacheDir.deleteRecursively()

            return@withContext zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun importData(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = database.appDao()
            val zipInputStream = ZipInputStream(inputStream)
            val filesMap = mutableMapOf<String, String>()
            
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    val content = zipInputStream.reader().readText()
                    filesMap[name] = content
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (filesMap.isEmpty()) return@withContext false

            database.withTransaction {
                // Restore Voucher Categories
                if (filesMap.containsKey("Voucher_Categories.csv")) {
                    dao.clearCategories()
                    val lines = filesMap["Voucher_Categories.csv"]!!.lines()
                    val categories = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        VoucherCategory(
                            id = parts[0].toLong(),
                            name = parts[1],
                            createdDate = parts[2].toLong(),
                            isSynced = parts[3].toBoolean()
                        )
                    }
                    dao.insertCategories(categories)
                }

                // Restore Vouchers
                if (filesMap.containsKey("Vouchers.csv")) {
                    dao.clearVouchers()
                    val lines = filesMap["Vouchers.csv"]!!.lines()
                    val vouchers = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        Voucher(
                            id = parts[0].toLong(),
                            categoryId = parts[1].toLong(),
                            voucherNumber = parts[2],
                            status = parts[3].toInt(),
                            customerPhone = parts[4].takeIf { it.isNotEmpty() },
                            sentDate = parts[5].takeIf { it.isNotEmpty() },
                            sentTime = parts[6].takeIf { it.isNotEmpty() },
                            importedAt = parts[7].toLong()
                        )
                    }
                    dao.insertVouchers(vouchers)
                }

                // Restore Transactions
                if (filesMap.containsKey("Transactions.csv")) {
                    dao.clearTransactions()
                    val lines = filesMap["Transactions.csv"]!!.lines()
                    val transactions = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        Transaction(
                            id = parts[0].toLong(),
                            customerPhone = parts[1],
                            amount = parts[2].toDouble(),
                            voucherNumber = parts[3],
                            categoryName = parts[4],
                            smsStatus = parts[5],
                            transactionDate = parts[6],
                            transactionTime = parts[7],
                            originalSms = parts[8],
                            isSynced = parts[9].toBoolean()
                        )
                    }
                    dao.insertTransactions(transactions)
                }

                // Restore Daily Statistics
                if (filesMap.containsKey("Daily_Statistics.csv")) {
                    dao.clearDailyStatistics()
                    val lines = filesMap["Daily_Statistics.csv"]!!.lines()
                    val statistics = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        DailyStatistic(
                            date = parts[0],
                            categoryId = parts[1].toLong(),
                            totalSalesCount = parts[2].toInt(),
                            totalRevenue = parts[3].toDouble(),
                            totalSentVouchers = parts[4].toInt(),
                            totalFailedSMS = parts[5].toInt(),
                            lastUpdated = parts[6].toLong(),
                            isSynced = parts[7].toBoolean()
                        )
                    }
                    dao.insertDailyStatistics(statistics)
                }

                // Restore Settings
                if (filesMap.containsKey("Settings.csv")) {
                    dao.clearSettings()
                    val lines = filesMap["Settings.csv"]!!.lines()
                    if (lines.size > 1) {
                        val line = lines[1]
                        if (line.isNotBlank()) {
                            val parts = parseCsvLine(line)
                            val settings = Settings(
                                id = parts[0].toInt(),
                                autoSend = parts[1].toBoolean(),
                                smsTemplate = parts[2],
                                duplicateProtection = parts[3].toBoolean(),
                                autoSync = parts[4].toBoolean(),
                                webAppUrl = parts[5].takeIf { it.isNotEmpty() },
                                syncWifiOnly = parts[6].toBoolean(),
                                lastSyncTime = parts[7].takeIf { it.isNotEmpty() }
                            )
                            dao.insertSettings(settings)
                        }
                    }
                }

                // Restore Application Logs
                if (filesMap.containsKey("Application_Logs.csv")) {
                    dao.clearLogs()
                    val lines = filesMap["Application_Logs.csv"]!!.lines()
                    val logs = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        AppLog(
                            id = parts[0].toLong(),
                            timestamp = parts[1].toLong(),
                            type = parts[2],
                            message = parts[3],
                            isSynced = parts[4].toBoolean()
                        )
                    }
                    dao.insertLogs(logs)
                }

                // Restore Sms Messages
                if (filesMap.containsKey("Sms_Messages.csv")) {
                    dao.clearSmsMessages()
                    val lines = filesMap["Sms_Messages.csv"]!!.lines()
                    val messages = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                        val parts = parseCsvLine(line)
                        SmsMessage(
                            id = parts[0].toLong(),
                            sender = parts[1],
                            body = parts[2],
                            timestamp = parts[3].toLong(),
                            fingerprint = parts[4],
                            status = parts[5],
                            retryCount = parts[6].toInt(),
                            errorMessage = parts[7].takeIf { it.isNotEmpty() },
                            nextRetryTime = parts[8].toLong(),
                            createdAt = parts[9].toLong()
                        )
                    }
                    dao.insertSmsMessages(messages)
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
