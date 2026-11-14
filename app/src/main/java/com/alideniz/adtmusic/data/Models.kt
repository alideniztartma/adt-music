package com.alideniz.adtmusic.data

import androidx.compose.runtime.Immutable

/** Tek bir şarkı (YouTube Music'teki bir video kimliğine karşılık gelir). */
@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSec: Long = -1,
    val artistUrl: String? = null,
) {
    val watchUrl: String get() = "https://music.youtube.com/watch?v=$id"
}

enum class CollectionKind { ALBUM, PLAYLIST }

/** Albüm veya çalma listesi bağlantısı (arama sonuçları, raflar). */
@Immutable
data class CollectionRef(
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val kind: CollectionKind,
    val count: Long = -1,
)

@Immutable
data class ArtistRef(
    val url: String,
    val name: String,
    val thumbnail: String?,
    val subscribers: Long = -1,
)

/** Arama sonucu satırı: şarkı, koleksiyon ya da sanatçı olabilir. */
sealed interface SearchItem {
    data class SongItem(val song: Song) : SearchItem
    data class CollectionItem(val ref: CollectionRef) : SearchItem
    data class ArtistItem(val ref: ArtistRef) : SearchItem
}

enum class SearchFilter(val label: String, val key: String) {
    SONGS("Şarkılar", "music_songs"),
    ALBUMS("Albümler", "music_albums"),
    ARTISTS("Sanatçılar", "music_artists"),
    PLAYLISTS("Listeler", "music_playlists"),
    VIDEOS("Videolar", "music_videos"),
}

@Immutable
data class CollectionDetail(
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val songs: List<Song>,
    val count: Long,
)

@Immutable
data class ArtistDetail(
    val name: String,
    val thumbnail: String?,
    val songs: List<Song>,
    val albums: List<CollectionRef>,
)

@Immutable
data class Mood(val label: String, val query: String)

@Immutable
data class HomeFeed(
    val quickPicks: List<Song> = emptyList(),
    val quickPicksSeed: Song? = null,
    val trending: List<Song> = emptyList(),
    val featured: List<CollectionRef> = emptyList(),
    val newReleases: List<CollectionRef> = emptyList(),
)

/** Zaman damgalı söz satırı. [timeMs] < 0 ise senkron değildir. */
@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String = "LRCLIB",
)
