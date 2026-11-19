package com.alideniz.adtmusic.playback

import androidx.compose.runtime.Immutable
import com.alideniz.adtmusic.AppContainer
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.StreamGuard
import com.alideniz.adtmusic.data.db.HistoryEntity
import com.alideniz.adtmusic.platform.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** media3 Player sabitlerinin masaüstü karşılığı (değerler aynı). */
object Player {
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2
}

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
 * Masaüstünde oynatıcının kendisi: sırayı tutar, VLC motorunu yönetir, geçmişi kaydeder,
 * otomatik radyo ve hata kurtarmayı yapar (Android'deki PlaybackService + MediaController).
 * Bütün çağrılar ana (arayüz) iş parçacığında yapılmalıdır.
 */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private lateinit var c: AppContainer

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    // ---- sıra
    private val queue = mutableListOf<Song>()
    private var index = 0
    private var shuffle = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    /** Karıştırma açılmadan önceki sıra (kapatınca geri döner). */
    private var unshuffled: List<Song>? = null
    private var speed = 1f
    private var semitones = 0f

    // ---- durum
    private var playWhenReady = false
    private var playing = false
    private var buffering = false
    private var ended = false
    private var loadJob: Job? = null
    private var loadToken = 0

    private var retriedFor: String? = null
    private var radioLoading = false
    private var playStartedAt = 0L
    private var songsSinceBadgeCheck = 0
    private var limitRetryJob: Job? = null

    private lateinit var engine: VlcEngine

    private val listener = object : VlcEngine.Listener {
        override fun onPlaying() = post { playing = true; buffering = false; startPlayClock(); refresh() }
        override fun onPaused() = post { playing = false; flushPlayTime(); saveQueue(); refresh() }
        override fun onBuffering() = post { if (!playing) { buffering = true; refresh() } }
        override fun onFinished() = post { playing = false; onEnded() }
        override fun onError() = post { playing = false; buffering = false; handleError(null) }
    }

    private fun post(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    fun connect() {
        if (::engine.isInitialized) return
        c = com.alideniz.adtmusic.appContainer
        speed = c.settings.playbackSpeed.value.coerceIn(0.25f, 3f)
        semitones = c.settings.pitchSemitones.value.coerceIn(-12f, 12f)
        engine = VlcEngine(listener)
        engine.start(semitones)
        engine.setRate(speed)
        c.effects.attach(engine)
        c.sleepTimer.attach(this)
        engine.unavailableReason?.let { c.toast(it) }
        restoreIfEmpty()
        refresh()
        scope.launch {
            while (true) {
                if (::engine.isInitialized && queue.isNotEmpty()) {
                    val len = engine.length
                    _progress.value = Progress(
                        position = if (ended) len else engine.time,
                        duration = len.takeIf { it > 0 } ?: ((current()?.durationSec ?: 0L) * 1000L),
                        buffered = len,
                    )
                }
                delay(if (playing) 200 else 600)
            }
        }
    }

    /** Uygulama penceresi kapanırken çağrılır. */
    fun shutdown() {
        if (!::engine.isInitialized) return
        runCatching { c.dataUsage.flush() }
        flushPlayTime()
        saveQueue()
        c.sleepTimer.detach()
        engine.release()
    }

    val engineReady: Boolean get() = ::engine.isInitialized && engine.available

    private fun current(): Song? = queue.getOrNull(index)

    private fun restoreIfEmpty() {
        if (queue.isNotEmpty() || !c.settings.resumeOnStart.value) return
        val songs = songsFromJson(c.settings.lastQueue.value)
        if (songs.isEmpty()) return
        queue.addAll(songs)
        index = c.settings.lastIndex.value.coerceIn(0, songs.size - 1)
        val pos = c.settings.lastPosition.value
        playWhenReady = false
        load(pos, countPlay = false)
    }

    private fun refresh() {
        val cur = current()
        _state.value = PlayerUiState(
            connected = true,
            isPlaying = playing && playWhenReady,
            isBuffering = buffering && playWhenReady,
            current = cur,
            index = index,
            queue = queue.toList(),
            shuffle = shuffle,
            repeatMode = repeatMode,
            hasNext = index < queue.size - 1,
            hasPrevious = index > 0 || (::engine.isInitialized && engine.time > 3000),
            speed = speed,
            pitch = pitchOf(semitones),
        )
    }

    // ------------------------------------------------------------ yükleme

    /** Geçerli şarkıyı yükler ve [playWhenReady] ise çalar. */
    private fun load(startMs: Long = 0, countPlay: Boolean = true, auto: Boolean = false) {
        val song = current() ?: return
        flushPlayTime()
        ended = false
        buffering = true
        val token = ++loadToken
        loadJob?.cancel()
        refresh()
        onTransition(song, countPlay, auto)
        loadJob = scope.launch {
            val source = try {
                withContext(Dispatchers.IO) { c.media.sourceFor(song.id) }
            } catch (e: Exception) {
                if (token != loadToken) return@launch
                buffering = false
                handleError(e)
                return@launch
            }
            if (token != loadToken) return@launch
            engine.play(source.mrl, source.options, startMs, paused = !playWhenReady)
            if (!playWhenReady) { buffering = false; refresh() }
        }
    }

    private fun onTransition(song: Song, countPlay: Boolean, auto: Boolean) {
        retriedFor = if (retriedFor == song.id) retriedFor else null
        scope.launch {
            runCatching {
                c.dao.ensureSong(song)
                if (countPlay && !c.settings.pauseHistory.value) {
                    val now = System.currentTimeMillis()
                    c.dao.markPlayed(song.id, now)
                    c.dao.insertHistory(HistoryEntity(songId = song.id, playedAt = now))
                }
            }
        }
        saveQueue()
        maybeExtendWithRadio()
        preloadUpcoming()
        songsSinceBadgeCheck++
        if (songsSinceBadgeCheck >= 3) {
            songsSinceBadgeCheck = 0
            scope.launch { delay(5000); c.checkBadges() }
        }
    }

    private fun onEnded() {
        flushPlayTime()
        // Uyku zamanlayıcısı "şarkı bitince" ayarlıysa: sıradakini hazırla ama çalma
        if (c.sleepTimer.consumeEndOfSong()) {
            playWhenReady = false
            if (index < queue.size - 1) { index++; load(auto = true) } else { ended = true; refresh() }
            return
        }
        when {
            repeatMode == Player.REPEAT_MODE_ONE -> load(auto = true)
            index < queue.size - 1 -> { index++; load(auto = true) }
            repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty() -> { index = 0; load(auto = true) }
            else -> {
                ended = true
                playWhenReady = false
                refresh()
                maybeExtendWithRadio(force = true)
            }
        }
    }

    private fun startPlayClock() {
        if (playStartedAt <= 0) playStartedAt = System.currentTimeMillis()
    }

    private fun flushPlayTime() {
        val id = current()?.id ?: return
        if (playStartedAt <= 0) return
        val ms = System.currentTimeMillis() - playStartedAt
        playStartedAt = 0
        if (ms in 1..6 * 3600_000L) scope.launch {
            runCatching {
                if (!c.settings.pauseHistory.value) {
                    c.dao.addPlayTime(id, ms)
                    c.dao.addListened(id, ms)
                }
            }
        }
    }

    private fun handleError(error: Throwable?) {
        val song = current() ?: return
        val id = song.id
        val position = engine.time
        val local = c.media.localFile(id) != null
        // Akış adresinin süresi dolmuş olabilir: bir kez yeniden çözmeyi dene.
        if (!local && retriedFor != id && !StreamGuard.isLimit(error)) {
            retriedFor = id
            c.media.invalidate(id)
            load(position, countPlay = false)
            return
        }
        val offline = error is java.net.UnknownHostException || error is java.net.ConnectException ||
            error?.cause is java.net.UnknownHostException
        if (StreamGuard.isLimit(error) && !offline) {
            val waitMs = (c.guard.state.value.limitedUntil - System.currentTimeMillis()).coerceAtLeast(30_000)
            c.toast(
                "YouTube bu ağı kısa süreliğine sınırladı. ${(waitMs / 60_000).coerceAtLeast(1)} dk sonra kendiliğinden " +
                    "yeniden denenecek. İndirilenler çalmaya devam eder."
            )
            limitRetryJob?.cancel()
            limitRetryJob = scope.launch {
                delay(waitMs)
                if (current()?.id != id || playing) return@launch
                retriedFor = null
                c.media.invalidate(id)
                playWhenReady = true
                load(position, countPlay = false)
            }
            buffering = false
            refresh()
            return
        }
        c.toast(
            when {
                offline -> "İnternet bağlantısı yok. İndirdiğin şarkıları dinleyebilirsin."
                else -> "Bu şarkı çalınamadı, sıradakine geçiliyor."
            }
        )
        buffering = false
        if (!offline && index < queue.size - 1) {
            index++
            load()
        } else refresh()
    }

    /** Sıra bitmek üzereyken çalan şarkıya benzeyenleri ekler (otomatik radyo). */
    private fun maybeExtendWithRadio(force: Boolean = false) {
        if (!c.settings.autoplay.value || radioLoading) return
        if (!c.guard.backgroundAllowed) return
        if (repeatMode != Player.REPEAT_MODE_OFF || queue.isEmpty()) return
        val remaining = queue.size - 1 - index
        if (!force && remaining > 2) return
        val seed = current()?.id ?: return
        radioLoading = true
        scope.launch {
            val existing = queue.map { it.id }.toSet()
            val more = runCatching { c.youtube.radio(seed) }.getOrDefault(emptyList())
                .filter { it.id !in existing }
                .take(15)
            if (more.isNotEmpty()) {
                val wasEnded = ended
                queue.addAll(more)
                if (wasEnded) {
                    index++
                    playWhenReady = true
                    load(auto = true)
                } else refresh()
            }
            radioLoading = false
        }
    }

    private fun preloadUpcoming() {
        if (!c.settings.preloadNext.value) return
        if (!c.guard.backgroundAllowed) return
        queue.getOrNull(index + 1)?.let { c.media.prefetch(it.id) }
    }

    private fun saveQueue() {
        if (!::c.isInitialized || queue.isEmpty()) return
        val start = (index - 50).coerceAtLeast(0)
        val end = (start + 200).coerceAtMost(queue.size)
        c.settings.lastQueue.set(queue.subList(start, end).toJson())
        c.settings.lastIndex.set(index - start)
        c.settings.lastPosition.set(if (::engine.isInitialized) engine.time else 0L)
    }

    // ------------------------------------------------------------ eylemler

    private fun ensure(): Boolean {
        connect()
        return true
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (!ensure() || songs.isEmpty()) return
        val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(0, songs.size - 1)
        queue.clear()
        if (shuffle) {
            val first = songs[start]
            unshuffled = songs.toList()
            queue.add(first)
            queue.addAll((songs.toMutableList().also { it.removeAt(start) }).shuffled())
            index = 0
        } else {
            unshuffled = null
            queue.addAll(songs)
            index = start
        }
        this.shuffle = shuffle
        playWhenReady = true
        load()
    }

    /** Şarkıyı belli bir saniyeden başlatır (ör. bir anıdan). */
    fun playAt(song: Song, positionMs: Long) {
        if (!ensure()) return
        queue.clear(); queue.add(song); index = 0
        unshuffled = null; shuffle = false
        playWhenReady = true
        load(positionMs.coerceAtLeast(0))
    }

    /** Tek bir şarkıyı başlatır ve ardından benzer şarkılarla radyoya devam eder. */
    fun playRadio(song: Song) {
        play(listOf(song))
        scope.launch {
            val more = runCatching { c.youtube.radio(song.id) }.getOrDefault(emptyList())
            if (current()?.id == song.id) {
                val existing = queue.map { it.id }.toSet()
                queue.addAll(more.filter { it.id !in existing }.take(25))
                refresh()
            }
        }
    }

    fun playNext(song: Song) {
        if (!ensure()) return
        if (queue.isEmpty()) { play(listOf(song)); return }
        queue.add(index + 1, song)
        refresh()
        c.toast("Sıradaki: ${song.title}")
    }

    fun addToQueue(songs: List<Song>) {
        if (!ensure()) return
        if (queue.isEmpty()) { play(songs); return }
        queue.addAll(songs)
        refresh()
        c.toast(if (songs.size == 1) "Sıraya eklendi" else "${songs.size} şarkı sıraya eklendi")
    }

    fun togglePlay() {
        if (!ensure() || queue.isEmpty()) return
        if (playing && playWhenReady) {
            playWhenReady = false
            engine.pause()
        } else {
            playWhenReady = true
            if (ended) { ended = false; load(0, countPlay = false) }
            else if (!engine.isPlaying && engine.length <= 0 && !buffering) load(c.settings.lastPosition.value, countPlay = false)
            else engine.resume()
        }
        refresh()
    }

    fun pause() {
        if (!::engine.isInitialized) return
        playWhenReady = false
        engine.pause()
        refresh()
    }

    fun next() {
        if (!ensure() || queue.isEmpty()) return
        when {
            index < queue.size - 1 -> index++
            repeatMode == Player.REPEAT_MODE_ALL -> index = 0
            else -> return
        }
        playWhenReady = true
        load()
    }

    fun previous() {
        if (!ensure() || queue.isEmpty()) return
        if (engine.time > 3000 || index == 0) {
            seekTo(0)
            return
        }
        index--
        playWhenReady = true
        load()
    }

    fun seekTo(ms: Long) {
        if (!::engine.isInitialized) return
        if (ended) { ended = false; playWhenReady = true; load(ms, countPlay = false); return }
        engine.seek(ms)
        _progress.value = _progress.value.copy(position = ms)
    }

    fun seekBy(deltaMs: Long) = seekTo((engine.time + deltaMs).coerceAtLeast(0))

    fun skipTo(i: Int) {
        if (i !in queue.indices) return
        index = i
        playWhenReady = true
        load()
    }

    fun removeAt(i: Int) {
        if (i !in queue.indices) return
        if (i == index) {
            queue.removeAt(i)
            if (queue.isEmpty()) { stopAndClear(); return }
            index = index.coerceAtMost(queue.size - 1)
            load()
        } else {
            queue.removeAt(i)
            if (i < index) index--
            refresh()
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val s = queue.removeAt(from)
        queue.add(to, s)
        index = when (index) {
            from -> to
            in (minOf(from, to)..maxOf(from, to)) -> if (from < to) index - 1 else index + 1
            else -> index
        }
        refresh()
    }

    fun clearUpcoming() {
        if (index + 1 < queue.size) queue.subList(index + 1, queue.size).clear()
        refresh()
    }

    fun toggleShuffle() {
        if (queue.isEmpty()) { shuffle = !shuffle; refresh(); return }
        val cur = current()
        if (!shuffle) {
            unshuffled = queue.toList()
            val upcoming = queue.subList(index + 1, queue.size).toList().shuffled()
            queue.subList(index + 1, queue.size).clear()
            queue.addAll(upcoming)
            shuffle = true
        } else {
            unshuffled?.let { orig ->
                // Sırada sonradan eklenenler de korunsun
                val extra = queue.filter { s -> orig.none { it.id == s.id } }
                queue.clear(); queue.addAll(orig + extra)
                index = queue.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
            }
            unshuffled = null
            shuffle = false
        }
        refresh()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refresh()
    }

    /** Hızı değiştirir; ton olduğu gibi kalır. */
    fun setSpeed(speed: Float) = setSpeedAndPitch(speed, semitones)

    /** Tonu yarım ton cinsinden değiştirir; hız olduğu gibi kalır. */
    fun setSemitones(semitones: Float) = setSpeedAndPitch(speed, semitones)

    fun setSpeedAndPitch(speed: Float, semitones: Float) {
        if (!ensure()) return
        val sp = speed.coerceIn(0.25f, 3f)
        val st = (Math.round(semitones * 2f) / 2f).coerceIn(-12f, 12f)
        this.speed = sp
        engine.setRate(sp)
        if (st != this.semitones) {
            this.semitones = st
            engine.setSemitones(st, resume = playWhenReady, positionMs = engine.time)
            c.effects.attach(engine)
        }
        c.settings.playbackSpeed.set(sp)
        c.settings.pitchSemitones.set(st)
        refresh()
    }

    fun resetSpeedAndPitch() = setSpeedAndPitch(1f, 0f)

    /** Çalmayı durdurur ve sırayı tamamen boşaltır (mini oynatıcı kapanır). */
    fun stopAndClear() {
        if (!::engine.isInitialized) return
        flushPlayTime()
        loadToken++
        engine.stop()
        queue.clear(); index = 0
        playWhenReady = false; playing = false; buffering = false
        c.settings.lastQueue.set("")
        _progress.value = Progress()
        refresh()
    }

    /** Oynatıcı ses düzeyi (0..1); masaüstünde sistem sesi yerine uygulama sesi. */
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0f, 1f)
        c.effects.applyVolume()
    }

    fun release() = shutdown()

    // ---- uyku zamanlayıcısı için
    internal fun fadeVolume(factor: Float) {
        if (::engine.isInitialized) c.effects.applyVolume(factor)
    }

    internal val isPlayingNow: Boolean get() = playing && playWhenReady
}

/** Yarım tonu ton çarpanına çevirir: her 12 yarım ton bir oktavdır (x2). */
fun pitchOf(semitones: Float): Float = Math.pow(2.0, semitones / 12.0).toFloat()

fun semitonesOf(pitch: Float): Float =
    if (pitch <= 0f) 0f else (12.0 * Math.log(pitch.toDouble()) / Math.log(2.0)).toFloat()
