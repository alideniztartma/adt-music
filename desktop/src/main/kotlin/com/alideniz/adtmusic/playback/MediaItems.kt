package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.data.Song
import org.json.JSONArray
import org.json.JSONObject

// ---- sırayı kaydetme / geri yükleme (kaldığın yerden devam)

fun List<Song>.toJson(): String = JSONArray().also { arr ->
    forEach { s ->
        arr.put(JSONObject().apply {
            put("id", s.id); put("t", s.title); put("a", s.artist)
            put("th", s.thumbnail ?: ""); put("d", s.durationSec); put("au", s.artistUrl ?: "")
        })
    }
}.toString()

fun songsFromJson(json: String): List<Song> = runCatching {
    val arr = JSONArray(json)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Song(
            id = o.getString("id"),
            title = o.optString("t"),
            artist = o.optString("a"),
            thumbnail = o.optString("th").ifBlank { null },
            durationSec = o.optLong("d", -1),
            artistUrl = o.optString("au").ifBlank { null },
        )
    }
}.getOrDefault(emptyList())
