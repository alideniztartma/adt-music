package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.platform.isWindows
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.LinuxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.OsxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File

/**
 * libVLC üzerinden ses çalan motor (ExoPlayer'ın masaüstü karşılığı).
 *
 * VLC; Windows paketinde uygulamayla birlikte gelir, Linux'ta sistemdeki VLC kullanılır.
 * Ton (pitch) değiştirmek için libVLC'de çalışırken ayar yapılamadığından, ton değişince
 * motor yeniden kurulur ve şarkı kaldığı yerden sürer.
 */
class VlcEngine(private val events: Listener) {

    interface Listener {
        fun onPlaying()
        fun onPaused()
        fun onBuffering()
        fun onFinished()
        fun onError()
    }

    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null
    private var semitones = 0f

    /** VLC bulunamadıysa açıklama. */
    var unavailableReason: String? = null
        private set

    val available: Boolean get() = player != null

    private var lastMrl: String? = null
    private var lastOptions: Array<String> = emptyArray()

    var bandFrequencies: List<Float> = emptyList()
        private set

    private val adapter = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) = events.onPlaying()
        override fun paused(mediaPlayer: MediaPlayer) = events.onPaused()
        override fun opening(mediaPlayer: MediaPlayer) = events.onBuffering()
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            if (newCache < 100f) events.onBuffering()
        }
        override fun finished(mediaPlayer: MediaPlayer) = events.onFinished()
        override fun error(mediaPlayer: MediaPlayer) = events.onError()
    }

    fun start(initialSemitones: Float) {
        semitones = initialSemitones
        build()
    }

    private fun build() {
        runCatching {
            val args = mutableListOf(
                "--no-video",
                "--quiet",
                "--intf=dummy",
                "--network-caching=1500",
                "--http-reconnect",
                "--audio-filter=scaletempo_pitch",
                "--pitch-shift=${"%.2f".format(java.util.Locale.US, semitones)}",
            )
            val f = MediaPlayerFactory(discovery(), args)
            val p = f.mediaPlayers().newMediaPlayer()
            p.events().addMediaPlayerEventListener(adapter)
            factory = f
            player = p
            bandFrequencies = runCatching { f.equalizer().bands() }.getOrDefault(emptyList())
            unavailableReason = null
        }.onFailure {
            unavailableReason = if (isWindows) {
                "Ses motoru (VLC) yüklenemedi. Uygulamayı yeniden kurmayı dene."
            } else {
                "Ses için VLC gerekli. Kur: sudo apt install vlc  (ya da dağıtımının paket yöneticisiyle)"
            }
        }
    }

    private fun discovery(): NativeDiscovery {
        val bundled = System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") }
        val strategies = mutableListOf<NativeDiscoveryStrategy>()
        if (bundled != null && File(bundled, if (isWindows) "libvlc.dll" else "libvlc.so").exists()) {
            strategies += BundledStrategy(bundled)
        }
        strategies += LinuxNativeDiscoveryStrategy()
        strategies += WindowsNativeDiscoveryStrategy()
        strategies += OsxNativeDiscoveryStrategy()
        return NativeDiscovery(*strategies.toTypedArray())
    }

    /** Uygulamayla gelen VLC klasörü. */
    private class BundledStrategy(private val dir: File) : NativeDiscoveryStrategy {
        private val windows = WindowsNativeDiscoveryStrategy()
        override fun supported() = true
        override fun discover(): String = dir.absolutePath
        override fun onFound(path: String) = true
        override fun onSetPluginPath(path: String): Boolean =
            if (isWindows) runCatching { windows.onSetPluginPath(path) }.getOrDefault(true) else true
    }

    // ------------------------------------------------------------ kontrol

    fun play(mrl: String, options: Array<String>, startMs: Long, paused: Boolean = false) {
        lastMrl = mrl
        lastOptions = options
        val p = player ?: return
        val all = if (startMs > 0) options + ":start-time=${"%.3f".format(java.util.Locale.US, startMs / 1000.0)}" else options
        if (paused) p.media().startPaused(mrl, *all) else p.media().play(mrl, *all)
        applyRate()
        applyVolume()
        applyEqualizer()
    }

    fun pause() { player?.controls()?.setPause(true) }
    fun resume() { player?.controls()?.setPause(false) }
    fun stop() { player?.controls()?.stop() }

    fun seek(ms: Long) { player?.controls()?.setTime(ms.coerceAtLeast(0)) }

    val time: Long get() = player?.status()?.time()?.coerceAtLeast(0) ?: 0
    val length: Long get() = player?.status()?.length()?.coerceAtLeast(0) ?: 0
    val isPlaying: Boolean get() = player?.status()?.isPlaying == true

    // ---- hız, ton, ses, ekolayzer

    private var rate = 1f
    private var volume = 100
    private var eqAmps: FloatArray? = null
    private var eqPreamp = 0f

    fun setRate(r: Float) {
        rate = r
        applyRate()
    }

    private fun applyRate() { player?.controls()?.setRate(rate) }

    /** 0..200 (100 = normal, üstü yükseltme). */
    fun setVolume(v: Int) {
        volume = v.coerceIn(0, 200)
        applyVolume()
    }

    private fun applyVolume() { player?.audio()?.setVolume(volume) }

    /** null = ekolayzer kapalı. Değerler dB (-20..20). */
    fun setEqualizer(amps: FloatArray?, preamp: Float = 0f) {
        eqAmps = amps
        eqPreamp = preamp
        applyEqualizer()
    }

    private fun applyEqualizer() {
        val p = player ?: return
        val amps = eqAmps
        if (amps == null) {
            p.audio().setEqualizer(null)
            return
        }
        val eq: Equalizer = factory?.equalizer()?.newEqualizer() ?: return
        eq.setPreamp(eqPreamp.coerceIn(-20f, 20f))
        for (i in 0 until minOf(eq.bandCount(), amps.size)) eq.setAmp(i, amps[i].coerceIn(-20f, 20f))
        p.audio().setEqualizer(eq)
    }

    /** Tonu değiştirir: motoru yeniden kurar ve aynı yerden devam eder. */
    fun setSemitones(st: Float, resume: Boolean, positionMs: Long) {
        if (st == semitones && player != null) return
        semitones = st
        val mrl = lastMrl
        val opts = lastOptions
        releaseInternal()
        build()
        if (mrl != null) {
            play(mrl, opts, positionMs, paused = !resume)
        }
    }

    private fun releaseInternal() {
        runCatching {
            player?.events()?.removeMediaPlayerEventListener(adapter)
            player?.controls()?.stop()
            player?.release()
        }
        runCatching { factory?.release() }
        player = null
        factory = null
    }

    fun release() = releaseInternal()
}
