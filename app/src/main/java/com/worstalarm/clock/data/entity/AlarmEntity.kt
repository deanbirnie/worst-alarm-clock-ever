package com.worstalarm.clock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Days-of-week bitmask: bit 0 = Monday ... bit 6 = Sunday.
 * 0 means "one-shot" (fires next occurrence of [hour]:[minute] and disables itself).
 *
 * [ringtoneUri] overrides the alarm sound for this alarm only (a content:// URI the
 * user picked via SAF). Null means "use the global sound from Settings", which in
 * turn falls back to the system default alarm tone.
 *
 * [awakeCheckEnabled] gates the post-routine "are you awake" popups (see
 * AwakeCheckPolicy) for this alarm specifically. Defaults on, since that's the
 * feature's whole point — turn it off per alarm (e.g. a quick nap) as needed.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val ringtoneUri: String? = null,
    val awakeCheckEnabled: Boolean = true
)
