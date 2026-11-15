package com.alideniz.adtmusic.data

import android.util.Base64
import androidx.compose.runtime.Immutable
import com.alideniz.adtmusic.data.db.AdtDao
import com.alideniz.adtmusic.data.db.HistoryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.sqrt

@Immutable
data class Streak(val current: Int, val longest: Int, val listenedToday: Boolean, val totalDays: Int)

@Immutable
data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val value: Int,
    val goal: Int,
) {
    val unlocked: Boolean get() = value >= goal
    val progress: Float get() = (value.toFloat() / goal).coerceIn(0f, 1f)
}

@Immutable
data class Weekly(
    val minutes: Long,
    val previousMinutes: Long,
    val plays: Int,
    val distinctSongs: Int,
    val topSong: Pair<Song, Int>?,
    val topArtist: Pair<String, Int>?,
)

@Immutable
data class TasteProfile(
    val name: String,
    val artists: List<String>,
    val songs: List<Song>,
)

@Immutable
data class Compatibility(
    val percent: Int,
    val label: String,
    val commonArtists: List<String>,
    val commonSongs: List<Song>,
    val suggestions: List<Song>,
    val friendName: String,
)

/** Seri, rozet, haftalık özet ve Mixtape eşleşmesi. Hepsi cihazda hesaplanır. */
object Insights {

    private fun valid(r: HistoryRow) = r.listenedMs < 0 || r.listenedMs >= 30_000L

    private fun epochDay(ms: Long): Long {
        val offset = TimeZone.getDefault().getOffset(ms)
        return (ms + offset) / 86_400_000L
    }

    fun streak(rows: List<HistoryRow>): Streak {
        val days = rows.filter(::valid).map { epochDay(it.playedAt) }.toSortedSet()
        if (days.isEmpty()) return Streak(0, 0, false, 0)
        val today = epochDay(System.currentTimeMillis())
        var longest = 1
        var run = 1
        var prev: Long? = null
        for (d in days) {
            if (prev != null) {
                run = if (d == prev + 1) run + 1 else 1
                if (run > longest) longest = run
            }
            prev = d
        }
        val listenedToday = today in days
        var current = 0
        var cursor = if (listenedToday) today else today - 1
        while (cursor in days) {
            current++
            cursor--
        }
        return Streak(current, maxOf(longest, current), listenedToday, days.size)
    }

    fun weekly(rows: List<HistoryRow>): Weekly {
        val now = System.currentTimeMillis()
        val week = 7 * 86_400_000L
        val thisWeek = rows.filter { it.playedAt >= now - week }
        val lastWeek = rows.filter { it.playedAt in (now - 2 * week) until (now - week) }
        val validThis = thisWeek.filter(::valid)
        return Weekly(
            minutes = thisWeek.sumOf { it.listenedMs.coerceAtLeast(0) } / 60_000,
            previousMinutes = lastWeek.sumOf { it.listenedMs.coerceAtLeast(0) } / 60_000,
            plays = validThis.size,
            distinctSongs = validThis.map { it.song.id }.distinct().size,
            topSong = validThis.groupBy { it.song.id }.maxByOrNull { it.value.size }?.let { it.value.first().song.toSong() to it.value.size },
            topArtist = validThis.groupBy { it.song.artist }.filterKeys { it.isNotBlank() }.maxByOrNull { it.value.size }?.let { it.key to it.value.size },
        )
    }

    suspend fun badges(dao: AdtDao, downloads: Int): List<Badge> = withContext(Dispatchers.IO) {
        val rows = dao.historySince(0)
        val valid = rows.filter(::valid)
        val songs = dao.allSongsOnce()
        val liked = songs.count { it.likedAt != null }
        val playlists = dao.playlistsOnce().size
        val moments = dao.momentsOnce()
        val followed = dao.followedArtistsOnce().size
        val streak = streak(rows)
        val cal = Calendar.getInstance()
        val hours = valid.map { cal.timeInMillis = it.playedAt; cal.get(Calendar.HOUR_OF_DAY) }
        val listenHours = (songs.sumOf { it.totalPlayMs } / 3_600_000L).toInt()
        val maxSong = valid.groupBy { it.song.id }.maxOfOrNull { it.value.size } ?: 0
        val distinctSongs = valid.map { it.song.id }.distinct().size
        val distinctArtists = valid.map { it.song.artist }.distinct().size

        listOf(
            Badge("first", "İlk Adım", "İlk şarkını dinle", "♪", valid.size, 1),
            Badge("curious", "Meraklı", "50 farklı şarkı dinle", "🔎", distinctSongs, 50),
            Badge("explorer", "Kâşif", "250 farklı şarkı dinle", "🧭", distinctSongs, 250),
            Badge("worldwide", "Geniş Ufuk", "50 farklı sanatçı dinle", "🌍", distinctArtists, 50),
            Badge("night", "Gece Kuşu", "Gece 00:00–05:00 arası 25 şarkı dinle", "🌙", hours.count { it in 0..4 }, 25),
            Badge("early", "Erken Kalkan", "Sabah 05:00–09:00 arası 25 şarkı dinle", "🌅", hours.count { it in 5..8 }, 25),
            Badge("marathon", "Maratoncu", "Toplam 10 saat dinle", "⏱", listenHours, 10),
            Badge("legend", "Efsane", "Toplam 100 saat dinle", "🏆", listenHours, 100),
            Badge("loyal", "Sadık", "Aynı şarkıyı 25 kez dinle", "🔁", maxSong, 25),
            Badge("collector", "Koleksiyoncu", "50 şarkı beğen", "♥", liked, 50),
            Badge("curator", "Liste Ustası", "5 çalma listesi oluştur", "📋", playlists, 5),
            Badge("fan", "Hayran", "5 sanatçı takip et", "⭐", followed, 5),
            Badge("memories", "Anı Avcısı", "10 anı bırak", "✦", moments.size, 10),
            Badge("capsule", "Zaman Yolcusu", "Bir zaman kapsülü mühürle", "💌", moments.count { it.moment.revealAt != null }, 1),
            Badge("offline", "Çevrimdışı", "20 şarkı indir", "⬇", downloads, 20),
            Badge("streak7", "Bir Hafta", "7 gün üst üste müzik dinle", "📅", streak.longest, 7),
            Badge("streak30", "Alışkanlık", "30 gün üst üste müzik dinle", "🗓", streak.longest, 30),
            Badge("streak100", "Durdurulamaz", "100 gün üst üste müzik dinle", "💯", streak.longest, 100),
        )
    }

    // ------------------------------------------------------------ zevk karşılaştırma

    suspend fun myProfile(dao: AdtDao, name: String): TasteProfile = withContext(Dispatchers.IO) {
        val rows = dao.historySince(0).filter(::valid)
        val artists = rows.groupBy { it.song.artist }.filterKeys { it.isNotBlank() }
            .entries.sortedByDescending { it.value.size }.take(15).map { it.key }
        val songs = rows.groupBy { it.song.id }.entries.sortedByDescending { it.value.size }
            .take(25).map { it.value.first().song.toSong() }
        TasteProfile(name, artists, songs)
    }

    fun encode(p: TasteProfile): String {
        val o = JSONObject().apply {
            put("n", p.name)
            put("a", JSONArray(p.artists))
            put("s", JSONArray().apply {
                p.songs.forEach { put(JSONArray().put(it.id).put(it.title.take(60)).put(it.artist.take(40))) }
            })
        }
        val bytes = ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(o.toString().toByteArray()) } }.toByteArray()
        return "MIX1:" + Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decode(code: String): TasteProfile? = runCatching {
        val t = code.trim()
        // Paylaşılan mesajın içinden kodu bul (eski "ADT1:" kodları da geçerli)
        val raw = when {
            t.contains("MIX1:") -> t.substringAfter("MIX1:")
            t.contains("ADT1:") -> t.substringAfter("ADT1:")
            else -> ""
        }.trim().takeWhile { !it.isWhitespace() }.ifEmpty { return null }
        val bytes = Base64.decode(raw, Base64.NO_WRAP or Base64.URL_SAFE)
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        val o = JSONObject(json)
        val a = o.getJSONArray("a")
        val s = o.getJSONArray("s")
        TasteProfile(
            name = o.optString("n", "Arkadaşın"),
            artists = (0 until a.length()).map { a.getString(it) },
            songs = (0 until s.length()).map {
                val x = s.getJSONArray(it)
                Song(x.getString(0), x.optString(1), x.optString(2), "https://i.ytimg.com/vi/${x.getString(0)}/hqdefault.jpg")
            },
        )
    }.getOrNull()

    fun compare(me: TasteProfile, friend: TasteProfile): Compatibility {
        val myArtists = me.artists.map { it.lowercase() }
        val theirArtists = friend.artists.map { it.lowercase() }
        // Sıraya göre ağırlıklı ortak sanatçı puanı
        var artistScore = 0.0
        var artistMax = 0.0
        myArtists.forEachIndexed { i, a ->
            val w = 1.0 / (1 + i)
            artistMax += w
            val j = theirArtists.indexOf(a)
            if (j >= 0) artistScore += w * (1.0 - kotlin.math.abs(i - j) / 30.0).coerceAtLeast(0.3)
        }
        val artistPart = if (artistMax > 0) artistScore / artistMax else 0.0
        val mySongs = me.songs.map { it.id }.toSet()
        val common = friend.songs.filter { it.id in mySongs }
        val songPart = if (me.songs.isNotEmpty() && friend.songs.isNotEmpty())
            common.size.toDouble() / minOf(me.songs.size, friend.songs.size) else 0.0
        val raw = (artistPart * 0.65 + songPart * 0.35).coerceIn(0.0, 1.0)
        val percent = (sqrt(raw) * 100).toInt().coerceIn(if (raw > 0) 8 else 0, 99)
        val label = when {
            percent >= 85 -> "Aynı kaset: müzik ikizisiniz"
            percent >= 65 -> "Ortak mixtape hazır"
            percent >= 40 -> "Aynı kasette iyi çalarsınız"
            percent >= 20 -> "A yüzü sen, B yüzü o"
            else -> "Bambaşka kasetler"
        }
        return Compatibility(
            percent = percent,
            label = label,
            commonArtists = friend.artists.filter { it.lowercase() in myArtists }.take(8),
            commonSongs = common.take(10),
            suggestions = friend.songs.filter { it.id !in mySongs }.take(12),
            friendName = friend.name,
        )
    }
}
