package com.worstalarm.clock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.worstalarm.clock.data.AppDatabase
import com.worstalarm.clock.data.Repository
import com.worstalarm.clock.data.SettingsRepository

class WorstAlarmApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: Repository by lazy {
        Repository(database.alarmDao(), database.barcodeDao(), database.awakeCheckDao())
    }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        createAlarmChannel()
    }

    private fun createAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    getString(R.string.alarm_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.alarm_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "worst_alarm_channel"
    }
}
