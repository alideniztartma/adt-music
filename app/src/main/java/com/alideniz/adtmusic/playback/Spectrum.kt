package com.alideniz.adtmusic.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Çalan müziğin anlık frekans dağılımı (görselleştirici ve ritim animasyonu için).
 * Yalnızca ekranda bir görselleştirici varken çalışır (referans sayımlı), pil harcamaz.
 */
class Spectrum(private val context: Context) {

    @Volatile private var sessionId = 0
    private var visualizer: Visualizer? = null
    private var users = 0

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    /** 0..1 arası 16 bant (bastan tize). */
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** 0..1 arası anlık bas/vuruş seviyesi. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val smooth = FloatArray(BANDS)
    private var beat = 0f
    private var avgBass = 0.15f

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun setSession(id: Int) {
        sessionId = id
        if (users > 0) restart()
    }

    @Synchronized
    fun acquire() {
        users++
        if (users == 1) restart()
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    @Synchronized
    private fun restart() {
        stop()
        val id = sessionId
        if (id <= 0 || !hasPermission()) return
        runCatching {
            val v = Visualizer(id)
            v.setEnabled(false)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) process(fft, samplingRate / 1000)
                    }
                },
                Visualizer.getMaxCaptureRate(), false, true,
            )
            v.setEnabled(true)
            visualizer = v
            _active.value = true
        }.onFailure { stop() }
    }

    @Synchronized
    private fun stop() {
        runCatching { visualizer?.setEnabled(false) }
        runCatching { visualizer?.release() }
        visualizer = null
        _active.value = false
        smooth.fill(0f)
        _bands.value = FloatArray(BANDS)
        _level.value = 0f
    }

    /** FFT baytlarını logaritmik 16 banda çevirir ve yumuşatır. */
    private fun process(fft: ByteArray, sampleRateHz: Int) {
        val bins = fft.size / 2
        if (bins < 8) return
        val nyquist = max(sampleRateHz / 2f, 1f)
        val mags = FloatArray(bins)
        mags[0] = kotlin.math.abs(fft[0].toFloat())
        for (k in 1 until bins) {
            mags[k] = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
        }
        val out = FloatArray(BANDS)
        val minHz = 40f
        val maxHz = minOf(16_000f, nyquist)
        for (b in 0 until BANDS) {
            val lo = minHz * (maxHz / minHz).pow(b / BANDS.toFloat())
            val hi = minHz * (maxHz / minHz).pow((b + 1) / BANDS.toFloat())
            val from = ((lo / nyquist) * bins).toInt().coerceIn(1, bins - 1)
            val to = ((hi / nyquist) * bins).toInt().coerceIn(from + 1, bins)
            var peak = 0f
            for (k in from until to) if (mags[k] > peak) peak = mags[k]
            // dB ölçeği → 0..1
            val db = 20f * log10(peak + 1f)
            val v = ((db - 6f) / 36f).coerceIn(0f, 1f)
            // Yükselişte hızlı, düşüşte yavaş
            smooth[b] = if (v > smooth[b]) smooth[b] + (v - smooth[b]) * 0.6f else smooth[b] * 0.82f + v * 0.18f
            out[b] = smooth[b]
        }
        _bands.value = out
        val bass = (out[0] + out[1] + out[2] + out[3]) / 4f
        avgBass = avgBass * 0.96f + bass * 0.04f
        val hit = ((bass - avgBass) * 3f).coerceIn(0f, 1f)
        beat = if (hit > beat) hit else beat * 0.86f
        _level.value = (beat * 0.75f + bass * 0.25f).coerceIn(0f, 1f)
    }

    companion object {
        const val BANDS = 16
    }
}
