package com.alideniz.adtmusic.data

import androidx.compose.ui.graphics.Color
import com.alideniz.adtmusic.data.db.AdtDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Kişisel karışım ya da akıllı listenin vitrindeki tanımı. */
data class ListDef(val kind: String, val title: String, val subtitle: String, val color: Color)

/**
 * Kişisel karışımlar (internetten, dinlediklerine göre) ve
 * akıllı listeler (tamamen cihazda, kendiliğinden güncellenen).
 */
class Mixes(private val dao: AdtDao, private val youtube: YouTubeRepository) {

    private val cache = ConcurrentHashMap<String, List<Song>>()

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun daySeed(): Long = dayKey().hashCode().toLong()

    // ------------------------------------------------------------ karışımlar

    suspend fun mix(kind: String, refresh: Boolean = false): List<Song> {
        val key = "$kind-${dayKey()}"
        if (!refresh) cache[key]?.let { return it }
        val songs = withContext(Dispatchers.IO) {
            when (kind) {
                "daily" -> daily()
                "discover" -> discover()
                "throwback" -> throwback()
                "liked" -> likedRadio()
                else -> emptyList()
            }
        }
        if (songs.isNotEmpty()) cache[key] = songs
        return songs
    }

    private suspend fun radiosOf(seeds: List<Song>, each: Int): List<Song> = coroutineScope {
        seeds.map { s -> async { runCatching { youtube.radio(s.id) }.getOrDefault(emptyList()).take(each) } }
            .awaitAll()
            .flatten()
    }

    private suspend fun topRecent(days: Int, limit: Int): List<Song> {
        val since = System.currentTimeMillis() - days * 24L * 3600_000
        return dao.historySince(since)
            .filter { it.listenedMs < 0 || it.listenedMs >= 30_000 }
            .groupBy { it.song.id }
            .map { (_, l) -> l.first().song.toSong() to l.size }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Günlük Karışım: en çok dinlediklerin + onlara benzeyen şarkılar. Her gün değişir. */
    private suspend fun daily(): List<Song> {
        val favorites = topRecent(30, 8).ifEmpty { dao.likedSongsOnce().map { it.toSong() }.take(8) }
        val seeds = favorites.take(3).ifEmpty { youtube.trending().take(2) }
        val similar = radiosOf(seeds, 14)
        val rnd = Random(daySeed())
        return (favorites.shuffled(rnd).take(6) + similar.shuffled(rnd)).distinctBy { it.id }.take(40)
    }

    /** Keşif Karışımı: hiç dinlemediğin, ama zevkine uygun şarkılar. */
    private suspend fun discover(): List<Song> {
        val heard = dao.allSongsOnce().filter { it.playCount > 0 }.map { it.id }.toSet()
        val seeds = dao.historySince(System.currentTimeMillis() - 14 * 24L * 3600_000)
            .map { it.song.toSong() }.distinctBy { it.id }.shuffled(Random(daySeed())).take(4)
            .ifEmpty { youtube.trending().take(3) }
        return radiosOf(seeds, 20).filter { it.id !in heard }.distinctBy { it.id }.shuffled(Random(daySeed())).take(35)
    }

    /** Geçmişe Yolculuk: bir zamanlar çok dinleyip unuttukların. */
    private suspend fun throwback(): List<Song> {
        val cutoff = System.currentTimeMillis() - 30 * 24L * 3600_000
        val old = dao.allSongsOnce()
            .filter { it.playCount >= 2 && (it.lastPlayedAt ?: 0) < cutoff }
            .sortedByDescending { it.playCount }
            .map { it.toSong() }
        val forgottenLikes = dao.likedSongsOnce()
            .filter { (it.lastPlayedAt ?: 0) < System.currentTimeMillis() - 14 * 24L * 3600_000 }
            .map { it.toSong() }
        return (old + forgottenLikes).distinctBy { it.id }.take(40)
    }

    /** Beğeni Radyosu: beğendiklerinden rastgele seçilenlerin radyosu. */
    private suspend fun likedRadio(): List<Song> {
        val liked = dao.likedSongsOnce().map { it.toSong() }
        if (liked.isEmpty()) return emptyList()
        val rnd = Random(daySeed())
        val seeds = liked.shuffled(rnd).take(3)
        return (liked.shuffled(rnd).take(8) + radiosOf(seeds, 12)).distinctBy { it.id }.shuffled(rnd).take(40)
    }

    // ------------------------------------------------------------ akıllı listeler

    suspend fun smart(kind: String): List<Song> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        when (kind) {
            "top50" -> dao.allSongsOnce().filter { it.playCount > 0 }
                .sortedWith(compareByDescending<com.alideniz.adtmusic.data.db.SongEntity> { it.playCount }.thenByDescending { it.totalPlayMs })
                .take(50).map { it.toSong() }
            "month_new" -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                dao.allSongsOnce().filter { it.addedAt >= start && it.playCount > 0 }
                    .sortedByDescending { it.addedAt }.map { it.toSong() }
            }
            "recent_fav" -> topRecent(30, 40)
            "forgotten" -> dao.likedSongsOnce()
                .filter { (it.lastPlayedAt ?: 0) < now - 60 * 24L * 3600_000 }
                .map { it.toSong() }
            "night" -> {
                val cal = Calendar.getInstance()
                dao.historySince(0)
                    .filter { cal.timeInMillis = it.playedAt; cal.get(Calendar.HOUR_OF_DAY) in 0..4 }
                    .groupBy { it.song.id }
                    .map { (_, l) -> l.first().song.toSong() to l.size }
                    .sortedByDescending { it.second }
                    .take(40).map { it.first }
            }
            "unplayed_liked" -> dao.likedSongsOnce().filter { it.playCount == 0 }.map { it.toSong() }
            else -> emptyList()
        }
    }

    companion object {
        val MIXES = listOf(
            ListDef("daily", "Günlük Karışım", "Sevdiklerin ve benzerleri · her gün yenilenir", Color(0xFFFF5A1F)),
            ListDef("discover", "Keşif Karışımı", "Hiç dinlemediğin, zevkine uygun şarkılar", Color(0xFF2F6BFF)),
            ListDef("throwback", "Geçmişe Yolculuk", "Bir zamanlar çok dinleyip unuttukların", Color(0xFFB07CFF)),
            ListDef("liked", "Beğeni Radyosu", "Beğendiklerinden yola çıkan radyo", Color(0xFFFF4D8D)),
        )
        val SMART = listOf(
            ListDef("top50", "En çok dinlediğim 50", "Tüm zamanların favorileri", Color(0xFF111111)),
            ListDef("recent_fav", "Son 30 günün favorileri", "Bu aralar başa sardıkların", Color(0xFFFF5A1F)),
            ListDef("month_new", "Bu ay keşfettiklerim", "Bu ay ilk kez dinlediğin şarkılar", Color(0xFF18B36B)),
            ListDef("night", "Gece şarkılarım", "Gece 00:00 – 05:00 arası dinlediklerin", Color(0xFF3B2F8F)),
            ListDef("forgotten", "Unutulmuş beğeniler", "2 aydır dinlemediğin beğenilerin", Color(0xFF64748B)),
            ListDef("unplayed_liked", "Beğendim ama dinlemedim", "Beğenip hiç çalmadıkların", Color(0xFFFFB400)),
        )

        fun def(kind: String): ListDef? = (MIXES + SMART).firstOrNull { it.kind == kind }
    }
}
