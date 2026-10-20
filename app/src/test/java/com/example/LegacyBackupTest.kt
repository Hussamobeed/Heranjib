package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.CsvExportManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=android.app.Application::class)
class LegacyBackupTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun database()=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
    private fun legacy(overrides:Map<String,String> = emptyMap()):ByteArray {
        val files=mutableMapOf(
            "Voucher_Categories.csv" to "id,name,createdDate,isSynced\n1,100,1,true\n",
            "Vouchers.csv" to "id,categoryId,voucherNumber,status,customerPhone,sentDate,sentTime,importedAt\n1,1,001,0,,,,1\n2,1,002,1,777000000,2026-10-20,10:00:00,1\n",
            "Transactions.csv" to "id,customerPhone,amount,voucherNumber,categoryName,smsStatus,transactionDate,transactionTime,originalSms,isSynced\n1,777000000,100,002,100,SENT,2026-10-20,10:00:00,\"payment, ok\\nline\",true\n",
            "Daily_Statistics.csv" to "date,categoryId,totalSalesCount,totalRevenue,totalSentVouchers,totalFailedSMS,lastUpdated,isSynced\n2026-10-20,1,1,100,1,0,1,true\n",
            "Settings.csv" to "id,autoSend,smsTemplate,duplicateProtection,autoSync,webAppUrl,syncWifiOnly,lastSyncTime\n1,true,code,true,false,,true,\n",
            "Application_Logs.csv" to "id,timestamp,type,message,isSynced\n",
            "Sms_Messages.csv" to "id,sender,body,timestamp,fingerprint,status,retryCount,errorMessage,nextRetryTime,createdAt\n1,Bank,test,1,fingerprint,PENDING,0,,0,1\n"
        )
        files.putAll(overrides)
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use{zip->files.forEach{(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry()}}
        return output.toByteArray()
    }
    private fun linked(value:Boolean){context.getSharedPreferences("saba_private",Context.MODE_PRIVATE).edit().clear().apply{if(value)putString("credential","test")}.commit()}

    @Test fun oldApkZipRestoresLeadingZerosSentFlagsHistoryAndSafeMessageQueue()=runTest {
        linked(false);val db=database()
        try {
            val manager=CsvExportManager(context,db)
            assertTrue(manager.lastImportError,manager.importData(legacy().inputStream()))
            val dao=db.appDao()
            assertEquals(2,dao.getAllVouchersForBackup().size)
            assertEquals(0,dao.findVoucherByUsername("001")!!.status)
            assertEquals(1,dao.findVoucherByUsername("002")!!.status)
            assertEquals("payment, ok\nline",dao.getTransactionsForExport().single().originalSms)
            assertFalse(dao.getTransactionsForExport().single().sabaSynced)
            assertEquals("REVIEW",dao.getAllSmsMessagesForBackup().single().status)
            assertEquals(10,dao.getSettingsSync()!!.giftThreshold)
        } finally {db.close()}
    }
    @Test fun malformedArchiveDoesNotClearExistingDatabase()=runTest {
        linked(false);val db=database()
        try {
            val dao=db.appDao();val category=dao.insertCategory(VoucherCategory(name="100",createdDate=1))
            dao.insertVouchers(listOf(Voucher(categoryId=category,voucherNumber="keep",status=1)))
            val manager=CsvExportManager(context,db)
            assertFalse(manager.importData(legacy(mapOf("Vouchers.csv" to "wrong\n")).inputStream()))
            assertEquals("keep",dao.getAllVouchersForBackup().single().voucherNumber)
        } finally {db.close()}
    }
    @Test fun linkedRestoreMergesTwiceWithoutResettingSoldCardsOrDuplicatingSales()=runTest {
        linked(true);val db=database()
        try {
            val dao=db.appDao();val cat=dao.insertCategory(VoucherCategory(name="100",createdDate=1))
            dao.insertVouchers(listOf(Voucher(categoryId=cat,voucherNumber="001",status=1,remoteId="11111111-1111-1111-1111-111111111111")))
            dao.insertTransaction(Transaction(id=1,customerPhone="other",amount=200.0,voucherNumber="other",categoryName="100",smsStatus="SENT",transactionDate="2026-10-20",transactionTime="09:00:00",originalSms="keep"))
            val manager=CsvExportManager(context,db)
            assertTrue(manager.lastImportError,manager.importData(legacy().inputStream()))
            assertTrue(manager.lastImportError,manager.importData(legacy().inputStream()))
            assertEquals(1,dao.findVoucherByUsername("001")!!.status)
            assertEquals("11111111-1111-1111-1111-111111111111",dao.findVoucherByUsername("001")!!.remoteId)
            assertEquals(1,dao.findVoucherByUsername("002")!!.status)
            assertEquals(2,dao.getTransactionsForExport().size)
            assertTrue(com.example.integration.SabaLink(context).configured)
        } finally {linked(false);db.close()}
    }
    @Test fun extendedBackupRoundTripKeepsPasswordsProfilesAndRemoteIdentity()=runTest {
        linked(false);val db=database()
        try {
            val dao=db.appDao();val cat=dao.insertCategory(VoucherCategory(name="100",createdDate=1))
            dao.insertVouchers(listOf(Voucher(categoryId=cat,voucherNumber="0001",password="p,ass",profile="profile",status=1,remoteId="11111111-1111-1111-1111-111111111111")))
            val manager=CsvExportManager(context,db);val file=manager.exportData()
            assertNotNull(file)
            assertTrue(manager.lastImportError,file!!.inputStream().use{manager.importData(it)})
            val restored=dao.findVoucherByUsername("0001")!!
            assertEquals("p,ass",restored.password);assertEquals("profile",restored.profile);assertNotNull(restored.remoteId);assertEquals(1,restored.status)
        } finally {db.close()}
    }
}
