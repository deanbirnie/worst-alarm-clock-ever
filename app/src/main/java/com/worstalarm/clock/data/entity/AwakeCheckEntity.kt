package com.worstalarm.clock.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Persisted "is the user actually awake" cycle for one alarm, active only after its final
 * routine step has been scanned. Survives process death / reboot so a killed app doesn't
 * silently drop a pending check (the same reason step-rings use AlarmManager, not a live
 * timer — see AlarmScheduler.scheduleStepRing).
 *
 * At most one row per alarm; deleting the alarm cascades the row away, cancelling the
 * cycle along with it (any already-scheduled AlarmManager alarms are cancelled separately
 * in AppViewModel.deleteAlarm, since Room can't reach into AlarmManager).
 */
@Entity(
    tableName = "awake_checks",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AwakeCheckEntity(
    @PrimaryKey val alarmId: Long,
    /** Successful "I'm awake" dismissals so far this cycle (0 or 1 — 2 deletes the row). */
    val dismissedCount: Int,
    /** Epoch ms the next popup is scheduled to appear. */
    val nextCheckAtMs: Long,
    /** Epoch ms the currently-shown popup must be dismissed by, or 0 if none is showing. */
    val popupDeadlineAtMs: Long
)
