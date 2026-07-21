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

    private val worstApp = app as WorstAlarmApp
    private val repo: Repository = worstApp.repository
    private val settings = worstApp.settings
    private val appContext = app.applicationContext

    fun alarms(): Flow<List<AlarmWithSteps>> = repo.observeAlarms()
    fun barcodes(): Flow<List<BarcodeEntity>> = repo.observeBarcodes()

    // ----- App settings -----

    val introSeen: Flow<Boolean> = settings.introSeen
    val globalRingtone: Flow<String?> = settings.globalRingtoneUri

    fun setIntroSeen(seen: Boolean) {
        viewModelScope.launch { settings.setIntroSeen(seen) }
    }

    fun setGlobalRingtone(uri: String?) {
        viewModelScope.launch { settings.setGlobalRingtoneUri(uri) }
    }

    suspend fun loadAlarm(id: Long): AlarmWithSteps? = withContext(Dispatchers.IO) {
        repo.getAlarm(id)
    }

    // Room suspend DAOs are main-safe (they hop to Room's own executor), so these launch on
    // the default main dispatcher and callbacks fire on the main thread — callers navigate
    // and show toasts from them.
    fun saveBarcode(barcode: BarcodeEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { repo.saveBarcode(barcode) }
            onSaved(id)
        }
    }

    fun deleteBarcode(barcode: BarcodeEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteBarcode(barcode) }
            onResult(ok)
        }
    }

    // The saved entity is passed to the callback so the UI can show the "Rings in …"
    // note (NextRingFormatter) for exactly what was persisted and scheduled.
    fun saveAlarm(
        alarm: AlarmEntity,
        steps: List<RoutineStepEntity>,
        onSaved: (AlarmEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val id = repo.saveAlarm(alarm, steps)
                repo.getAlarm(id)?.alarm ?: alarm.copy(id = id)
            }
            if (saved.enabled) AlarmScheduler.schedule(appContext, saved)
            else AlarmScheduler.cancel(appContext, saved.id)
            onSaved(saved)
        }
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setEnabled(alarm.id, enabled) }
            if (enabled) AlarmScheduler.schedule(appContext, alarm.copy(enabled = true))
            else AlarmScheduler.cancel(appContext, alarm.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(appContext, alarm.id)
            AlarmScheduler.cancelStepRing(appContext, alarm.id)
            AlarmScheduler.cancelAwakeCheck(appContext, alarm.id)
            AlarmScheduler.cancelAwakeCheckTimeout(appContext, alarm.id)
            // The awake_checks row (if any) cascades away with the alarm via its foreign key.
            withContext(Dispatchers.IO) { repo.deleteAlarm(alarm.id) }
        }
    }
}
