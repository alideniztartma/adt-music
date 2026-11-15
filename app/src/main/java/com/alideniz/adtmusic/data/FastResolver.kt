package com.alideniz.adtmusic.data

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.nio.charset.StandardCharsets

/**
 * Hafif akış çözücü.
 *
 * NewPipe'ın tam çıkarımı bir şarkı için 4–5 istek atar (iki ayrı oturum açma, oynatıcı,
 * web üst verisi, ilgili videolar). Çalmak için yalnızca ses adresi gerekir; bu sınıf
 * tek bir oynatıcı isteğiyle onu alır ve aynı oturumu (visitorData) saatlerce yeniden
 * kullanır — gerçek bir uygulama gibi. Böylece YouTube'a giden istek sayısı ~4 kat azalır
 * ve "bot" sanılıp IP'nin engellenmesi çok daha geç (çoğu zaman hiç) olur.
 */
class FastResolver(private val settings: Settings) {

    @Volatile private var visitor: String? = null
    @Volatile private var visitorAt = 0L

    /** Oturumu at: bir sonraki istek yeni bir visitorData ile yapılır. */
    fun resetSession() {
        visitor = null
        visitorAt = 0
        settings.ytVisitorData.set("")
        settings.ytVisitorAt.set(0L)
    }

    private fun visitorData(): String {
        val now = System.currentTimeMillis()
        visitor?.let { if (now - visitorAt < SESSION_MS) return it }
        val saved = settings.ytVisitorData.value
        val savedAt = settings.ytVisitorAt.value
        if (saved.isNotBlank() && now - savedAt < SESSION_MS) {
            visitor = saved; visitorAt = savedAt
            return saved
        }
        val info = InnertubeClientRequestInfo.ofVisionOsClient()
        val loc = NewPipe.getPreferredLocalization()
        val fresh = YoutubeParsingHelper.getVisitorDataFromInnertube(
            info, loc, NewPipe.getPreferredContentCountry(), headers(), YoutubeParsingHelper.YOUTUBEI_V1_URL, null, false,
        )
        visitor = fresh; visitorAt = now
        settings.ytVisitorData.set(fresh)
        settings.ytVisitorAt.set(now)
        return fresh
    }

    private fun headers(): Map<String, List<String>> = mapOf(
        "User-Agent" to listOf(YoutubeParsingHelper.getVisionOsUserAgent(NewPipe.getPreferredLocalization())),
        "X-Goog-Api-Format-Version" to listOf("2"),
    )

    /**
     * Ses akışını çözer. YouTube sınırladıysa [YouTubeLimitedException],
     * şarkı gerçekten çalınamıyorsa [IllegalStateException] atar.
     * Beklenmedik bir yanıtta null döner (çağıran tam çıkarıma düşer).
     */
    fun resolve(id: String, quality: String): ResolvedStream? {
        val loc = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val info = InnertubeClientRequestInfo.ofVisionOsClient()
        info.clientInfo.visitorData = visitorData()
        val cpn = YoutubeParsingHelper.generateContentPlaybackNonce()
        val builder = YoutubeParsingHelper.prepareJsonBuilder(loc, country, info, null)
            .value(YoutubeParsingHelper.VIDEO_ID, id)
            .value(YoutubeParsingHelper.CPN, cpn)
            .value(YoutubeParsingHelper.CONTENT_CHECK_OK, true)
            .value(YoutubeParsingHelper.RACY_CHECK_OK, true)
        val body = JsonWriter.string(builder.done()).toByteArray(StandardCharsets.UTF_8)
        val url = YoutubeParsingHelper.YOUTUBEI_V1_GAPIS_URL + "player?" +
            YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER +
            "&t=" + YoutubeParsingHelper.generateTParameter() + "&id=" + id

        val response = NewPipe.getDownloader().postWithContentTypeJson(url, headers(), body, loc)
        if (response.responseCode() == 403 || response.responseCode() == 429) {
            resetSession()
            throw YouTubeLimitedException("YouTube isteği reddetti (${response.responseCode()})")
        }
        val text = response.responseBody() ?: return null
        val json: JsonObject = runCatching { JsonParser.`object`().from(text) }.getOrNull() ?: return null

        val play = json.getObject("playabilityStatus")
        val status = play.getString("status", "")
        if (status != "OK") {
            val reason = play.getString("reason", "") + " " + play.getObject("errorScreen").toString()
            val lower = reason.lowercase()
            if (status == "LOGIN_REQUIRED" && ("bot" in lower)) {
                resetSession()
                throw YouTubeLimitedException("YouTube bot doğrulaması istedi")
            }
            if (status == "LOGIN_REQUIRED") return null // yaş sınırı vb.: tam çıkarım denesin
            if (status == "UNPLAYABLE" || status == "ERROR") {
                throw IllegalStateException(play.getString("reason", "Bu şarkı çalınamıyor") ?: "Bu şarkı çalınamıyor")
            }
            return null
        }

        val formats: JsonArray = json.getObject("streamingData").getArray("adaptiveFormats")
        val audio = (0 until formats.size).mapNotNull { formats.getObject(it) }
            .filter { it.getString("mimeType", "").startsWith("audio/") && it.has("url") }
            // Dublaj/yan ses yerine orijinal ses
            .filter { f ->
                val track = f.getObject("audioTrack")
                track.isEmpty() || track.getBoolean("audioIsDefault", true)
            }
        if (audio.isEmpty()) return null

        val pick = when (quality) {
            "low" -> audio.minByOrNull { it.getInt("bitrate", Int.MAX_VALUE) }
            "normal" -> audio.minByOrNull { kotlin.math.abs(it.getInt("averageBitrate", it.getInt("bitrate", 128_000)) - 128_000) }
            else -> audio.maxByOrNull { it.getInt("averageBitrate", it.getInt("bitrate", 0)) }
        } ?: return null

        var streamUrl = pick.getString("url")
        streamUrl = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id, streamUrl)
        streamUrl += "&cpn=$cpn"

        val expire = Regex("[?&]expire=(\\d+)").find(streamUrl)?.groupValues?.get(1)?.toLongOrNull()
        return ResolvedStream(
            url = streamUrl,
            mimeType = pick.getString("mimeType", "").substringBefore(';').ifBlank { null },
            bitrate = pick.getInt("averageBitrate", pick.getInt("bitrate", 0)),
            expiresAtMs = expire?.times(1000) ?: (System.currentTimeMillis() + 3 * 3600_000L),
        )
    }

    companion object {
        /** Aynı oturum 12 saat kullanılır. */
        private const val SESSION_MS = 12 * 3600_000L
    }
}
