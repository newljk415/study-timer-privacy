package com.newljk415.studytimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.newljk415.studytimer.domain.Durations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val durations: Durations = Durations.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
    /** 마지막으로 확인된 광고 제거 권한(오프라인·일시적 조회 실패 시 사용). */
    val removeAdsCached: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    private object K {
        val focus = intPreferencesKey("focus_min")
        val brk = intPreferencesKey("break_min")
        val theme = stringPreferencesKey("theme")
        val keepOn = booleanPreferencesKey("keep_screen_on")
        val sound = booleanPreferencesKey("sound")
        val vibrate = booleanPreferencesKey("vibrate")
        val notifAsked = booleanPreferencesKey("notif_asked")
        val removeAds = booleanPreferencesKey("remove_ads")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            durations = Durations(
                (p[K.focus] ?: 25).coerceIn(Durations.FOCUS_RANGE),
                (p[K.brk] ?: 5).coerceIn(Durations.BREAK_RANGE),
            ),
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            keepScreenOn = p[K.keepOn] ?: true,
            sound = p[K.sound] ?: true,
            vibrate = p[K.vibrate] ?: true,
            notificationPermissionAsked = p[K.notifAsked] ?: false,
            removeAdsCached = p[K.removeAds] ?: false,
        )
    }

    suspend fun current() = settings.first()

    /** 범위를 벗어나면 저장하지 않고 false. */
    suspend fun setDurations(focus: Int, brk: Int): Boolean {
        if (focus !in Durations.FOCUS_RANGE || brk !in Durations.BREAK_RANGE) return false
        store.edit { it[K.focus] = focus; it[K.brk] = brk }
        return true
    }

    suspend fun setTheme(t: ThemeMode) = store.edit { it[K.theme] = t.name }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[K.keepOn] = v }
    suspend fun setSound(v: Boolean) = store.edit { it[K.sound] = v }
    suspend fun setVibrate(v: Boolean) = store.edit { it[K.vibrate] = v }
    suspend fun setNotificationAsked() = store.edit { it[K.notifAsked] = true }
    suspend fun setRemoveAdsCached(v: Boolean) = store.edit { it[K.removeAds] = v }
}
