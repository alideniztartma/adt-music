@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.alideniz.adtmusic.MainActivity
import com.alideniz.adtmusic.R
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Settings
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.db.AdtDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class DlState { QUEUED, DOWNLOADING, DONE, FAILED }

data class DlInfo(val state: DlState, val progress: Float)

/** İndirmeleri yönetir ve durumlarını arayüze akış olarak verir. */
class DownloadTracker(
    private val context: Context,
    media: MediaStack,
    private val settings: Settings,
    private val dao: AdtDao,
    private val scope: CoroutineScope,
) {
    val manager: DownloadManager = DownloadManager(
        context,
        media.databaseProvider,
        media.downloadCache,
        media.upstreamFactory,
        Executors.newFixedThreadPool(3),
    ).apply {
        maxParallelDownloads = 3
        requirements = requirementsFor(settings.downloadWifiOnly.value)
    }

    private val _states = MutableStateFlow<Map<String, DlInfo>>(emptyMap())
    val states: StateFlow<Map<String, DlInfo>> = _states.asStateFlow()

    init {
        runCatching {
            manager.downloadIndex.getDownloads().use { cursor ->
                val map = mutableMapOf<String, DlInfo>()
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    map[d.request.id] = d.toInfo()
                }
                _states.value = map
            }
        }
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                _states.value = _states.value + (download.request.id to download.toInfo())
            }

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                _states.value = _states.value - download.request.id
            }
        })
        // İlerleme yüzdesi dinleyiciyle gelmediği için indirme sürerken düzenli yokla.
        scope.launch {
            while (isActive) {
                delay(700)
                val running = manager.currentDownloads
                if (running.isNotEmpty()) {
                    val updated = _states.value.toMutableMap()
                    running.forEach { updated[it.request.id] = it.toInfo() }
                    _states.value = updated
                }
            }
        }
    }

    private fun Download.toInfo(): DlInfo = when (state) {
        Download.STATE_COMPLETED -> DlInfo(DlState.DONE, 1f)
        Download.STATE_DOWNLOADING -> DlInfo(DlState.DOWNLOADING, (percentDownloaded / 100f).coerceIn(0f, 1f))
        Download.STATE_FAILED -> DlInfo(DlState.FAILED, 0f)
        else -> DlInfo(DlState.QUEUED, 0f)
    }

    fun download(song: Song) {
        scope.launch {
            dao.ensureSong(song)
            val request = DownloadRequest.Builder(song.id, MediaStack.uriFor(song.id))
                .setCustomCacheKey(song.id)
                .setData(song.title.toByteArray())
                .build()
            DownloadService.sendAddDownload(context, AdtDownloadService::class.java, request, false)
            // Sözleri de önceden al: internetsiz dinlerken de görünsün.
            runCatching { context.container.lyrics.lyricsFor(song) }
        }
    }

    fun downloadAll(songs: List<Song>) = songs.forEach { if (_states.value[it.id]?.state != DlState.DONE) download(it) }

    fun remove(id: String) {
        DownloadService.sendRemoveDownload(context, AdtDownloadService::class.java, id, false)
    }

    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(context, AdtDownloadService::class.java, false)
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        settings.downloadWifiOnly.set(wifiOnly)
        DownloadService.sendSetRequirements(
            context, AdtDownloadService::class.java, requirementsFor(wifiOnly), false,
        )
    }

    private fun requirementsFor(wifiOnly: Boolean) =
        Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
}

class AdtDownloadService : DownloadService(
    NOTIFICATION_ID,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel,
    0,
) {
    private val helper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = container.downloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val count = downloads.count { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED }
        return helper.buildProgressNotification(
            this, R.drawable.ic_notification, intent,
            if (count > 0) "$count şarkı indiriliyor" else "İndirmeler",
            downloads, notMetRequirements,
        )
    }

    companion object {
        const val NOTIFICATION_ID = 7341
        const val CHANNEL_ID = "adt_downloads"
    }
}
