package com.worstalarm.clock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worstalarm.clock.data.dao.AlarmDao
import com.worstalarm.clock.data.dao.AwakeCheckDao
import com.worstalarm.clock.data.dao.BarcodeDao
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.AwakeCheckEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity

@Database(
    entities = [
        AlarmEntity::class,
        BarcodeEntity::class,
        RoutineStepEntity::class,
        AwakeCheckEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun barcodeDao(): BarcodeDao
    abstract fun awakeCheckDao(): AwakeCheckDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2: optional location on barcodes, optional per-alarm ringtone. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE barcodes ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarms ADD COLUMN ringtoneUri TEXT")
            }
        }

        /** v3: post-routine "are you awake" check cycle, one row per alarm mid-cycle. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS awake_checks (
                        alarmId INTEGER NOT NULL PRIMARY KEY,
                        dismissedCount INTEGER NOT NULL,
                        nextCheckAtMs INTEGER NOT NULL,
                        popupDeadlineAtMs INTEGER NOT NULL,
                        FOREIGN KEY(alarmId) REFERENCES alarms(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        /** v4: per-alarm toggle for the awake-check cycle. Defaults on for existing alarms. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN awakeCheckEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private const val DB_NAME = "worst-alarm.db"

        /**
         * The database lives in **device-protected storage** so the alarm engine can read
         * it during Direct Boot — the window after a reboot (e.g. an overnight OS update)
         * but before the user has unlocked the phone for the first time. Credential-protected
         * storage (Room's default location) is sealed until that first unlock, which for an
         * alarm people set for the early hours would mean the alarm silently never fires.
         *
         * Trade-off: device-protected storage is encrypted with a device key rather than one
         * derived from the user's lock-screen credential. Acceptable here — the contents are
         * alarm times, weekday masks, and barcode values, not secrets — and it's the same
         * approach AOSP's own Clock uses so alarms survive a locked reboot.
         */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val deviceContext = appContext.createDeviceProtectedStorageContext()
                // One-time migration for users upgrading from a build that kept the DB in
                // credential storage: move the existing file (and its -wal/-shm) across so
                // no alarms are lost. No-op on fresh installs or once already moved. Wrapped
                // defensively — this realistically runs on the first (unlocked) app open, but
                // a failure here must never crash the boot/alarm re-arm path.
                runCatching { deviceContext.moveDatabaseFrom(appContext, DB_NAME) }
                Room.databaseBuilder(deviceContext, AppDatabase::class.java, DB_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
