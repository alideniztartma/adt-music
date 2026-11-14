@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.alideniz.adtmusic.MainActivity
import com.alideniz.adtmusic.R
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.db.HistoryEntity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Arka planda çalmayı yürüten servis. Bildirim, kilit ekranı, kulaklık tuşları,
 * Bluetooth ve Android Auto bu oturum üzerinden kontrol eder.
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val c by lazy { container }

    private var likedIds: Set<String> = emptySet()
    private var retriedFor: String? = null
    private var radioLoading = false
    private var playStartedAt = 0L
    private var currentId: String? = null
    private val searchResults = mutableMapOf<String, List<Song>>()

    private val likeCommand = SessionCommand(CMD_LIKE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val audioSessionId = Util.generateAudioSessionIdV21(this)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(c.media.playerDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setAudioSessionId(audioSessionId)
        player.skipSilenceEnabled = c.settings.skipSilence.value
        player.playbackParameters = PlaybackParameters(
            c.settings.playbackSpeed.value.coerceIn(0.25f, 3f),
            pitchOf(c.settings.pitchSemitones.value),
        )
        player.addListener(listener)

        c.effects.attach(audioSessionId)
        c.sleepTimer.attach(player)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("adt_playback")
                .setChannelName(R.string.playback_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

        scope.launch { c.settings.skipSilence.flow.collect { player.skipSilenceEnabled = it } }
        scope.launch {
            c.dao.likedIds().collect {
                likedIds = it.toSet()
                updateNotificationButtons()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            saveQueue()
            stopSelf()
        }
    }

    override fun onDestroy() {
        flushPlayTime()
        saveQueue()
        c.sleepTimer.detach()
        c.effects.release()
        session.release()
        player.removeListener(listener)
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ dinleyici

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushPlayTime()
            retriedFor = null
            if (mediaItem == null) return
            currentId = mediaItem.mediaId
            if (player.isPlaying) playStartedAt = SystemClock.elapsedRealtime()
            val song = mediaItem.toSong()
            scope.launch {
                runCatching {
                    c.dao.ensureSong(song)
                    if (!c.settings.pauseHistory.value) {
                        val now = System.currentTimeMillis()
                        c.dao.markPlayed(song.id, now)
                        c.dao.insertHistory(HistoryEntity(songId = song.id, playedAt = now))
                    }
                }
            }
            updateNotificationButtons()
            saveQueue()
            maybeExtendWithRadio()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playStartedAt = SystemClock.elapsedRealtime()
            } else {
                flushPlayTime()
                saveQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) maybeExtendWithRadio(force = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    private fun flushPlayTime() {
        val id = currentId ?: return
        if (playStartedAt <= 0) return
        val ms = SystemClock.elapsedRealtime() - playStartedAt
        playStartedAt = 0
        if (ms in 1..6 * 3600_000L) scope.launch { runCatching { c.dao.addPlayTime(id, ms) } }
    }

    private fun handleError(error: PlaybackException) {
        val id = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        // Akış adresinin süresi dolmuş olabilir: bir kez yeniden çözmeyi dene.
        if (id != null && retriedFor != id) {
            retriedFor = id
            c.media.invalidate(id)
            player.prepare()
            player.seekTo(position)
            player.play()
            return
        }
        val http = generateSequence(error.cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
        val offline = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        c.toast(
            when {
                offline -> "İnternet bağlantısı yok. İndirdiğin şarkıları dinleyebilirsin."
                http != null -> "YouTube bu şarkıyı vermedi (${http.responseCode}). Sıradakine geçiliyor."
                else -> "Bu şarkı çalınamadı, sıradakine geçiliyor."
            }
        )
        if (!offline && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    /** Sıra bitmek üzereyken çalan şarkıya benzeyenleri ekler (otomatik radyo). */
    private fun maybeExtendWithRadio(force: Boolean = false) {
        if (!c.settings.autoplay.value || radioLoading) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.mediaItemCount == 0) return
        val remaining = player.mediaItemCount - 1 - player.currentMediaItemIndex
        if (!force && remaining > 2) return
        val seed = player.currentMediaItem?.mediaId ?: return
        radioLoading = true
        scope.launch {
            val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val more = runCatching { c.youtube.radio(seed) }.getOrDefault(emptyList())
                .filter { it.id !in existing }
                .take(15)
            if (more.isNotEmpty()) {
                val wasEnded = player.playbackState == Player.STATE_ENDED
                player.addMediaItems(more.map { it.toMediaItem() })
                if (wasEnded) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
            radioLoading = false
        }
    }

    private fun saveQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val start = (player.currentMediaItemIndex - 50).coerceAtLeast(0)
        val end = (start + 200).coerceAtMost(player.mediaItemCount)
        val songs = (start until end).map { player.getMediaItemAt(it).toSong() }
        c.settings.lastQueue.set(songs.toJson())
        c.settings.lastIndex.set(player.currentMediaItemIndex - start)
        c.settings.lastPosition.set(player.currentPosition.coerceAtLeast(0))
    }

    private fun updateNotificationButtons() {
        if (!::session.isInitialized) return
        val id = player.currentMediaItem?.mediaId
        val liked = id != null && id in likedIds
        val like = CommandButton.Builder()
            .setDisplayName(if (liked) "Beğenmekten vazgeç" else "Beğen")
            .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(likeCommand)
            .build()
        session.setCustomLayout(ImmutableList.of(like))
    }

    // ------------------------------------------------------------ oturum geri çağrıları

    private val callback = object : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(likeCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            updateNotificationButtons()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_LIKE) {
                val item = player.currentMediaItem
                if (item != null) {
                    val song = item.toSong()
                    scope.launch {
                        c.dao.ensureSong(song)
                        val liked = song.id in likedIds
                        c.dao.setLiked(song.id, if (liked) null else System.currentTimeMillis())
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val songs = songsFromJson(c.settings.lastQueue.value)
            MediaSession.MediaItemsWithStartPosition(
                songs.map { it.toMediaItem() },
                c.settings.lastIndex.value.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
                c.settings.lastPosition.value,
            )
        }

        // ---- Android Auto ve diğer medya tarayıcıları için kütüphane ağacı

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsableItem(ROOT, "ADT Music"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items: List<MediaItem> = when {
                parentId == ROOT -> listOf(
                    browsableItem(NODE_LIKED, "Beğenilenler"),
                    browsableItem(NODE_RECENT, "Son dinlenenler"),
                    browsableItem(NODE_DOWNLOADS, "İndirilenler"),
                    browsableItem(NODE_PLAYLISTS, "Çalma listelerin"),
                    browsableItem(NODE_TRENDING, "Trendler"),
                )
                parentId == NODE_LIKED -> c.dao.likedSongs().first().map { it.toSong().toMediaItem() }
                parentId == NODE_RECENT -> c.dao.recentSongs(50).first().map { it.toSong().toMediaItem() }
                parentId == NODE_DOWNLOADS -> {
                    val ids = c.downloads.states.value.filterValues { it.state == DlState.DONE }.keys.toList()
                    c.dao.songsByIds(ids).map { it.toSong().toMediaItem() }
                }
                parentId == NODE_PLAYLISTS -> c.dao.playlists().first().map {
                    browsableItem("$PREFIX_PLAYLIST${it.playlist.id}", it.playlist.name, "${it.songCount} şarkı", it.cover)
                }
                parentId.startsWith(PREFIX_PLAYLIST) -> {
                    val id = parentId.removePrefix(PREFIX_PLAYLIST).toLongOrNull() ?: -1
                    c.dao.playlistSongsOnce(id).map { it.toSong().toMediaItem() }
                }
                parentId == NODE_TRENDING -> runCatching { c.youtube.trending() }.getOrDefault(emptyList())
                    .map { it.toMediaItem() }
                else -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(items.size)
            val to = (from + pageSize).coerceAtMost(items.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val song = c.dao.song(mediaId)?.toSong()
            if (song != null) LibraryResult.ofItem(song.toMediaItem(), null)
            else LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val songs = c.youtube.searchSongs(query, 30)
                searchResults[query] = songs
                session.notifySearchResultChanged(browser, query, songs.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val songs = searchResults[query] ?: c.youtube.searchSongs(query, 30)
            val from = (page * pageSize).coerceAtMost(songs.size)
            val to = (from + pageSize).coerceAtMost(songs.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(songs.subList(from, to).map { it.toMediaItem() }), params)
        }
    }

    companion object {
        const val CMD_LIKE = "adt.like"
        private const val ROOT = "root"
        private const val NODE_LIKED = "liked"
        private const val NODE_RECENT = "recent"
        private const val NODE_DOWNLOADS = "downloads"
        private const val NODE_PLAYLISTS = "playlists"
        private const val NODE_TRENDING = "trending"
        private const val PREFIX_PLAYLIST = "pl:"
    }
}
