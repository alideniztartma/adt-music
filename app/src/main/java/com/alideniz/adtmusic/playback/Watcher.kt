package com.alideniz.adtmusic.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alideniz.adtmusic.MainActivity
import com.alideniz.adtmusic.R
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Insights
import com.alideniz.adtmusic.data.SearchFilter
import com.alideniz.adtmusic.data.SearchItem
import com.alideniz.adtmusic.data.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Calendar

/**
 * Arka planda (6 saatte bir, internet varken) çalışır:
 *  • takip edilen sanatçıların yeni şarkılarını bildirir,
 *  • pazar akşamları haftalık dinleme özetini gönderir.
 */
class Watcher(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext.container
        runCatching { if (app.settings.releaseNotifications.value) checkReleases() }
        runCatching { if (app.settings.weeklyNotification.value) maybeWeekly() }
        Result.success()
    }

    private suspend fun checkReleases() {
        val app = applicationContext.container
        val settings = app.settings
        val urls = JSONObject(settings.artistUrls.value)
        val seen = JSONObject(settings.artistLastSeen.value)
        val yt = ServiceList.YouTube
        val artists = app.dao.followedArtistsOnce().take(25)
        var notified = 0
        for (artist in artists) {
            val url = urls.optString(artist.name).ifBlank {
                val found = runCatching {
                    app.youtube.search(artist.name, SearchFilter.ARTISTS).items
                        .firstNotNullOfOrNull { (it as? SearchItem.ArtistItem)?.ref?.url }
                }.getOrNull() ?: continue
                urls.put(artist.name, found)
                found
            }
            val latest = runCatching {
                val info = ChannelInfo.getInfo(yt, url)
                val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                    ?: info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.TRACKS) }
                    ?: return@runCatching emptyList<StreamInfoItem>()
                ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>()
            }.getOrDefault(emptyList()).take(6)
            if (latest.isEmpty()) continue
            val firstId = YouTubeRepository.videoIdOf(latest.first().url) ?: continue
            val last = seen.optString(artist.name)
            if (last.isBlank()) {
                // İlk kez: yalnızca başlangıç noktasını kaydet, eski şarkıları bildirme.
                seen.put(artist.name, firstId)
                continue
            }
            val fresh = latest.takeWhile { YouTubeRepository.videoIdOf(it.url) != last }.take(2)
            seen.put(artist.name, firstId)
            fresh.forEach { item ->
                val id = YouTubeRepository.videoIdOf(item.url) ?: return@forEach
                if (notified < 6) {
                    notify(
                        id.hashCode(),
                        "${artist.name} yeni şarkı paylaştı",
                        item.name ?: "Yeni şarkı",
                        Intent(applicationContext, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse("https://music.youtube.com/watch?v=$id")),
                    )
                    notified++
                }
            }
        }
        settings.artistUrls.set(urls.toString())
        settings.artistLastSeen.set(seen.toString())
    }

    private suspend fun maybeWeekly() {
        val app = applicationContext.container
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || cal.get(Calendar.HOUR_OF_DAY) < 17) return
        val last = app.settings.lastWeeklyNotice.value
        if (System.currentTimeMillis() - last < 5 * 86_400_000L) return
        val rows = app.dao.historySince(System.currentTimeMillis() - 14 * 86_400_000L)
        val w = Insights.weekly(rows)
        if (w.minutes <= 0) return
        app.settings.lastWeeklyNotice.set(System.currentTimeMillis())
        val text = buildString {
            append("${w.minutes} dakika, ${w.distinctSongs} şarkı dinledin.")
            w.topArtist?.let { append(" Haftanın sanatçısı: ${it.first}.") }
            w.topSong?.let { append(" En çok: ${it.first.title}.") }
        }
        notify(
            4242,
            "Haftalık özetin hazır 🎧",
            text,
            Intent(applicationContext, MainActivity::class.java).putExtra("shortcut", "weekly"),
        )
    }

    private fun notify(id: Int, title: String, text: String, intent: Intent) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val pi = PendingIntent.getActivity(
            ctx, id, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFFFF5A1F.toInt())
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        private const val CHANNEL = "adt_news"
        private const val WORK = "adt_watcher"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Yeni şarkılar ve özetler", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Takip ettiğin sanatçıların yeni şarkıları ve haftalık özet"
                    }
                )
            }
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<Watcher>(6, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
