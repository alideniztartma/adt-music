package com.alideniz.adtmusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.alideniz.adtmusic.data.Song
import org.json.JSONArray
import org.json.JSONObject

private const val EXTRA_DURATION = "adt_duration"
private const val EXTRA_ARTIST_URL = "adt_artist_url"

fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(EXTRA_DURATION, durationSec)
        artistUrl?.let { putString(EXTRA_ARTIST_URL, it) }
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(MediaStack.uriFor(id))
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumbnail?.let { Uri.parse(it) })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)
                .build()
        )
        .build()
}

/** Denetleyiciden gelen öğede adres olmayabilir; kimlikten yeniden kurar. */
fun MediaItem.withPlayableUri(): MediaItem =
    buildUpon()
        .setUri(MediaStack.uriFor(mediaId))
        .setCustomCacheKey(mediaId)
        .build()

fun MediaItem.toSong(): Song {
    val md = mediaMetadata
    return Song(
        id = mediaId,
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString().orEmpty(),
        thumbnail = md.artworkUri?.toString(),
        durationSec = md.extras?.getLong(EXTRA_DURATION, -1L) ?: -1L,
        artistUrl = md.extras?.getString(EXTRA_ARTIST_URL),
    )
}

fun browsableItem(id: String, title: String, subtitle: String? = null, art: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(art?.let { Uri.parse(it) })
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

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
