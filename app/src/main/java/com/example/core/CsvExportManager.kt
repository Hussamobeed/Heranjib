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
import kotlinx.coroutines.sync.withLock
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
        require(!inQuotes) { "سطر CSV غير مكتمل" }
        result.add(unescapeCsvField(currentField.toString()))
        return result
    }

    suspend fun exportData(): File? = withContext(Dispatchers.IO) { LedgerGate.sync.withLock { LedgerGate.sales.withLock {
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
            vouchersFile.writeText("id,categoryId,voucherNumber,status,customerPhone,sentDate,sentTime,importedAt,remoteId,password,profile\n")
            dao.getAllVouchersForBackup().forEach { v ->
                vouchersFile.appendText("${v.id},${v.categoryId},${escapeCsvField(v.voucherNumber)},${v.status},${escapeCsvField(v.customerPhone)},${escapeCsvField(v.sentDate)},${escapeCsvField(v.sentTime)},${v.importedAt},${escapeCsvField(v.remoteId)},${escapeCsvField(v.password)},${escapeCsvField(v.profile)}\n")
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
            settingsFile.writeText("id,autoSend,smsTemplate,duplicateProtection,autoSync,webAppUrl,syncWifiOnly,lastSyncTime,giftEnabled,giftThreshold,giftCategoryPreference\n")
            val settings = dao.getSettingsSync()
            if (settings != null) {
                settingsFile.appendText("${settings.id},${settings.autoSend},${escapeCsvField(settings.smsTemplate)},${settings.duplicateProtection},${settings.autoSync},${escapeCsvField(settings.webAppUrl)},${settings.syncWifiOnly},${escapeCsvField(settings.lastSyncTime)},${settings.giftEnabled},${settings.giftThreshold},${escapeCsvField(settings.giftCategoryPreference)}\n")
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

            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    } } }


    var lastImportError: String? = null
        private set

    /** Accept the ZIP/CSV format produced by the old APK as well as the extended format. */
    suspend fun importData(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        lastImportError = null
        try {
            val expected = setOf("Voucher_Categories.csv","Vouchers.csv","Transactions.csv","Daily_Statistics.csv","Settings.csv","Application_Logs.csv","Sms_Messages.csv")
            val files = mutableMapOf<String,String>()
            var total = 0L
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name in expected) {
                        require(!files.containsKey(name)) { "ملف مكرر داخل النسخة الاحتياطية" }
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var n = zip.read(buffer)
                        while (n >= 0) {
                            total += n
                            require(total <= 64L * 1024 * 1024) { "حجم النسخة بعد فك الضغط أكبر من 64 ميجابايت" }
                            bytes.write(buffer,0,n); n = zip.read(buffer)
                        }
                        files[name] = bytes.toString("UTF-8")
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            require(files.keys.containsAll(expected)) { "اختر ملف ZIP الكامل المصدر من التطبيق القديم أو الجديد، وليس ملف CSV منفرداً" }
            val columns=mapOf(
                "Voucher_Categories.csv" to "id,name,createdDate,isSynced",
                "Vouchers.csv" to "id,categoryId,voucherNumber,status,customerPhone,sentDate,sentTime,importedAt",
                "Transactions.csv" to "id,customerPhone,amount,voucherNumber,categoryName,smsStatus,transactionDate,transactionTime,originalSms,isSynced",
                "Daily_Statistics.csv" to "date,categoryId,totalSalesCount,totalRevenue,totalSentVouchers,totalFailedSMS,lastUpdated,isSynced",
                "Settings.csv" to "id,autoSend,smsTemplate,duplicateProtection,autoSync,webAppUrl,syncWifiOnly,lastSyncTime",
                "Application_Logs.csv" to "id,timestamp,type,message,isSynced",
                "Sms_Messages.csv" to "id,sender,body,timestamp,fingerprint,status,retryCount,errorMessage,nextRetryTime,createdAt")
            fun table(name:String): List<Map<String,String>> {
                val lines=files.getValue(name).removePrefix("\uFEFF").lines().filter{it.isNotBlank()}
                require(lines.isNotEmpty()) { "ملف فارغ: $name" }
                val header=parseCsvLine(lines.first())
                require(header.containsAll(columns.getValue(name).split(','))) { "عناوين النسخة غير صالحة: $name" }
                require(header.distinct().size==header.size) { "عناوين مكررة في $name" }
                return lines.drop(1).map { line ->
                    val cells=parseCsvLine(line)
                    require(cells.size==header.size) { "سطر غير مكتمل في $name" }
                    header.zip(cells).toMap()
                }
            }
            fun Map<String,String>.required(key:String)=get(key) ?: error("حقل ناقص: $key")
            fun Map<String,String>.long(key:String)=required(key).toLong()
            fun Map<String,String>.bool(key:String,default:Boolean=false):Boolean = when(get(key)?.lowercase()) {
                "true","1"->true; "false","0"->false; null,""->default; else->error("قيمة غير صالحة: $key")
            }
            fun digits(text:String)=text.map{if(it.isDigit())Character.getNumericValue(it).toString() else it.toString()}.joinToString("")
            val categories=table("Voucher_Categories.csv").map{r->VoucherCategory(r.long("id"),r.required("name"),r.long("createdDate"),r.bool("isSynced"))}
            val categoryIds=categories.map{it.id}.toSet()
            require(categoryIds.size==categories.size && categories.all{it.id>0 && it.name.isNotBlank()}) { "فئات المخزون غير صالحة" }
            val vouchers=table("Vouchers.csv").map{r->
                val state=r.required("status").toInt()
                require(state in 0..3 && r.long("categoryId") in categoryIds && r.required("voucherNumber").isNotBlank()) { "بيانات الكروت غير صالحة" }
                val remote=r["remoteId"]?.takeIf{it.isNotEmpty()}
                if(remote!=null)java.util.UUID.fromString(remote)
                Voucher(id=r.long("id"),categoryId=r.long("categoryId"),voucherNumber=r.required("voucherNumber"),status=if(state==3)2 else state,
                    customerPhone=r["customerPhone"]?.takeIf{it.isNotEmpty()},sentDate=r["sentDate"]?.takeIf{it.isNotEmpty()},sentTime=r["sentTime"]?.takeIf{it.isNotEmpty()},
                    importedAt=r.long("importedAt"),remoteId=remote,password=r["password"]?:"",profile=r["profile"]?:"",syncedStatus=-1)
            }
            require(vouchers.map{it.id}.distinct().size==vouchers.size && vouchers.all{it.id>0}) { "معرفات الكروت مكررة" }
            val transactions=table("Transactions.csv").map{r->
                val amount=r.required("amount").toDouble();require(amount.isFinite()&&amount>=0) { "مبلغ غير صالح" }
                val status=r.required("smsStatus");require(status in listOf("SENT","FAILED","GIFT_SENT","UNKNOWN")) { "حالة معاملة غير صالحة" }
                Transaction(id=r.long("id"),customerPhone=r.required("customerPhone"),amount=amount,voucherNumber=r.required("voucherNumber"),categoryName=r.required("categoryName"),
                    smsStatus=status,transactionDate=digits(r.required("transactionDate")),transactionTime=digits(r.required("transactionTime")),originalSms=r.required("originalSms"),isSynced=r.bool("isSynced"),sabaSynced=false)
            }
            require(transactions.map{it.id}.distinct().size==transactions.size && transactions.all{it.id>0}) { "معرفات المعاملات مكررة" }
            val statistics=table("Daily_Statistics.csv").map{r->DailyStatistic(date=digits(r.required("date")),categoryId=r.long("categoryId"),
                totalSalesCount=r.required("totalSalesCount").toInt(),totalRevenue=r.required("totalRevenue").toDouble(),totalSentVouchers=r.required("totalSentVouchers").toInt(),
                totalFailedSMS=r.required("totalFailedSMS").toInt(),lastUpdated=r.long("lastUpdated"),isSynced=r.bool("isSynced"))}
            require(statistics.all{it.categoryId in categoryIds && it.totalRevenue.isFinite()}) { "إحصائيات النسخة غير صالحة" }
            val settings=table("Settings.csv").map{r->Settings(id=r.required("id").toInt(),autoSend=r.bool("autoSend",true),smsTemplate=r.required("smsTemplate"),duplicateProtection=r.bool("duplicateProtection",true),
                autoSync=r.bool("autoSync"),webAppUrl=null,syncWifiOnly=r.bool("syncWifiOnly",true),lastSyncTime=null,giftEnabled=r.bool("giftEnabled",true),
                giftThreshold=r["giftThreshold"]?.toIntOrNull()?:10,giftCategoryPreference=r["giftCategoryPreference"]?:"MOST_BOUGHT")}
            require(settings.size<=1) { "إعدادات النسخة غير صالحة" }
            val logs=table("Application_Logs.csv").map{r->AppLog(id=r.long("id"),timestamp=r.long("timestamp"),type=r.required("type"),message=r.required("message"),isSynced=r.bool("isSynced"),sabaSynced=false)}
            val messages=table("Sms_Messages.csv").map{r->
                val status=r.required("status")
                SmsMessage(id=r.long("id"),sender=r.required("sender"),body=r.required("body"),timestamp=r.long("timestamp"),fingerprint=r.required("fingerprint"),
                    status=if(status in listOf("PENDING","PROCESSING","RETRYING"))"REVIEW" else status,retryCount=r.required("retryCount").toInt(),
                    errorMessage=r["errorMessage"]?.takeIf{it.isNotEmpty()},nextRetryTime=r.long("nextRetryTime"),createdAt=r.long("createdAt"))
            }
            LedgerGate.sync.withLock { LedgerGate.sales.withLock {
                val linked=com.example.integration.SabaLink(context).configured
                val dao=database.appDao()
                database.withTransaction {
                    if(!linked) {
                        dao.clearVouchers();dao.clearCategories();dao.clearTransactions();dao.clearDailyStatistics();dao.clearSettings();dao.clearLogs();dao.clearSmsMessages()
                        dao.insertCategories(categories);check(dao.insertVouchers(vouchers).all{it!=-1L}) { "كروت مكررة داخل الفئة" }
                        dao.insertTransactions(transactions);dao.insertDailyStatistics(statistics)
                        settings.forEach{dao.insertSettings(it)};dao.insertLogs(logs);dao.insertSmsMessages(messages)
                    } else {
                        val mapping=mutableMapOf<Long,Long>()
                        for(category in categories) {
                            val present=dao.getCategoryByName(category.name)
                            mapping[category.id]=present?.id?:dao.insertCategory(category.copy(id=0))
                        }
                        for(v in vouchers) {
                            val present=dao.findVoucherByUsername(v.voucherNumber)
                            if(present==null) {
                                check(dao.insertVouchers(listOf(v.copy(id=0,categoryId=mapping.getValue(v.categoryId),status=if(v.status==1)1 else 2,remoteId=null))).single()!=-1L)
                            } else {
                                val state=if(present.status==1||v.status==1)1 else if(present.status in listOf(2,3)||v.status in listOf(2,3))2 else 0
                                dao.updateVoucher(present.copy(status=state,password=present.password.ifEmpty{v.password},profile=present.profile.ifEmpty{v.profile},syncedStatus=-1))
                            }
                        }
                        fun key(t:Transaction)=listOf(t.customerPhone,t.amount.toString(),t.voucherNumber,t.categoryName,t.smsStatus,t.transactionDate,t.transactionTime,t.originalSms)
                        val current=dao.getTransactionsForExport();val keys=current.map{key(it)}.toMutableSet();val used=current.map{it.id}.toMutableSet()
                        for(t in transactions)if(keys.add(key(t))){
                            val inserted=dao.insertTransaction(t.copy(id=if(used.add(t.id))t.id else 0,sabaSynced=false))
                            used.add(inserted)
                        }
                        // Keep the active phone identity, current settings and processed message fingerprints.
                        val fingerprints=dao.getAllSmsMessagesForBackup().map{it.fingerprint}.toMutableSet()
                        for(message in messages)if(fingerprints.add(message.fingerprint))dao.insertSmsMessage(message.copy(id=0))
                        val logKeys=dao.getLogsForExport().map{listOf(it.timestamp.toString(),it.type,it.message)}.toMutableSet()
                        for(log in logs)if(logKeys.add(listOf(log.timestamp.toString(),log.type,log.message)))dao.insertLog(log.copy(id=0,sabaSynced=false))
                        dao.clearDailyStatistics()
                        val cats=dao.getAllCategoriesList().associateBy{it.name}
                        for(t in dao.getTransactionsForExport()){
                            val cat=cats[t.categoryName.removeSuffix(" (هدية)")]?:continue
                            val old=dao.getDailyStatistic(t.transactionDate,cat.id)?:DailyStatistic(date=t.transactionDate,categoryId=cat.id)
                            dao.insertDailyStatistic(old.copy(totalSalesCount=old.totalSalesCount+if(t.smsStatus=="SENT")1 else 0,
                                totalRevenue=old.totalRevenue+if(t.smsStatus=="SENT")t.amount else 0.0,
                                totalSentVouchers=old.totalSentVouchers+if(t.smsStatus in listOf("SENT","GIFT_SENT"))1 else 0,
                                totalFailedSMS=old.totalFailedSMS+if(t.smsStatus in listOf("FAILED","UNKNOWN"))1 else 0,lastUpdated=System.currentTimeMillis()))
                        }
                    }
                }
                if(linked)runCatching { com.example.worker.SyncWorker.enqueue(context) }
            } }
            true
        } catch(e:Exception) {
            lastImportError=e.message?:"تعذر استعادة النسخة"
            false
        }
    }
}
