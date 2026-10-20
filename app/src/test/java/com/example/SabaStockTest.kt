package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.local.AppDatabase
import com.example.data.local.entity.Voucher
import com.example.data.local.entity.VoucherCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class SabaStockTest {
    @Test fun reservationAndReplayNeverMakeSentCardsAvailable() = runTest {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.appDao()
            val category=dao.insertCategory(VoucherCategory(name="100",createdDate=1))
            val stock=Voucher(categoryId=category,voucherNumber="00123",remoteId="11111111-1111-1111-1111-111111111111",password="pass")
            val localId=dao.insertVouchers(listOf(stock)).single()
            assertEquals(1,dao.reserveVoucher(localId))
            assertEquals(0,dao.reserveVoucher(localId))
            dao.ackVoucher(localId,0)
            assertEquals(3,dao.getVouchersForSync(500).single().status)
            dao.updateVoucher(dao.findVoucherByUsername("00123")!!.copy(status=1))
            assertEquals(-1L,dao.insertVouchers(listOf(stock)).single())
            assertEquals(1,dao.findVoucherByUsername("00123")!!.status)
            assertEquals("pass",dao.findVoucherByUsername("00123")!!.password)
        } finally {db.close()}
    }
    @Test fun versionEightMigrationPreservesStockAndResyncsOldTransactions() {
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .callback(object:SupportSQLiteOpenHelper.Callback(8){
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE vouchers(id INTEGER PRIMARY KEY,categoryId INTEGER,voucherNumber TEXT,status INTEGER)")
                    db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY,isSynced INTEGER)")
                    db.execSQL("CREATE TABLE app_logs(id INTEGER PRIMARY KEY,isSynced INTEGER)")
                    db.execSQL("INSERT INTO vouchers VALUES(1,1,'00123',1)")
                    db.execSQL("INSERT INTO transactions VALUES(1,1)")
                }
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {}
            }).build())
        try {
            val db=helper.writableDatabase
            AppDatabase.MIGRATION_8_9.migrate(db)
            db.query("SELECT voucherNumber,status,syncedStatus FROM vouchers").use {assertTrue(it.moveToFirst());assertEquals("00123",it.getString(0));assertEquals(1,it.getInt(1));assertEquals(-1,it.getInt(2))}
            db.query("SELECT isSynced,sabaSynced FROM transactions").use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0));assertEquals(0,it.getInt(1))}
        } finally {helper.close()}
    }
}
