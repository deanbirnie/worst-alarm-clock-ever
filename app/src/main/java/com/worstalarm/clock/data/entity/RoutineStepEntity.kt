package com.worstalarm.clock.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One step in an alarm's routine. Steps are ordered by [stepIndex].
 *
 * Flow at alarm time:
 *   - Step 0 rings immediately when the alarm fires.
 *   - After the user scans step N's barcode, wait [timeToNextRingSeconds] then ring step N+1.
 *   - The last step's [timeToNextRingSeconds] is ignored (there is no next ring).
 */
@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BarcodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["barcodeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("alarmId"), Index("barcodeId")]
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val stepIndex: Int,
    val locationLabel: String,
    val barcodeId: Long,
    val timeToNextRingSeconds: Int
)
