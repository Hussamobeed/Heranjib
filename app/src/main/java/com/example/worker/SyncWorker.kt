package com.example.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.integration.SabaLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncWorker(private val context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    companion object {
        private val lock=Mutex()
        private fun asciiDigits(text: String) = text.map { ch -> if (ch.isDigit()) Character.getNumericValue(ch).toString() else ch.toString() }.joinToString("")
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("SabaImmediateSync",ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        }
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        lock.withLock {
            val link=SabaLink(context)
            if(!link.configured)return@withLock Result.success()
            val db=AppDatabase.getDatabase(context); val dao=db.appDao()
            fun id(kind:String,value:Long)=UUID.nameUUIDFromBytes("${link.installationId}:$kind:$value".toByteArray(Charsets.UTF_8)).toString()
            fun empty()=JSONObject().put("vouchers",JSONArray()).put("transactions",JSONArray()).put("logs",JSONArray()).put("appVersion",BuildConfig.VERSION_NAME)
            var logsEnabled=false
            try {
                check(link.request("sync",empty()).optBoolean("accepted"))
                // Server requests remain pending until every credential is committed locally.
                val poll=link.request("poll",JSONObject())
                logsEnabled=poll.optBoolean("logsEnabled")
                val commands=poll.getJSONArray("commands")
                for(i in 0 until commands.length()) {
                    val command=commands.getJSONObject(i);val users=command.getJSONArray("users")
                    db.withTransaction {
                        val name=command.getString("category")
                        require(name.toIntOrNull()==command.getInt("price")) { "فئة الدفعة غير صالحة" }
                        var category=dao.getCategoryByName(name)
                        if(category==null){val categoryId=dao.insertCategory(VoucherCategory(name=name,createdDate=System.currentTimeMillis()));category=dao.getCategoryByName(name)?:throw IllegalStateException("تعذر إنشاء الفئة $categoryId")}
                        for(j in 0 until users.length()) {
                            val user=users.getJSONObject(j); val remoteId=user.getString("id"); val username=user.getString("username")
                            val previous=dao.findVoucherByUsername(username)
                            if(previous!=null) {
                                require(previous.remoteId==remoteId) { "الكرت $username موجود مسبقاً. لم نؤكد استلام الدفعة" }
                            } else {
                                val result=dao.insertVouchers(listOf(Voucher(categoryId=category!!.id,voucherNumber=username,remoteId=remoteId,
                                    password=user.optString("password"),profile=user.optString("profile"),syncedStatus=-1)))
                                check(result.single()!=-1L) { "تعذر حفظ كرت من الدفعة" }
                            }
                        }
                    }
                    check(link.request("sync",empty().put("ack",command.getString("id"))).optBoolean("accepted")) { "لم يؤكد الخادم الاستلام" }
                }
                // Upgrade stock in-place. Sent flags are never reset by a download or migration.
                for(voucher in dao.getAllVouchersForBackup()) if(voucher.remoteId==null)dao.setRemoteId(voucher.id,id("voucher",voucher.id))
                val categories=dao.getAllCategoriesList().associateBy{it.id}
                while(true) {
                    val vouchers=dao.getVouchersForSync(500);if(vouchers.isEmpty())break
                    val payload=JSONArray()
                    for(v in vouchers) {
                        val category=categories[v.categoryId]?.name ?: throw IllegalStateException("فئة الكرت غير موجودة")
                        val price=category.toIntOrNull() ?: throw IllegalStateException("فئة $category يجب أن تكون مبلغاً رقمياً")
                        payload.put(JSONObject().put("id",v.remoteId).put("username",v.voucherNumber).put("category",category).put("price",price)
                            .put("status",when(v.status){0->"available";1->"sent";3->"reserved";else->"unknown"}))
                    }
                    check(link.request("sync",empty().put("vouchers",payload)).optBoolean("accepted")) { "لم يؤكد الخادم المخزون" }
                    for(v in vouchers)dao.ackVoucher(v.id,v.status)
                }
                while(true) {
                    val transactions=dao.getTransactionsForSync(200);if(transactions.isEmpty())break
                    val payload=JSONArray()
                    for(tx in transactions) {
                        val voucher=dao.findVoucherByUsername(tx.voucherNumber)
                        val date=LocalDate.parse(asciiDigits(tx.transactionDate),DateTimeFormatter.ISO_LOCAL_DATE)
                        val time=LocalTime.parse(asciiDigits(tx.transactionTime))
                        payload.put(JSONObject().put("id",id("transaction",tx.id)).put("voucherId",voucher?.remoteId?:"").put("username",tx.voucherNumber)
                            .put("originalSms",tx.originalSms.take(4000)).put("category",tx.categoryName).put("phone",tx.customerPhone).put("amount",tx.amount).put("status",tx.smsStatus)
                            .put("saleDate",date.toString()).put("soldAt",date.atTime(time).atZone(ZoneId.of("Asia/Aden")).toInstant().toString()))
                    }
                    check(link.request("sync",empty().put("transactions",payload)).optBoolean("accepted")) { "لم يؤكد الخادم المعاملات" }
                    transactions.forEach{dao.ackTransaction(it.id)}
                }
                if(poll.optBoolean("logsEnabled")) {
                    while(true) {
                        val logs=dao.getLogsForSaba(200);if(logs.isEmpty())break
                        val payload=JSONArray()
                        logs.forEach{payload.put(JSONObject().put("id",id("log",it.id)).put("level",it.type).put("message",it.message.take(2000)).put("loggedAt",Instant.ofEpochMilli(it.timestamp).toString()))}
                        val result=link.request("sync",empty().put("logs",payload))
                        check(result.optBoolean("accepted")) { "لم يؤكد الخادم السجل" }
                        if(!result.optBoolean("logsEnabled"))break
                        logs.forEach{dao.ackSabaLog(it.id)}
                    }
                }
                check(link.request("sync",empty().put("inventoryReady",true)).optBoolean("accepted"))
                dao.getSettingsSync()?.let{dao.updateSettings(it.copy(lastSyncTime=Instant.now().toString()))}
                Result.success()
            } catch(e:Exception) {
                dao.insertLog(AppLog(type="ERROR",message="مزامنة SABA: ${e.message}".take(2000)))
                if(logsEnabled)try {
                    val diagnostic=JSONObject().put("id",UUID.randomUUID().toString()).put("level","ERROR").put("message","مزامنة SABA: ${e.message}".take(2000)).put("loggedAt",Instant.now().toString())
                    link.request("sync",empty().put("logs",JSONArray().put(diagnostic)))
                } catch(_:Exception) {}
                Result.retry()
            }
        }
    }
}
