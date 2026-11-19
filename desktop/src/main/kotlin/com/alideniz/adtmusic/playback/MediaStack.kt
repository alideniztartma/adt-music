package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.data.NewPipeDownloader
import com.alideniz.adtmusic.data.ResolvedStream
import com.alideniz.adtmusic.data.Settings
import com.alideniz.adtmusic.data.YouTubeRepository
import com.alideniz.adtmusic.platform.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Bir şarkının çalınabilir kaynağı: yerel dosya ya da YouTube akışı. */
data class PlaySource(val mrl: String, val options: Array<String>, val local: Boolean)

/**
 * Masaüstü veri katmanı: şarkı indirildiyse yerel dosyadan, değilse YouTube
 * akışından çalar. Akış adresleri birkaç saat önbellekte tutulur.
 */
class MediaStack(
    private val context: Context,
    private val settings: Settings,
    private val youtube: YouTubeRepository,
    baseClient: OkHttpClient,
    private val dataUsage: DataUsage? = null,
) {
    /** İndirilen şarkılar: asla otomatik silinmez. */
    val downloadsDir: File by lazy { File(context.filesDir, "downloads").apply { mkdirs() } }

    private val resolved = ConcurrentHashMap<String, ResolvedStream>()

    /** googlevideo.com istekleri için YouTube'un beklediği başlıklar (indirmelerde). */
    val mediaClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            if (!req.url.host.endsWith("googlevideo.com")) return@Interceptor chain.proceed(req)
            val b = req.newBuilder()
            headersFor(req.url.toString()).forEach { (k, v) -> b.header(k, v) }
            chain.proceed(b.build())
        })
        .apply { dataUsage?.let { addNetworkInterceptor(it.interceptor) } }
        .build()

    fun headersFor(url: String): Map<String, String> {
        val h = linkedMapOf<String, String>()
        h["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else NewPipeDownloader.USER_AGENT
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            h["Origin"] = "https://www.youtube.com"
            h["Referer"] = "https://www.youtube.com/"
        }
        return h
    }

    /** İndirilmiş dosya (varsa). */
    fun localFile(id: String): File? =
        downloadsDir.listFiles { f -> f.isFile && f.nameWithoutExtension == id && !f.name.endsWith(".part") }
            ?.firstOrNull()

    /** Çalmak için kaynağı bulur. Engelleyici; IO iş parçacığında çağrılmalı. */
    fun sourceFor(id: String): PlaySource {
        localFile(id)?.let { return PlaySource(it.toURI().toASCIIString(), emptyArray(), local = true) }
        val stream = resolve(id)
        val h = headersFor(stream.url)
        val opts = mutableListOf(":http-user-agent=" + h["User-Agent"])
        h["Referer"]?.let { opts += ":http-referrer=$it" }
        return PlaySource(stream.url, opts.toTypedArray(), local = false)
    }

    fun resolve(id: String): ResolvedStream {
        val now = System.currentTimeMillis()
        resolved[id]?.let { if (it.expiresAtMs - 120_000 > now) return it }
        return youtube.resolveBlocking(id, effectiveQuality()).also { resolved[id] = it }
    }

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Masaüstünde ölçülü bağlantı ayrımı yok: seçilen kalite kullanılır. */
    fun effectiveQuality(): String = settings.audioQuality.value

    /** Yalnızca akış adresini önceden çözer (veri indirmez). */
    fun warmUrl(id: String) {
        if (!youtube.guard.backgroundAllowed) return
        if (localFile(id) != null) return
        prefetchScope.launch { runCatching { resolve(id) } }
    }

    /** Sıradaki şarkı için: adresi önceden çöz. */
    fun prefetch(id: String) = warmUrl(id)

    /** Süresi dolmuş (403) akış adresini unut; bir sonraki istekte yeniden çözülür. */
    fun invalidate(id: String) {
        resolved.remove(id)
        youtube.invalidateStream(id)
    }

    /** Masaüstünde akışlar VLC'nin kendi ara belleğinden çalınır; ayrı çalma önbelleği yok. */
    fun playerCacheBytes(): Long = 0L

    fun downloadCacheBytes(): Long = downloadsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearPlayerCache() {}
}
