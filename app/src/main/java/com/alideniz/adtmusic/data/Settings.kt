package com.alideniz.adtmusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uygulama ayarları. Hepsi yalnızca cihazda (SharedPreferences) saklanır. */
class Settings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("adt_settings", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, private val default: T) {
        private val state = MutableStateFlow(read())
        val flow: StateFlow<T> = state.asStateFlow()
        val value: T get() = state.value

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = when (default) {
            is Boolean -> prefs.getBoolean(key, default) as T
            is Int -> prefs.getInt(key, default) as T
            is Long -> prefs.getLong(key, default) as T
            is Float -> prefs.getFloat(key, default) as T
            is String -> (prefs.getString(key, default) ?: default) as T
            else -> error("Desteklenmeyen tür")
        }

        fun set(v: T) {
            prefs.edit().apply {
                when (v) {
                    is Boolean -> putBoolean(key, v)
                    is Int -> putInt(key, v)
                    is Long -> putLong(key, v)
                    is Float -> putFloat(key, v)
                    is String -> putString(key, v)
                }
            }.apply()
            state.value = v
        }
    }

    /** "system", "light" veya "dark" */
    val theme = Pref("theme", "system")
    val dynamicColor = Pref("dynamic_color", true)
    /** "high", "normal" veya "low" */
    val audioQuality = Pref("audio_quality", "high")
    val autoplay = Pref("autoplay", true)
    val skipSilence = Pref("skip_silence", false)
    val cacheSizeMb = Pref("cache_size_mb", 1024)
    val downloadWifiOnly = Pref("download_wifi_only", false)
    val saveSearchHistory = Pref("save_search_history", true)
    val pauseHistory = Pref("pause_history", false)
    val lyricsTextSize = Pref("lyrics_text_size", 26)
    val resumeOnStart = Pref("resume_on_start", true)

    // Hız ve ton (kalıcı)
    val playbackSpeed = Pref("playback_speed", 1f)
    /** Yarım ton cinsinden ton kaydırma (-12..+12). */
    val pitchSemitones = Pref("pitch_semitones", 0f)

    // Ekolayzer
    val eqEnabled = Pref("eq_enabled", false)
    val eqPreset = Pref("eq_preset", "Düz")
    /** Bant seviyeleri milibel cinsinden, virgülle ayrılmış. */
    val eqBands = Pref("eq_bands", "")
    val bassBoost = Pref("bass_boost", 0)
    val virtualizer = Pref("virtualizer", 0)

    // Kaldığı yerden devam
    val lastQueue = Pref("last_queue", "")
    val lastIndex = Pref("last_index", 0)
    val lastPosition = Pref("last_position", 0L)
}
