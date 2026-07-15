package com.worstalarm.clock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App-wide settings (DataStore-backed): global alarm tone, intro-seen flag. */
class SettingsRepository(private val context: Context) {

    /** True once the user has checked "Do not show this again" on the intro dialog. */
    val introSeen: Flow<Boolean> =
        context.dataStore.data.map { it[INTRO_SEEN] ?: false }

    /** Global alarm sound as a content:// URI string; null = system default alarm tone. */
    val globalRingtoneUri: Flow<String?> =
        context.dataStore.data.map { it[GLOBAL_RINGTONE] }

    suspend fun setIntroSeen(seen: Boolean) {
        context.dataStore.edit { it[INTRO_SEEN] = seen }
    }

    suspend fun setGlobalRingtoneUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(GLOBAL_RINGTONE) else it[GLOBAL_RINGTONE] = uri
        }
    }

    private companion object {
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val GLOBAL_RINGTONE = stringPreferencesKey("global_ringtone_uri")
    }
}
