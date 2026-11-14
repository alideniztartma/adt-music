package com.alideniz.adtmusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Immutable
data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val current: Song? = null,
    val index: Int = 0,
    val queue: List<Song> = emptyList(),
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    /** Ton kaydırma, yarım ton cinsinden. */
    val semitones: Float get() = semitonesOf(pitch)
}

@Immutable
data class Progress(val position: Long = 0, val duration: Long = 0, val buffered: Long = 0) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Arayüzün çalma servisiyle konuştuğu nesne (MediaController sarmalayıcısı).
 * Bütün çağrılar ana iş parçacığında yapılmalıdır.
 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        scope.launch {
            runCatching {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().await()
            }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                refresh()
                restoreIfEmpty(c)
                pending.toList().forEach { it(c) }
                pending.clear()
            }
            connecting = false
        }
        scope.launch {
            while (true) {
                controller?.let { c ->
                    _progress.value = Progress(
                        position = c.currentPosition.coerceAtLeast(0),
                        duration = c.duration.takeIf { it > 0 } ?: ((_state.value.current?.durationSec ?: 0L) * 1000L),
                        buffered = c.bufferedPosition.coerceAtLeast(0),
                    )
                }
                delay(if (_state.value.isPlaying) 200 else 600)
            }
        }
    }

    private fun restoreIfEmpty(c: MediaController) {
        val settings = context.container.settings
        if (c.mediaItemCount > 0 || !settings.resumeOnStart.value) return
        val songs = songsFromJson(settings.lastQueue.value)
        if (songs.isEmpty()) return
        c.setMediaItems(
            songs.map { it.toMediaItem() },
            settings.lastIndex.value.coerceIn(0, songs.size - 1),
            settings.lastPosition.value,
        )
        c.prepare()
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val count = c.mediaItemCount
        val queue = (0 until count).map { c.getMediaItemAt(it).toSong() }
        _state.value = PlayerUiState(
            connected = true,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            current = c.currentMediaItem?.toSong(),
            index = c.currentMediaItemIndex,
            queue = queue,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem() || c.currentPosition > 3000,
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
        )
    }

    // ------------------------------------------------------------ eylemler

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) = withController { c ->
        if (songs.isEmpty()) return@withController
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(0, songs.size - 1)
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0)
        c.prepare()
        c.play()
    }

    /** Tek bir şarkıyı başlatır ve ardından benzer şarkılarla radyoya devam eder. */
    fun playRadio(song: Song) {
        play(listOf(song))
        scope.launch {
            val more = runCatching { context.container.youtube.radio(song.id) }.getOrDefault(emptyList())
            withController { c ->
                if (c.currentMediaItem?.mediaId == song.id) {
                    val existing = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
                    c.addMediaItems(more.filter { it.id !in existing }.take(25).map { it.toMediaItem() })
                }
            }
        }
    }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(listOf(song)); return@withController
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        context.container.toast("Sıradaki: ${song.title}")
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(songs); return@withController
        }
        c.addMediaItems(songs.map { it.toMediaItem() })
        context.container.toast(if (songs.size == 1) "Sıraya eklendi" else "${songs.size} şarkı sıraya eklendi")
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun pause() = withController { it.pause() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun seekBy(deltaMs: Long) = withController { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0)) }
    fun skipTo(index: Int) = withController { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() } }
    fun removeAt(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }
    fun clearUpcoming() = withController { c ->
        val start = c.currentMediaItemIndex + 1
        if (start < c.mediaItemCount) c.removeMediaItems(start, c.mediaItemCount)
    }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    /** Hızı değiştirir; ton olduğu gibi kalır. */
    fun setSpeed(speed: Float) = setSpeedAndPitch(speed, _state.value.semitones)

    /** Tonu yarım ton cinsinden değiştirir; hız olduğu gibi kalır. */
    fun setSemitones(semitones: Float) = setSpeedAndPitch(_state.value.speed, semitones)

    fun setSpeedAndPitch(speed: Float, semitones: Float) = withController {
        val sp = speed.coerceIn(0.25f, 3f)
        val st = semitones.coerceIn(-12f, 12f)
        it.playbackParameters = PlaybackParameters(sp, pitchOf(st))
        val settings = context.container.settings
        settings.playbackSpeed.set(sp)
        settings.pitchSemitones.set(st)
    }

    fun resetSpeedAndPitch() = setSpeedAndPitch(1f, 0f)

    fun currentItem(): MediaItem? = controller?.currentMediaItem

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}

/** Yarım tonu ExoPlayer'ın ton çarpanına çevirir: her 12 yarım ton bir oktavdır (x2). */
fun pitchOf(semitones: Float): Float = Math.pow(2.0, semitones / 12.0).toFloat()

fun semitonesOf(pitch: Float): Float =
    if (pitch <= 0f) 0f else (12.0 * Math.log(pitch.toDouble()) / Math.log(2.0)).toFloat()
