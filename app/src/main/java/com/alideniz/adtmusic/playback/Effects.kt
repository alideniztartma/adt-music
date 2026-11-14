package com.alideniz.adtmusic.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.Player
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
 * Yerleşik ekolayzer, bas güçlendirici ve sanal surround.
 * Ayarlar cihazda saklanır; çalma oturumuna bağlanınca uygulanır.
 */
class AudioEffects(private val settings: Settings) {

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands: StateFlow<List<EqBand>> = _bands.asStateFlow()

    private val _levels = MutableStateFlow<List<Int>>(emptyList())
    val levels: StateFlow<List<Int>> = _levels.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        runCatching {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            val range = eq.bandLevelRange
            _bands.value = (0 until eq.numberOfBands.toInt()).map { i ->
                EqBand(i, eq.getCenterFreq(i.toShort()) / 1000, range[0].toInt(), range[1].toInt())
            }
            _available.value = true
        }
        runCatching { bass = BassBoost(0, audioSessionId) }
        runCatching { virtualizer = Virtualizer(0, audioSessionId) }
        runCatching { loudness = LoudnessEnhancer(audioSessionId) }
        val saved = settings.eqBands.value.split(",").mapNotNull { it.trim().toIntOrNull() }
        _levels.value = if (saved.size == _bands.value.size) saved else List(_bands.value.size) { 0 }
        apply()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null; bass = null; virtualizer = null; loudness = null
    }

    private fun apply() {
        val enabled = settings.eqEnabled.value
        equalizer?.let { eq ->
            runCatching {
                eq.setEnabled(enabled)
                _levels.value.forEachIndexed { i, level ->
                    if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), level.toShort())
                }
            }
        }
        bass?.let { b ->
            runCatching {
                b.setEnabled(enabled && settings.bassBoost.value > 0)
                if (b.strengthSupported) b.setStrength(settings.bassBoost.value.toShort())
            }
        }
        loudness?.let { l ->
            runCatching {
                val boost = settings.volumeBoost.value
                l.setTargetGain(boost)
                l.setEnabled(boost > 0)
            }
        }
        virtualizer?.let { v ->
            runCatching {
                v.setEnabled(enabled && settings.virtualizer.value > 0)
                if (v.strengthSupported) v.setStrength(settings.virtualizer.value.toShort())
            }
        }
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
    private var player: Player? = null
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (_remaining.value == END_OF_SONG && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                player?.pause()
                cancel()
            }
        }
    }

    fun attach(p: Player) {
        player?.removeListener(listener)
        player = p
        p.addListener(listener)
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        cancel()
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
        val start = p.volume
        for (i in 10 downTo 0) {
            p.volume = start * i / 10f
            delay(250)
        }
        p.pause()
        p.volume = start
    }

    companion object {
        const val END_OF_SONG = -1L
    }
}
