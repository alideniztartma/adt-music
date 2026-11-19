package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EqBand(val index: Int, val centerHz: Int, val minMb: Int, val maxMb: Int)

/**
 * Ekolayzer, bas güçlendirici ve ses yükseltme — masaüstünde VLC'nin 10 bantlı
 * ekolayzeriyle. Sanal surround masaüstünde yok (ayar saklanır, etkisi olmaz).
 */
class AudioEffects(private val settings: Settings) {

    private var engine: VlcEngine? = null
    private var fade = 1f

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands: StateFlow<List<EqBand>> = _bands.asStateFlow()

    private val _levels = MutableStateFlow<List<Int>>(emptyList())
    val levels: StateFlow<List<Int>> = _levels.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun attach(e: VlcEngine) {
        engine = e
        val freqs = e.bandFrequencies
        _bands.value = freqs.mapIndexed { i, hz -> EqBand(i, hz.toInt(), -MAX_MB, MAX_MB) }
        _available.value = freqs.isNotEmpty()
        val saved = settings.eqBands.value.split(",").mapNotNull { it.trim().toIntOrNull() }
        _levels.value = if (saved.size == _bands.value.size) saved else List(_bands.value.size) { 0 }
        apply()
    }

    fun release() {
        engine = null
    }

    private fun apply() {
        val e = engine ?: return
        val enabled = settings.eqEnabled.value
        if (enabled && _bands.value.isNotEmpty()) {
            val bass = settings.bassBoost.value / 1000f * 8f // en fazla +8 dB
            val amps = FloatArray(_bands.value.size) { i ->
                val base = (_levels.value.getOrNull(i) ?: 0) / 100f
                val bassAdd = when (i) { 0 -> bass; 1 -> bass * 0.8f; 2 -> bass * 0.45f; 3 -> bass * 0.15f; else -> 0f }
                base + bassAdd
            }
            e.setEqualizer(amps, preamp = if (bass > 0) -bass * 0.4f else 0f)
        } else {
            e.setEqualizer(null)
        }
        applyVolume()
    }

    /** Uygulama sesi × ses yükseltme × (uyku zamanlayıcısı kısması). */
    fun applyVolume(fadeFactor: Float = fade) {
        fade = fadeFactor
        val e = engine ?: return
        val user = com.alideniz.adtmusic.appContainerOrNull?.player?.volume?.value ?: 1f
        val boost = 1f + settings.volumeBoost.value / 1000f // 0..1000 mb → x1..x2
        e.setVolume((100f * user * boost * fade).toInt())
    }

    fun setEnabled(enabled: Boolean) {
        settings.eqEnabled.set(enabled)
        apply()
    }

    fun setBand(index: Int, levelMb: Int) {
        val list = _levels.value.toMutableList()
        if (index !in list.indices) return
        list[index] = levelMb
        _levels.value = list
        settings.eqBands.set(list.joinToString(","))
        settings.eqPreset.set("Özel")
        apply()
    }

    fun setBass(strength: Int) {
        settings.bassBoost.set(strength.coerceIn(0, 1000))
        apply()
    }

    /** Ses yükseltme (EQ kapalı olsa da çalışır), milibel 0..1000. */
    fun setVolumeBoost(mb: Int) {
        settings.volumeBoost.set(mb.coerceIn(0, 1000))
        apply()
    }

    fun setVirtualizer(strength: Int) {
        settings.virtualizer.set(strength.coerceIn(0, 1000))
        apply()
    }

    fun applyPreset(name: String) {
        val shape = PRESETS[name] ?: return
        val bands = _bands.value
        if (bands.isEmpty()) {
            settings.eqPreset.set(name)
            return
        }
        // Ön ayar 5 noktadan oluşur; cihazdaki bant sayısına göre ara değer bulunur.
        val levels = bands.mapIndexed { i, band ->
            val pos = if (bands.size == 1) 0f else i.toFloat() / (bands.size - 1) * (shape.size - 1)
            val lo = pos.toInt().coerceIn(0, shape.size - 1)
            val hi = (lo + 1).coerceAtMost(shape.size - 1)
            val t = pos - lo
            val v = shape[lo] * (1 - t) + shape[hi] * t
            (v * band.maxMb).toInt().coerceIn(band.minMb, band.maxMb)
        }
        _levels.value = levels
        settings.eqBands.set(levels.joinToString(","))
        settings.eqPreset.set(name)
        apply()
    }

    companion object {
        const val MAX_MB = 1500

        /** -1..1 arası, düşükten yükseğe 5 nokta. */
        val PRESETS: Map<String, FloatArray> = linkedMapOf(
            "Düz" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
            "Bas+" to floatArrayOf(0.7f, 0.45f, 0.05f, 0f, 0f),
            "Tiz+" to floatArrayOf(0f, 0f, 0.05f, 0.4f, 0.65f),
            "Vokal" to floatArrayOf(-0.2f, 0.1f, 0.5f, 0.35f, 0f),
            "Pop" to floatArrayOf(-0.1f, 0.25f, 0.45f, 0.2f, -0.1f),
            "Rock" to floatArrayOf(0.5f, 0.25f, -0.15f, 0.25f, 0.5f),
            "Elektronik" to floatArrayOf(0.55f, 0.3f, 0f, 0.3f, 0.5f),
            "Akustik" to floatArrayOf(0.3f, 0.15f, 0.1f, 0.25f, 0.2f),
            "Rap" to floatArrayOf(0.6f, 0.4f, 0f, 0.15f, 0.3f),
            "Gece" to floatArrayOf(0.2f, 0.1f, 0f, -0.25f, -0.45f),
        )
    }
}

/** Uyku zamanlayıcısı: belirli bir süre sonra ya da şarkı bitince durdurur. */
class SleepTimer(private val scope: CoroutineScope) {

    /** Kalan süre (ms). null = kapalı, -1 = şarkı bitince. */
    private val _remaining = MutableStateFlow<Long?>(null)
    val remaining: StateFlow<Long?> = _remaining.asStateFlow()

    private var job: Job? = null
    private var player: PlayerConnection? = null

    fun attach(p: PlayerConnection) {
        player = p
    }

    fun detach() {
        player = null
        cancel()
    }

    /** Şarkı kendiliğinden bittiğinde çağrılır; "şarkı bitince" ayarlıysa true döner. */
    fun consumeEndOfSong(): Boolean {
        if (_remaining.value != END_OF_SONG) return false
        cancel()
        return true
    }

    fun start(minutes: Int) {
        job?.cancel()
        val end = System.currentTimeMillis() + minutes * 60_000L
        _remaining.value = minutes * 60_000L
        job = scope.launch {
            while (true) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                _remaining.value = left
                delay(1000)
            }
            fadeOutAndPause()
            _remaining.value = null
        }
    }

    fun endOfSong() {
        job?.cancel()
        _remaining.value = END_OF_SONG
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remaining.value = null
    }

    private suspend fun fadeOutAndPause() {
        val p = player ?: return
        try {
            for (i in 10 downTo 0) {
                p.fadeVolume(i / 10f)
                delay(250)
            }
            p.pause()
        } finally {
            p.fadeVolume(1f) // iptal edilse de ses kısık kalmasın
        }
    }

    companion object {
        const val END_OF_SONG = -1L
    }
}
