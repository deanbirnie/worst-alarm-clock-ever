package com.worstalarm.clock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Days-of-week bitmask: bit 0 = Monday ... bit 6 = Sunday.
 * 0 means "one-shot" (fires next occurrence of [hour]:[minute] and disables itself).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val enabled: Boolean
)
