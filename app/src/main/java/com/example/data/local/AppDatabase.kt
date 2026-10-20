package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.entity.AppLog
import com.example.data.local.entity.DailyStatistic
import com.example.data.local.entity.Settings
import com.example.data.local.entity.SmsMessage
import com.example.data.local.entity.Transaction
import com.example.data.local.entity.Voucher
import com.example.data.local.entity.VoucherCategory
import com.example.data.local.dao.AppDao

@Database(
    entities = [
        VoucherCategory::class, 
        Voucher::class, 
        Transaction::class, 
        Settings::class,
        SmsMessage::class,
        AppLog::class,
        DailyStatistic::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN giftEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN giftThreshold INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE settings ADD COLUMN giftCategoryPreference TEXT NOT NULL DEFAULT 'MOST_BOUGHT'")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN password TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN profile TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN syncedStatus INTEGER NOT NULL DEFAULT -1")
                db.execSQL("CREATE UNIQUE INDEX index_vouchers_remoteId ON vouchers(remoteId)")
                db.execSQL("ALTER TABLE transactions ADD COLUMN sabaSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_logs ADD COLUMN sabaSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hotspot_voucher_db"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

