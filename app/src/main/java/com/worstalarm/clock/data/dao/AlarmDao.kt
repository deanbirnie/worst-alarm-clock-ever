package com.worstalarm.clock.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

data class StepWithBarcode(
    @Embedded val step: RoutineStepEntity,
    @Relation(
        parentColumn = "barcodeId",
        entityColumn = "id"
    )
    val barcode: BarcodeEntity
)

data class AlarmWithSteps(
    @Embedded val alarm: AlarmEntity,
    @Relation(
        entity = RoutineStepEntity::class,
        parentColumn = "id",
        entityColumn = "alarmId"
    )
    val steps: List<StepWithBarcode>
) {
    val orderedSteps: List<StepWithBarcode> get() = steps.sortedBy { it.step.stepIndex }
}

@Dao
interface AlarmDao {
    @Transaction
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAllWithSteps(): Flow<List<AlarmWithSteps>>

    @Transaction
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getWithSteps(id: Long): AlarmWithSteps?

    @Transaction
    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AlarmWithSteps>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarm(id: Long)

    @Query("DELETE FROM routine_steps WHERE alarmId = :alarmId")
    suspend fun deleteStepsFor(alarmId: Long)

    @Insert
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Transaction
    suspend fun replaceSteps(alarmId: Long, steps: List<RoutineStepEntity>) {
        deleteStepsFor(alarmId)
        insertSteps(steps)
    }
}
