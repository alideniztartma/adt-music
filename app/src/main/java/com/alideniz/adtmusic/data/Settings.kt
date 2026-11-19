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
    val mobileDataSaver = Pref("mobile_data_saver", false)
    /** Mobil veride kalite: "same" (Wi-Fi ile aynı), "high", "normal", "low" */
    val mobileQuality = Pref("mobile_quality", "same")
    val dataMonth = Pref("data_month", "")
    val dataMobileBytes = Pref("data_mobile_bytes", 0L)
    val dataWifiBytes = Pref("data_wifi_bytes", 0L)
    /** Bluetooth ses cihazı bağlanınca kaldığı yerden çal. */
    val btAutoPlay = Pref("bt_auto_play", false)
    /** YouTube oturumu (hafif çözücü için, 12 saat yeniden kullanılır). */
    val ytVisitorData = Pref("yt_visitor_data", "")
    val ytVisitorAt = Pref("yt_visitor_at", 0L)
    val preloadNext = Pref("preload_next", true)
    val pauseOnDisconnect = Pref("pause_on_disconnect", true)
    val autoplay = Pref("autoplay", true)
    /** Şarkı sonunda yumuşak geçiş süresi (saniye, 0 = kapalı). */
    val crossfadeSec = Pref("crossfade_sec", 0)
    val normalizeVolume = Pref("normalize_volume", false)
    val headsetGestures = Pref("headset_gestures", true)
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

    // Kişiselleştirme
    val accentColor = Pref("accent_color", 0xFFFF5A1F.toInt())
    val pureBlack = Pref("pure_black", false)
    /** "sharp", "soft" veya "round" */
    val cornerStyle = Pref("corner_style", "sharp")
    val fontScale = Pref("font_scale", 1f)
    val reduceMotion = Pref("reduce_motion", false)
    val highRefreshRate = Pref("high_refresh_rate", true)
    /** "high" (en yüksek, ör. 144 Hz) veya "low" (60 Hz) */
    val frameRate = Pref("frame_rate", "high")
    val showFps = Pref("show_fps", false)
    /** "tint", "plain" veya "blur" */
    val playerBackground = Pref("player_background", "tint")
    /** "fill", "word", "glow" veya "focus" */
    val lyricsAnimation = Pref("lyrics_animation", "word")
    /** "start" veya "center" */
    val lyricsAlign = Pref("lyrics_align", "start")
    val keepScreenOnLyrics = Pref("keep_screen_on_lyrics", true)
    /** "home", "search" veya "library" */
    val startTab = Pref("start_tab", "home")
    val contentCountry = Pref("content_country", "TR")
    val autoDownloadLiked = Pref("auto_download_liked", false)
    val searchSuggestions = Pref("search_suggestions", true)
    val haptics = Pref("haptics", true)
    val homeShowRecent = Pref("home_show_recent", true)
    val homeShowFollowed = Pref("home_show_followed", true)
    val homeShowTrending = Pref("home_show_trending", true)
    val homeShowAlbums = Pref("home_show_albums", true)
    /** Ses yükseltme, milibel (0..1000). */
    val volumeBoost = Pref("volume_boost", 0)
    val miniPlayerProgress = Pref("mini_player_progress", true)

    // Rozetler, profil, bildirimler
    val unlockedBadges = Pref("unlocked_badges", "")
    val profileName = Pref("profile_name", "")
    val releaseNotifications = Pref("release_notifications", true)
    val weeklyNotification = Pref("weekly_notification", true)
    val lastWeeklyNotice = Pref("last_weekly_notice", 0L)
    /** sanatçı adı → kanal adresi (JSON) */
    val artistUrls = Pref("artist_urls", "{}")
    /** sanatçı adı → son görülen video kimliği (JSON) */
    val artistLastSeen = Pref("artist_last_seen", "{}")
    val watcherCursor = Pref("watcher_cursor", 0)

    // Oynatıcı düzeni
    /** "minimal", "apple", "vinyl", "cassette", "glass", "neon", "ipod", "brutal" */
    val playerStyle = Pref("player_style", "minimal")
    /** "large", "medium", "small" veya "none" (kapaksız) */
    val coverSize = Pref("cover_size", "large")
    /** "button" (ayrı ekran), "preview" (kapak altında canlı satır) veya "auto" (otomatik aç) */
    val lyricsPlacement = Pref("lyrics_placement", "button")
    val playerVolumeBar = Pref("player_volume_bar", true)
    /** Uygulama simgesi vurgu rengini izlesin. */
    val iconFollowsAccent = Pref("icon_follows_accent", true)

    // Yeni oynatıcılar ve animasyonlar
    /** Şarkı değişince kapak geçişi: "slide", "fade", "zoom", "flip", "spin" */
    val coverTransition = Pref("cover_transition", "slide")
    /** Sayfa geçişi: "slide", "fade", "zoom", "rise", "none" */
    val pageTransition = Pref("page_transition", "slide")
    /** Oynatıcının açılışı: "slide", "fade", "zoom", "pop" */
    val playerOpenAnimation = Pref("player_open_animation", "slide")
    /** Düğmeye basınca: "shrink", "bounce", "pulse", "none" */
    val pressAnimation = Pref("press_animation", "shrink")
    /** Tüm animasyonların hızı (0.5 = yavaş, 2 = hızlı) */
    val animationSpeed = Pref("animation_speed", 1f)
    /** Oynatıcı üstünde efekt: "off", "snow", "stars", "bubbles", "confetti", "hearts" */
    val particleEffect = Pref("particle_effect", "off")
    /** Efekt yoğunluğu (10..100) */
    val particleAmount = Pref("particle_amount", 40)
    /** İlerleme çubuğu (yeni oynatıcılarda): "line", "thick", "wave", "dots", "pill" */
    val seekStyle = Pref("seek_style", "line")
    /** Beğenince: "pop", "burst", "none" */
    val likeAnimation = Pref("like_animation", "burst")
    /** Arka plan hareketi: "off", "slow", "normal", "fast" */
    val backgroundMotion = Pref("background_motion", "normal")
    val titleMarquee = Pref("title_marquee", true)
    /** Sıradaki şarkı satırı (yeni oynatıcılarda) */
    val showUpNext = Pref("show_up_next", true)
    /** Alt menüde yazılar */
    val navLabels = Pref("nav_labels", true)
    /** Plak dönüş hızı: "33", "45", "78" */
    val vinylSpeed = Pref("vinyl_speed", "33")
    /** Plak zemini: "wood", "dark", "accent" */
    val vinylBase = Pref("vinyl_base", "wood")
    /** Kaset rengi: "black", "clear", "orange", "blue", "pink" */
    val cassetteColor = Pref("cassette_color", "black")
    /** Cam oynatıcıda bulanıklık (0..100) */
    val glassBlur = Pref("glass_blur", 60)
    /** Neon rengi: "accent", "pink", "cyan", "lime", "purple" */
    val neonColor = Pref("neon_color", "accent")
    /** iPod kasası: "white", "black", "silver", "red", "blue" */
    val ipodColor = Pref("ipod_color", "white")
    /** Brütalist renk: "yellow", "pink", "lime", "blue", "accent" */
    val brutalColor = Pref("brutal_color", "yellow")

    // Söz görünümü
    /** "extrabold", "bold" veya "medium" */
    val lyricsWeight = Pref("lyrics_weight", "extrabold")
    /** "tight", "normal" veya "wide" */
    val lyricsSpacing = Pref("lyrics_spacing", "normal")
    val lyricsUppercase = Pref("lyrics_uppercase", false)
    /** "accent" veya "ink": çalan satırın vurgu rengi */
    val lyricsHighlight = Pref("lyrics_highlight", "accent")
    val lyricsBlur = Pref("lyrics_blur", false)

    // Oynatıcı görselleri
    /** "off", "bars" veya "wave" */
    val visualizerStyle = Pref("visualizer_style", "off")
    /** "off", "breathe" veya "beat" */
    val coverAnimation = Pref("cover_animation", "breathe")

    // Güncelleme
    val autoUpdateCheck = Pref("auto_update_check", true)
    val lastUpdateCheck = Pref("last_update_check", 0L)

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
