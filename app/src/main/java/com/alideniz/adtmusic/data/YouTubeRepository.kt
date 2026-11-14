package com.alideniz.adtmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** Çözülmüş ses akışı (YouTube adresleri birkaç saat geçerlidir). */
data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val bitrate: Int,
    val expiresAtMs: Long,
)

/** Sayfalı arama için imleç. */
class SearchCursor internal constructor(
    internal val handler: SearchQueryHandler,
    internal val page: Page?,
) {
    val hasMore: Boolean get() = page != null
}

data class SearchPage(val items: List<SearchItem>, val cursor: SearchCursor?, val correction: String?)

/**
 * YouTube Music kataloğuna erişen tek kapı. Tüm çağrılar arka planda (IO) çalışır.
 * Resmî bir API değildir; NewPipeExtractor üzerinden herkese açık sayfalar okunur.
 */
class YouTubeRepository {

    private val yt get() = ServiceList.YouTube
    private val streamCache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()
    private val collectionCache = ConcurrentHashMap<String, CollectionDetail>()

    // ---------------------------------------------------------------- arama

    suspend fun search(query: String, filter: SearchFilter): SearchPage = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(filter.key), "")
        val info = SearchInfo.getInfo(yt, handler)
        val items = info.relatedItems.mapNotNull { it.toSearchItem(filter) }
        SearchPage(
            items = items,
            cursor = info.nextPage?.let { SearchCursor(handler, it) },
            correction = info.searchSuggestion?.takeIf { it.isNotBlank() && info.isCorrectedSearch },
        )
    }

    suspend fun searchMore(cursor: SearchCursor, filter: SearchFilter): SearchPage = withContext(Dispatchers.IO) {
        val page = cursor.page ?: return@withContext SearchPage(emptyList(), null, null)
        val more = SearchInfo.getMoreItems(yt, cursor.handler, page)
        SearchPage(
            items = more.items.mapNotNull { it.toSearchItem(filter) },
            cursor = more.nextPage?.let { SearchCursor(cursor.handler, it) },
            correction = null,
        )
    }

    suspend fun searchSongs(query: String, limit: Int = 30): List<Song> =
        runCatching { search(query, SearchFilter.SONGS).items }
            .getOrDefault(emptyList())
            .mapNotNull { (it as? SearchItem.SongItem)?.song }
            .take(limit)

    suspend fun searchCollections(query: String, filter: SearchFilter): List<CollectionRef> =
        runCatching { search(query, filter).items }
            .getOrDefault(emptyList())
            .mapNotNull { (it as? SearchItem.CollectionItem)?.ref }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList()).take(8)
    }

    // ---------------------------------------------------------------- keşif

    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        val fromKiosk = runCatching {
            val extractor = yt.kioskList.getExtractorById("trending_music", null)
            extractor.fetchPage()
            KioskInfo.getInfo(extractor).relatedItems.mapNotNull { it.toSong() }
        }.getOrDefault(emptyList())
        fromKiosk.ifEmpty { searchSongs("Türkiye top 50 şarkı") }
    }

    /** Bir şarkıdan başlayan sonsuz radyo (YouTube Music "Mix" listesi). */
    suspend fun radio(seedId: String): List<Song> = withContext(Dispatchers.IO) {
        val mix = runCatching {
            val url = "https://www.youtube.com/watch?v=$seedId&list=RDAMVM$seedId"
            PlaylistInfo.getInfo(yt, url).relatedItems.mapNotNull { it.toSong() }
        }.getOrDefault(emptyList())
        val songs = mix.ifEmpty {
            runCatching {
                streamInfo(seedId).relatedItems.mapNotNull { (it as? StreamInfoItem)?.toSong() }
            }.getOrDefault(emptyList())
        }
        songs.filter { it.id != seedId }.distinctBy { it.id }
    }

    suspend fun home(seed: Song?): HomeFeed = coroutineScope {
        val trending = async { runCatching { trending() }.getOrDefault(emptyList()) }
        val featured = async { searchCollections("Türkçe pop hitler", SearchFilter.PLAYLISTS).take(10) }
        val albums = async { searchCollections("yeni albüm 2026", SearchFilter.ALBUMS).take(10) }
        val trend = trending.await()
        val actualSeed = seed ?: trend.firstOrNull()
        val picks = actualSeed?.let { s -> runCatching { radio(s.id) }.getOrDefault(emptyList()) }.orEmpty()
        HomeFeed(
            quickPicks = picks.take(20),
            quickPicksSeed = actualSeed,
            trending = trend.take(30),
            featured = featured.await(),
            newReleases = albums.await(),
        )
    }

    // ------------------------------------------------------- albüm / liste

    suspend fun collection(url: String, maxSongs: Int = 300): CollectionDetail = withContext(Dispatchers.IO) {
        collectionCache[url]?.let { return@withContext it }
        val info = PlaylistInfo.getInfo(yt, url)
        val songs = info.relatedItems.mapNotNull { it.toSong() }.toMutableList()
        var next = info.nextPage
        var guard = 0
        while (songs.size < maxSongs && guard < 8) {
            val page: Page = next ?: break
            val more = runCatching { PlaylistInfo.getMoreItems(yt, url, page) }.getOrNull() ?: break
            songs += more.items.mapNotNull { it.toSong() }
            next = more.nextPage
            guard++
        }
        val distinct = songs.distinctBy { it.id }
        CollectionDetail(
            url = url,
            title = info.name.orEmpty(),
            subtitle = info.uploaderName.orEmpty(),
            thumbnail = bestThumb(info.thumbnails) ?: distinct.firstOrNull()?.thumbnail,
            songs = distinct,
            count = if (info.streamCount > 0) info.streamCount else distinct.size.toLong(),
        ).also { collectionCache[url] = it }
    }

    suspend fun artist(name: String, thumbnail: String?): ArtistDetail = coroutineScope {
        val songs = async { searchSongs(name, 40) }
        val albums = async { searchCollections(name, SearchFilter.ALBUMS) }
        val lower = name.lowercase()
        val s = songs.await()
        val own = s.filter { it.artist.lowercase().contains(lower) }
        val a = albums.await()
        val ownAlbums = a.filter { it.subtitle.lowercase().contains(lower) }
        ArtistDetail(
            name = name,
            thumbnail = thumbnail,
            songs = own.ifEmpty { s },
            albums = ownAlbums.ifEmpty { a }.take(12),
        )
    }

    // ------------------------------------------------------------ akışlar

    suspend fun streamInfo(id: String): StreamInfo = withContext(Dispatchers.IO) {
        val cached = streamCache[id]
        if (cached != null && cached.second > System.currentTimeMillis()) return@withContext cached.first
        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        streamCache[id] = info to (System.currentTimeMillis() + 60 * 60 * 1000L)
        info
    }

    suspend fun song(id: String): Song = withContext(Dispatchers.IO) {
        val info = streamInfo(id)
        Song(
            id = id,
            title = info.name.orEmpty(),
            artist = info.uploaderName?.removeSuffix(" - Topic").orEmpty(),
            thumbnail = bestThumb(info.thumbnails),
            durationSec = info.duration,
            artistUrl = info.uploaderUrl,
        )
    }

    /**
     * Ses akışı adresini çözer. Bu fonksiyon oynatıcının yükleme iş parçacığından
     * çağrılır, bu yüzden engelleyici (blocking) çalışır.
     */
    fun resolveBlocking(id: String, quality: String): ResolvedStream {
        val cached = streamCache[id]
        val info = if (cached != null && cached.second > System.currentTimeMillis()) {
            cached.first
        } else {
            StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id").also {
                streamCache[id] = it to (System.currentTimeMillis() + 60 * 60 * 1000L)
            }
        }
        val candidates = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .ifEmpty { info.audioStreams.filter { it.isUrl } }
        val stream = pickStream(candidates, quality)
            ?: throw IllegalStateException("Bu şarkı için ses akışı bulunamadı")
        val url = stream.content
        val expire = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return ResolvedStream(
            url = url,
            mimeType = stream.format?.mimeType,
            bitrate = stream.averageBitrate,
            expiresAtMs = (expire?.times(1000)) ?: (System.currentTimeMillis() + 3 * 3600 * 1000L),
        )
    }

    fun invalidateStream(id: String) {
        streamCache.remove(id)
    }

    private fun pickStream(list: List<AudioStream>, quality: String): AudioStream? {
        if (list.isEmpty()) return null
        // Opus ve AAC her Android cihazda çalınabilir; önce bunları dene.
        val playable = list.filter {
            val mime = it.format?.mimeType.orEmpty()
            mime.contains("mp4") || mime.contains("webm")
        }.ifEmpty { list }
        // Dublajlı/yan ses kanalları yerine orijinal sesi tercih et.
        val original = playable.filter { it.audioTrackType == null || it.audioTrackType.name == "ORIGINAL" }
            .ifEmpty { playable }
        return when (quality) {
            "low" -> original.minByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: Int.MAX_VALUE }
            "normal" -> original.minByOrNull { kotlin.math.abs((it.averageBitrate.takeIf { b -> b > 0 } ?: 128) - 128) }
            else -> original.maxByOrNull { it.averageBitrate }
        }
    }

    // ------------------------------------------------------------ dönüşüm

    private fun InfoItem.toSearchItem(filter: SearchFilter): SearchItem? = when (this) {
        is StreamInfoItem -> toSong()?.let { SearchItem.SongItem(it) }
        is PlaylistInfoItem -> SearchItem.CollectionItem(
            CollectionRef(
                url = url,
                title = name.orEmpty(),
                subtitle = uploaderName.orEmpty(),
                thumbnail = bestThumb(thumbnails),
                kind = if (filter == SearchFilter.ALBUMS) CollectionKind.ALBUM else CollectionKind.PLAYLIST,
                count = streamCount,
            )
        )
        is ChannelInfoItem -> SearchItem.ArtistItem(
            ArtistRef(url = url, name = name.orEmpty(), thumbnail = bestThumb(thumbnails), subscribers = subscriberCount)
        )
        else -> null
    }

    private fun StreamInfoItem.toSong(): Song? {
        val id = videoIdOf(url) ?: return null
        return Song(
            id = id,
            title = name.orEmpty(),
            artist = uploaderName?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "Bilinmeyen sanatçı",
            thumbnail = bestThumb(thumbnails),
            durationSec = duration,
            artistUrl = uploaderUrl,
        )
    }

    companion object {
        private val ID_REGEX = Regex("(?:v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})")
        private val LIST_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")

        fun videoIdOf(url: String?): String? = url?.let { ID_REGEX.find(it)?.groupValues?.get(1) }
        fun playlistIdOf(url: String?): String? = url?.let { LIST_REGEX.find(it)?.groupValues?.get(1) }

        fun bestThumb(images: List<Image>?): String? {
            val best = images?.maxByOrNull { if (it.height > 0) it.height else it.width }?.url ?: return null
            return upgradeThumb(best)
        }

        /** Küçük kapak adreslerini yüksek çözünürlüklü olanlarla değiştirir. */
        fun upgradeThumb(url: String): String = when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
                url.replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w544-h544-l90-rj")
                    .replace(Regex("=s\\d+[^&]*$"), "=s544")
            url.contains("i.ytimg.com") ->
                url.replace(Regex("/(default|mqdefault|sddefault|hq720)\\.jpg"), "/hqdefault.jpg")
            else -> url
        }
    }
}
