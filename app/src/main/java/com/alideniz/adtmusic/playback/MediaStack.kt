@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic.playback

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.alideniz.adtmusic.data.NewPipeDownloader
import com.alideniz.adtmusic.data.ResolvedStream
import com.alideniz.adtmusic.data.Settings
import com.alideniz.adtmusic.data.YouTubeRepository
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Oynatıcının veri katmanı:
 *
 *  adt://<videoId>  →  [indirmeler önbelleği]  →  [akıllı çalma önbelleği]  →  YouTube akışı
 *
 * Şarkı indirildiyse ya da yakın zamanda dinlendiyse internet hiç kullanılmaz.
 */
class MediaStack(
    private val context: Context,
    private val settings: Settings,
    private val youtube: YouTubeRepository,
    baseClient: OkHttpClient,
    private val dataUsage: DataUsage? = null,
) {
    val databaseProvider = StandaloneDatabaseProvider(context)

    /** Dinlenen şarkıların otomatik önbelleği: dolunca en eski kullanılan silinir. */
    val playerCache = SimpleCache(
        File(context.cacheDir, "player_cache"),
        LeastRecentlyUsedCacheEvictor(settings.cacheSizeMb.value.coerceAtLeast(128) * 1024L * 1024L),
        databaseProvider,
    )

    /** Kullanıcının indirdiği şarkılar: asla otomatik silinmez. */
    val downloadCache = SimpleCache(
        File(context.filesDir, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    private val resolved = ConcurrentHashMap<String, ResolvedStream>()

    /** googlevideo.com istekleri için YouTube'un beklediği başlıklar. */
    private val mediaClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            if (!req.url.host.endsWith("googlevideo.com")) return@Interceptor chain.proceed(req)
            val b = req.newBuilder()
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                b.header("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
            } else {
                b.header("User-Agent", NewPipeDownloader.USER_AGENT)
            }
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                b.header("Origin", "https://www.youtube.com")
                b.header("Referer", "https://www.youtube.com/")
            }
            chain.proceed(b.build())
        })
        .apply { dataUsage?.let { addNetworkInterceptor(it.interceptor) } }
        .build()

    val cacheKeyFactory = CacheKeyFactory { spec: DataSpec ->
        spec.key ?: spec.uri.host ?: spec.uri.toString()
    }

    private val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != SCHEME) return dataSpec
            val id = uri.host ?: throw IOException("Geçersiz şarkı adresi")
            val now = System.currentTimeMillis()
            val cached = resolved[id]
            val stream = if (cached != null && cached.expiresAtMs - 120_000 > now) {
                cached
            } else {
                try {
                    youtube.resolveBlocking(id, effectiveQuality())
                } catch (e: Exception) {
                    throw IOException("Akış çözülemedi: ${e.message}", e)
                }.also { resolved[id] = it }
            }
            return dataSpec.withUri(Uri.parse(stream.url))
        }
    }

    /** Ağdan okuyan, adt:// adreslerini gerçek akışa çeviren kaynak. */
    private val okHttpFactory = OkHttpDataSource.Factory(mediaClient)

    val upstreamFactory: DataSource.Factory =
        ResolvingDataSource.Factory(
            DataSource.Factory { ChunkedDataSource(okHttpFactory.createDataSource()) },
            resolver,
        )

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetching = ConcurrentHashMap.newKeySet<String>()

    /**
     * Sıradaki şarkının akış adresini çözer ve ilk [bytes] baytını önbelleğe alır.
     * Böylece şarkıya geçildiğinde "yükleniyor" beklemesi olmaz.
     */
    fun prefetch(id: String, bytes: Long = PREFETCH_BYTES) {
        if (!prefetching.add(id)) return
        prefetchScope.launch {
            try {
                if (downloadCache.getCachedBytes(id, 0, bytes) >= bytes) return@launch
                if (playerCache.getCachedBytes(id, 0, bytes) >= bytes) return@launch
                val ds = CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setCacheKeyFactory(cacheKeyFactory)
                    .createDataSource()
                val spec = DataSpec.Builder()
                    .setUri(uriFor(id))
                    .setKey(id)
                    .setPosition(0)
                    .setLength(bytes)
                    .build()
                CacheWriter(ds, spec, null, null).cache()
            } catch (_: Exception) {
            } finally {
                prefetching.remove(id)
            }
        }
    }

    /** Mobil veride tasarruf açıksa düşük kalite seçilir. */
    fun effectiveQuality(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm?.isActiveNetworkMetered == true) {
            return when (val m = settings.mobileQuality.value) {
                "same" -> if (settings.mobileDataSaver.value) "low" else settings.audioQuality.value
                else -> m
            }
        }
        return settings.audioQuality.value
    }

    /** Yalnızca akış adresini önceden çözer (veri indirmez). */
    fun warmUrl(id: String) {
        prefetchScope.launch {
            runCatching {
                if (resolved[id] == null) resolved[id] = youtube.resolveBlocking(id, effectiveQuality())
            }
        }
    }

    /** Oynatıcının kullandığı kaynak zinciri. */
    fun playerDataSourceFactory(): DataSource.Factory {
        val playerCacheFactory = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(playerCacheFactory)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(cacheKeyFactory)
    }

    /** Süresi dolmuş (403) akış adresini unut; bir sonraki istekte yeniden çözülür. */
    fun invalidate(id: String) {
        resolved.remove(id)
        youtube.invalidateStream(id)
    }

    fun isFullyCached(id: String, lengthHint: Long = -1): Boolean {
        val inDownloads = downloadCache.getCachedBytes(id, 0, -1L)
        if (inDownloads > 0 && downloadCache.getContentMetadata(id).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L).let { it > 0 && it <= inDownloads }) return true
        val inPlayer = playerCache.getCachedBytes(id, 0, -1L)
        val len = playerCache.getContentMetadata(id).get(ContentMetadata.KEY_CONTENT_LENGTH, lengthHint)
        return len > 0 && inPlayer >= len
    }

    fun playerCacheBytes(): Long = playerCache.cacheSpace
    fun downloadCacheBytes(): Long = downloadCache.cacheSpace

    fun clearPlayerCache() {
        playerCache.keys.toList().forEach { key -> runCatching { playerCache.removeResource(key) } }
    }

    companion object {
        const val SCHEME = "adt"
        const val PREFETCH_BYTES = 1_200_000L
        fun uriFor(id: String): Uri = Uri.parse("$SCHEME://$id")
    }
}
