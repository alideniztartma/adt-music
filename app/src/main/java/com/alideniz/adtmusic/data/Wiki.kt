package com.alideniz.adtmusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

data class Bio(
    val title: String,
    val text: String,
    val url: String,
    val image: String?,
    val lang: String,
)

/**
 * Sanatçı ve albüm biyografileri (Wikipedia).
 * Önce Türkçe Vikipedi denenir, yoksa ya da çok kısaysa İngilizce'ye düşülür.
 * Sonuçlar 14 gün önbellekte tutulur.
 */
class Wiki(private val context: Context, private val client: OkHttpClient) {

    private val dir by lazy { File(context.cacheDir, "bio").apply { mkdirs() } }
    private val memory = HashMap<String, Bio?>()

    private val musicTr = listOf("şarkıcı", "müzisyen", "grup", "rapçi", "besteci", "albüm", "şarkı", "müzik", "söz yazarı", "prodüktör")
    private val musicEn = listOf("singer", "band", "rapper", "musician", "album", "song", "music", "producer", "dj", "songwriter", "duo")

    suspend fun artist(name: String): Bio? = cached("artist:$name") {
        val q = quoteless(name)
        search("tr", "intitle:\"$q\"", name, musicTr, null)
            ?.takeIf { it.text.length >= 120 }
            ?: search("en", "intitle:\"$q\"", name, musicEn, null)
            ?: search("tr", "$q şarkıcı", name, musicTr, null)
    }

    suspend fun album(title: String, artist: String): Bio? = cached("album:$artist:$title") {
        val clean = cleanAlbum(title)
        val t = quoteless(clean)
        val a = quoteless(artist)
        search("tr", "intitle:\"$t\" \"$a\"", clean, listOf("albüm", "ep", "single", "teklisi"), artist)
            ?.takeIf { it.text.length >= 100 }
            ?: search("en", "intitle:\"$t\" \"$a\"", clean, listOf("album", "ep", "single", "mixtape"), artist)
            ?: search("en", "$t $a album", clean, listOf("album", "ep", "mixtape"), artist)
    }

    private suspend fun cached(key: String, load: suspend () -> Bio?): Bio? {
        synchronized(memory) { if (memory.containsKey(key)) return memory[key] }
        val file = File(dir, key.lowercase(Locale.ROOT).hashCode().toString() + ".json")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < 14 * 86_400_000L) {
            val bio = runCatching { fromJson(JSONObject(file.readText())) }.getOrNull()
            synchronized(memory) { memory[key] = bio }
            return bio
        }
        val bio = runCatching { load() }.getOrNull()
        runCatching { file.writeText(bio?.let { toJson(it) }?.toString() ?: "{}") }
        synchronized(memory) { memory[key] = bio }
        return bio
    }

    private suspend fun search(
        lang: String,
        query: String,
        mustMatch: String,
        keywords: List<String>,
        mentions: String?,
    ): Bio? = withContext(Dispatchers.IO) {
        val url = "https://$lang.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", query)
            .addQueryParameter("gsrlimit", "5")
            .addQueryParameter("prop", "extracts|pageimages|info")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("exsentences", "8")
            .addQueryParameter("piprop", "thumbnail")
            .addQueryParameter("pithumbsize", "600")
            .addQueryParameter("inprop", "url")
            .addQueryParameter("redirects", "1")
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "ADTMusic/1.4 (Android; kişisel kullanım)")
            .build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            ?: return@withContext null
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: return@withContext null
        val list = (0 until pages.length()).map { pages.getJSONObject(it) }
            .sortedBy { it.optInt("index", 99) }
        val want = norm(mustMatch)
        val mention = mentions?.let { norm(it) }
        val page = list.firstOrNull { p ->
            val t = norm(p.optString("title"))
            val ex = norm(p.optString("extract"))
            val titleOk = t.isNotBlank() && (t.contains(want) || want.contains(t))
            val musicOk = keywords.any { k -> ex.contains(norm(k)) }
            val mentionOk = mention == null || ex.contains(mention)
            titleOk && musicOk && mentionOk
        } ?: return@withContext null
        val extract = page.optString("extract").trim()
        if (extract.length < 40 || extract.contains("may refer to") || extract.contains("şu anlamlara gelebilir")) {
            return@withContext null
        }
        Bio(
            title = page.optString("title"),
            text = extract.replace(Regex("\\s*\\([^()]*\\d{4}[^()]*\\)"), "").replace(Regex("\n{2,}"), "\n\n"),
            url = page.optString("fullurl"),
            image = page.optJSONObject("thumbnail")?.optString("source"),
            lang = lang,
        )
    }

    private fun quoteless(s: String) = s.replace("\"", "").trim()

    /** "Albüm Adı (Deluxe Edition)" → "Albüm Adı" */
    private fun cleanAlbum(s: String) = s.replace(Regex("\\s*[\\(\\[][^\\)\\]]*(deluxe|edition|remaster|version|expanded|bonus)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "").trim()

    private fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale("tr")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ı', 'i')
        return n.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun toJson(b: Bio) = JSONObject().put("t", b.title).put("x", b.text).put("u", b.url).put("i", b.image ?: "").put("l", b.lang)

    private fun fromJson(o: JSONObject): Bio? {
        if (!o.has("x")) return null
        return Bio(o.getString("t"), o.getString("x"), o.getString("u"), o.optString("i").ifBlank { null }, o.optString("l", "tr"))
    }
}
