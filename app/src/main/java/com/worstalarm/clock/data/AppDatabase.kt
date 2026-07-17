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
    version = 3,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "worst-alarm.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
