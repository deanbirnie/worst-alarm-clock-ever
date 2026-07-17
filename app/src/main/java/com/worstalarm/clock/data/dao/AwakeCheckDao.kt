package com.worstalarm.clock.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.worstalarm.clock.data.entity.AwakeCheckEntity

@Dao
interface AwakeCheckDao {
    @Upsert
    suspend fun upsert(entity: AwakeCheckEntity)

    @Query("SELECT * FROM awake_checks WHERE alarmId = :alarmId")
    suspend fun get(alarmId: Long): AwakeCheckEntity?

    /** Every alarm currently mid-cycle — used to re-arm schedules after a reboot. */
    @Query("SELECT * FROM awake_checks")
    suspend fun getAll(): List<AwakeCheckEntity>

    @Query("DELETE FROM awake_checks WHERE alarmId = :alarmId")
    suspend fun delete(alarmId: Long)
}
