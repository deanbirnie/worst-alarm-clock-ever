package com.worstalarm.clock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worstalarm.clock.WorstAlarmApp
import com.worstalarm.clock.alarm.AlarmScheduler
import com.worstalarm.clock.data.Repository
import com.worstalarm.clock.data.dao.AlarmWithSteps
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared application-level view model. Exposes the repository Flows and commits mutations. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: Repository = (app as WorstAlarmApp).repository
    private val appContext = app.applicationContext

    fun alarms(): Flow<List<AlarmWithSteps>> = repo.observeAlarms()
    fun barcodes(): Flow<List<BarcodeEntity>> = repo.observeBarcodes()

    suspend fun loadAlarm(id: Long): AlarmWithSteps? = withContext(Dispatchers.IO) {
        repo.getAlarm(id)
    }

    fun saveBarcode(barcode: BarcodeEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repo.saveBarcode(barcode)
            onSaved(id)
        }
    }

    fun deleteBarcode(barcode: BarcodeEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(repo.deleteBarcode(barcode))
        }
    }

    fun saveAlarm(
        alarm: AlarmEntity,
        steps: List<RoutineStepEntity>,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repo.saveAlarm(alarm, steps)
            val saved = repo.getAlarm(id)?.alarm ?: alarm.copy(id = id)
            if (saved.enabled) AlarmScheduler.schedule(appContext, saved)
            else AlarmScheduler.cancel(appContext, saved.id)
            onSaved()
        }
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setEnabled(alarm.id, enabled)
            if (enabled) AlarmScheduler.schedule(appContext, alarm.copy(enabled = true))
            else AlarmScheduler.cancel(appContext, alarm.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancel(appContext, alarm.id)
            repo.deleteAlarm(alarm.id)
        }
    }
}
