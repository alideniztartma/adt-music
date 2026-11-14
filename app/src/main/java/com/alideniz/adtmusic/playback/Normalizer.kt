@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Ses dengeleme: şarkılar arasındaki ses seviyesi farkını yumuşakça eşitler.
 *
 * Kısa pencerelerde ortalama ses gücü (RMS) ölçülür, hedef seviyeye göre kazanç
 * hesaplanır. Kazanç ani değil yavaş değişir (sesin "pompalanmasını" önler) ve
 * bir sınırlayıcı kırpılmayı engeller. Kapalıyken ses olduğu gibi geçer.
 */
class Normalizer : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var channels = 2
    private var gain = 1f
    private var envelope = TARGET_RMS

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (!enabled) {
            out.put(input)
            out.flip()
            gain = 1f
            return
        }
        val frames = remaining / (2 * channels)
        val start = input.position()

        // 1) Bu bloğun RMS değeri
        var sum = 0.0
        for (i in 0 until frames * channels) {
            val s = input.getShort(start + i * 2) / 32768f
            sum += s * s
        }
        val rms = sqrt(sum / (frames * channels).coerceAtLeast(1)).toFloat()

        // 2) Zarf: sessizlikte hedefi bozmamak için çok kısık blokları yok say
        if (rms > SILENCE) {
            val coef = if (rms > envelope) ATTACK else RELEASE
            envelope += (rms - envelope) * coef
        }
        val targetGain = (TARGET_RMS / envelope.coerceAtLeast(1e-4f)).coerceIn(MIN_GAIN, MAX_GAIN)
        val step = (targetGain - gain) / frames.coerceAtLeast(1)

        // 3) Kazancı uygula + yumuşak sınırlayıcı
        var g = gain
        for (f in 0 until frames) {
            g += step
            for (ch in 0 until channels) {
                val idx = start + (f * channels + ch) * 2
                var v = input.getShort(idx) / 32768f * g
                // yumuşak kırpma (tanh benzeri)
                if (v > 0.9f) v = 0.9f + (v - 0.9f) / (1f + (v - 0.9f) * 10f)
                else if (v < -0.9f) v = -0.9f + (v + 0.9f) / (1f - (v + 0.9f) * 10f)
                out.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        gain = g
        input.position(input.limit())
        out.flip()
    }

    override fun onFlush() {
        // Şarkı değişince kazancı sıfırlamayız: geçiş daha doğal olur.
    }

    override fun onReset() {
        gain = 1f
        envelope = TARGET_RMS
    }

    companion object {
        private const val TARGET_RMS = 0.12f   // ≈ -18 dBFS
        private const val SILENCE = 0.005f
        private const val ATTACK = 0.25f
        private const val RELEASE = 0.04f
        private const val MIN_GAIN = 0.35f
        private const val MAX_GAIN = 3.0f
    }
}
