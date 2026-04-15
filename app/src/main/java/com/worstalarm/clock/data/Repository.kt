package com.worstalarm.clock.data

import com.worstalarm.clock.data.dao.AlarmDao
import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.data.dao.BarcodeDao
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

class Repository(
    private val alarmDao: AlarmDao,
    private val barcodeDao: BarcodeDao
) {
    fun observeAlarms(): Flow<List<AlarmWithSteps>> = alarmDao.observeAllWithSteps()
    fun observeBarcodes(): Flow<List<BarcodeEntity>> = barcodeDao.observeAll()

    suspend fun getAlarm(id: Long): AlarmWithSteps? = alarmDao.getWithSteps(id)
    suspend fun getAllEnabledAlarms(): List<AlarmWithSteps> = alarmDao.getAllEnabled()
    suspend fun getBarcode(id: Long): BarcodeEntity? = barcodeDao.getById(id)

    suspend fun saveBarcode(barcode: BarcodeEntity): Long =
        if (barcode.id == 0L) barcodeDao.insert(barcode)
        else { barcodeDao.update(barcode); barcode.id }

    suspend fun deleteBarcode(barcode: BarcodeEntity): Boolean {
        if (barcodeDao.usageCount(barcode.id) > 0) return false
        barcodeDao.delete(barcode)
        return true
    }

    /** Persist an alarm + its ordered steps atomically (as far as Room single-call scope allows). */
    suspend fun saveAlarm(alarm: AlarmEntity, steps: List<RoutineStepEntity>): Long {
        val id = if (alarm.id == 0L) alarmDao.insertAlarm(alarm)
        else { alarmDao.updateAlarm(alarm); alarm.id }
        val normalized = steps.mapIndexed { idx, s -> s.copy(alarmId = id, stepIndex = idx) }
        alarmDao.replaceSteps(id, normalized)
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = alarmDao.setEnabled(id, enabled)
    suspend fun deleteAlarm(id: Long) = alarmDao.deleteAlarm(id)
}
