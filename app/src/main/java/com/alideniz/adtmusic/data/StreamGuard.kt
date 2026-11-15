package com.alideniz.adtmusic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** YouTube bu ağı/oturumu geçici olarak sınırladığında atılır. */
class YouTubeLimitedException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class GuardState(
    /** Bu zamana kadar arka plan istekleri (ön yükleme, bildirim taraması…) yapılmaz. */
    val limitedUntil: Long = 0,
    val strikes: Int = 0,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val lastOkAt: Long = 0,
) {
    val limited: Boolean get() = System.currentTimeMillis() < limitedUntil
}

/**
 * YouTube'a giden istekleri korur.
 *
 * YouTube kısa sürede çok fazla istek gören bir IP'yi bir süre "bot" sanıp engeller.
 * Engel görülünce uygulama her şarkıyı tek tek deneyerek durumu kötüleştirmek yerine
 * geri çekilir: arka plan isteklerini durdurur, artan aralıklarla yeniden dener ve
 * yeni bir oturum (visitorData) açar.
 */
class StreamGuard {
    private val _state = MutableStateFlow(GuardState())
    val state: StateFlow<GuardState> = _state.asStateFlow()

    /** Engel algılandı: bekleme süresi her seferinde uzar (1 → 3 → 8 → 15 dk). */
    @Synchronized
    fun reportLimited(reason: String) {
        val s = _state.value
        val strikes = s.strikes + 1
        val waitMin = when (strikes) { 1 -> 1; 2 -> 3; 3 -> 8; else -> 15 }
        _state.value = s.copy(
            limitedUntil = System.currentTimeMillis() + waitMin * 60_000L,
            strikes = strikes,
            lastError = reason,
            lastErrorAt = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun reportOk() {
        val s = _state.value
        if (s.strikes == 0 && s.limitedUntil == 0L && System.currentTimeMillis() - s.lastOkAt < 60_000) return
        _state.value = s.copy(limitedUntil = 0, strikes = 0, lastOkAt = System.currentTimeMillis())
    }

    /** Kullanıcı "tekrar dene" dediğinde bekleme kaldırılır (sayaç korunur). */
    @Synchronized
    fun clearWait() {
        _state.value = _state.value.copy(limitedUntil = 0)
    }

    /** Arka plan işi yapılabilir mi? */
    val backgroundAllowed: Boolean get() = !_state.value.limited

    companion object {
        /** Bir hatanın "YouTube sınırladı" türünden olup olmadığını anlar. */
        fun isLimit(t: Throwable?): Boolean {
            var e = t
            var depth = 0
            while (e != null && depth < 8) {
                if (e is YouTubeLimitedException) return true
                if (e is org.schabi.newpipe.extractor.exceptions.ReCaptchaException) return true
                val msg = e.message.orEmpty().lowercase()
                if ("bot" in msg && ("sign in" in msg || "oturum" in msg || "confirm" in msg || "onayla" in msg)) return true
                if ("429" in msg && "too many" in msg) return true
                e = e.cause
                depth++
            }
            return false
        }
    }
}
