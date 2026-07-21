package com.worstalarm.clock.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-wide settings (DataStore-backed): global alarm tone, intro-seen flag.
 *
 * Stored in **device-protected storage** for the same reason as the Room database (see
 * [AppDatabase]): the ring path reads the global tone during Direct Boot — after a reboot
 * but before the first unlock — and credential-protected storage is sealed until then.
 * A missing/unreadable custom tone falls back to the system alarm sound at ring time, so a
 * locked-boot alarm still makes noise even if the picked file can't be resolved yet.
 */
class SettingsRepository(context: Context) {

    private val dataStore = run {
        val appContext = context.applicationContext
        val deviceContext = appContext.createDeviceProtectedStorageContext()
        PreferenceDataStoreFactory.create(
            produceFile = {
                val target = deviceContext.preferencesDataStoreFile(STORE_NAME)
                // Best-effort one-time migration: preserve the global tone / intro flag for
                // users upgrading from a build that kept settings in credential storage.
                if (!target.exists()) {
                    val source = appContext.preferencesDataStoreFile(STORE_NAME)
                    if (source.exists()) runCatching { source.copyTo(target) }
                }
                target
            }
        )
    }

    /** True once the user has checked "Do not show this again" on the intro dialog. */
    val introSeen: Flow<Boolean> =
        dataStore.data.map { it[INTRO_SEEN] ?: false }

    /** Global alarm sound as a content:// URI string; null = system default alarm tone. */
    val globalRingtoneUri: Flow<String?> =
        dataStore.data.map { it[GLOBAL_RINGTONE] }

    suspend fun setIntroSeen(seen: Boolean) {
        dataStore.edit { it[INTRO_SEEN] = seen }
    }

    suspend fun setGlobalRingtoneUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(GLOBAL_RINGTONE) else it[GLOBAL_RINGTONE] = uri
        }
    }

    private companion object {
        const val STORE_NAME = "settings"
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val GLOBAL_RINGTONE = stringPreferencesKey("global_ringtone_uri")
    }
}
