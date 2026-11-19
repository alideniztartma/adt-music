package com.alideniz.adtmusic.data

import com.alideniz.adtmusic.data.db.AdtDao
import com.alideniz.adtmusic.data.db.LyricsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Şarkı sözleri: LRCLIB (lrclib.net) — açık, ücretsiz, anahtar istemeyen ve
 * zaman damgalı (LRC) sözler sunan topluluk veritabanı.
 *
 * Musixmatch'in resmî API'si ücretli ve kullanım şartları üçüncü taraf
 * uygulamalarda senkron sözleri göstermeye izin vermediği için kullanılmadı.
 */
class LyricsRepository(
    private val client: OkHttpClient,
    private val dao: AdtDao,
) {

    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        dao.lyrics(song.id)?.let { cached ->
            return@withContext cached.toLyrics()
        }
        // Ağ / sunucu hatasında kaydetme: sonraki açılışta yeniden denensin
        val fetched = try { fetch(song) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            return@withContext null
        }
        dao.saveLyrics(
            LyricsEntity(
                songId = song.id,
                synced = fetched?.first,
                plain = fetched?.second,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        fetched?.let { (synced, plain) -> build(synced, plain) }
    }

    suspend fun clearFor(songId: String) = withContext(Dispatchers.IO) { dao.deleteLyrics(songId) }

    private fun LyricsEntity.toLyrics(): Lyrics? = build(synced, plain)

    private fun build(synced: String?, plain: String?): Lyrics? {
        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        }
        if (!plain.isNullOrBlank()) {
            return Lyrics(plain.lines().map { LyricLine(-1, it) }, synced = false)
        }
        return null
    }

    /** (synced, plain) çiftini döndürür. */
    private fun fetch(song: Song): Pair<String?, String?>? {
        val title = cleanTitle(song.title)
        val artist = cleanArtist(song.artist)

        // 1) Birebir eşleşme
        val getUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (song.durationSec > 0) addQueryParameter("duration", song.durationSec.toString()) }
            .build()
        request(getUrl.toString())?.let { body ->
            val o = JSONObject(body)
            val pair = o.optStringOrNull("syncedLyrics") to o.optStringOrNull("plainLyrics")
            if (pair.first != null || pair.second != null) return pair
        }

        // 2) Arama: süresi en yakın, senkron sözü olan sonucu seç
        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$artist $title")
            .build()
        val body = request(searchUrl.toString()) ?: return null
        val arr = JSONArray(body)
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dur = o.optDouble("duration", -1.0)
            val diff = if (song.durationSec > 0 && dur > 0) kotlin.math.abs(dur - song.durationSec) else 30.0
            val penalty = if (o.optStringOrNull("syncedLyrics") == null) 100.0 else 0.0
            val score = diff + penalty
            if (score < bestScore) { bestScore = score; best = o }
        }
        return best?.let { it.optStringOrNull("syncedLyrics") to it.optStringOrNull("plainLyrics") }
    }

    private fun request(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ADT Music (https://github.com/alideniztartma)")
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) return null // gerçekten bulunamadı
            if (!res.isSuccessful) throw java.io.IOException("LRCLIB ${res.code}")
            return res.body?.string()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private val TIME = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

        fun parseLrc(lrc: String): List<LyricLine> {
            val out = mutableListOf<LyricLine>()
            lrc.lineSequence().forEach { raw ->
                val matches = TIME.findAll(raw).toList()
                if (matches.isEmpty()) return@forEach
                val text = raw.substring(matches.last().range.last + 1).trim()
                matches.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val fracStr = m.groupValues[3]
                    val frac = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        else -> fracStr.take(3).toLong()
                    }
                    out += LyricLine(min * 60_000 + sec * 1000 + frac, text)
                }
            }
            return out.sortedBy { it.timeMs }
        }

        private val NOISE = Regex(
            "\\s*[(\\[](official|resmi|lyric|lyrics|audio|video|visualizer|music video|klip|hd|4k|remaster(ed)?|prod\\.?|ft\\.?|feat\\.?)[^)\\]]*[)\\]]",
            RegexOption.IGNORE_CASE,
        )

        fun cleanTitle(title: String): String {
            var t = title.replace(NOISE, "")
            // "Sanatçı - Şarkı" biçimindeki video başlıklarında şarkı kısmını al
            if (t.contains(" - ")) t = t.substringAfter(" - ")
            return t.replace(Regex("\\s+(ft|feat)\\.?\\s.*$", RegexOption.IGNORE_CASE), "").trim()
        }

        fun cleanArtist(artist: String): String =
            artist.removeSuffix(" - Topic").replace("VEVO", "").split(",", "&", " x ").first().trim()
    }
}
