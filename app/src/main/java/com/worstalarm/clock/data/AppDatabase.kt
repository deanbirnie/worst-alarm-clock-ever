package com.worstalarm.clock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.worstalarm.clock.data.dao.AlarmDao
import com.worstalarm.clock.data.dao.BarcodeDao
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity

@Database(
    entities = [AlarmEntity::class, BarcodeEntity::class, RoutineStepEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "worst-alarm.db"
            ).build().also { instance = it }
        }
    }
}
