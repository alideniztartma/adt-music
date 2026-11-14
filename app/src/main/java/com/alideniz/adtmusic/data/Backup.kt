package com.alideniz.adtmusic.data

import com.alideniz.adtmusic.AppContainer
import com.alideniz.adtmusic.data.db.FollowedArtistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kütüphane yedeği: beğenilenler, çalma listeleri ve takip edilen sanatçılar
 * tek bir JSON dosyasına yazılır. Dosya kullanıcının seçtiği yere kaydedilir;
 * hiçbir yere gönderilmez.
 */
object Backup {

    private fun Song.json() = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist)
        put("thumbnail", thumbnail ?: ""); put("duration", durationSec); put("artistUrl", artistUrl ?: "")
    }

    private fun JSONObject.song() = Song(
        id = getString("id"),
        title = optString("title"),
        artist = optString("artist"),
        thumbnail = optString("thumbnail").ifBlank { null },
        durationSec = optLong("duration", -1),
        artistUrl = optString("artistUrl").ifBlank { null },
    )

    suspend fun export(app: AppContainer): String = withContext(Dispatchers.IO) {
        val dao = app.dao
        val root = JSONObject()
        root.put("app", "ADT Music")
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        root.put("liked", JSONArray().apply {
            dao.likedSongsOnce().forEach { e ->
                put(e.toSong().json().apply { put("likedAt", e.likedAt ?: 0L) })
            }
        })
        root.put("playlists", JSONArray().apply {
            dao.playlistsOnce().forEach { p ->
                put(JSONObject().apply {
                    put("name", p.name)
                    put("sourceUrl", p.sourceUrl ?: "")
                    put("songs", JSONArray().apply { dao.playlistSongsOnce(p.id).forEach { put(it.toSong().json()) } })
                })
            }
        })
        root.put("followed", JSONArray().apply {
            dao.followedArtistsOnce().forEach { a ->
                put(JSONObject().apply { put("name", a.name); put("thumbnail", a.thumbnail ?: ""); put("at", a.followedAt) })
            }
        })
        root.put("moments", JSONArray().apply {
            dao.momentsOnce().forEach { r ->
                val m = r.moment
                put(JSONObject().apply {
                    put("song", r.song().json())
                    put("pos", m.positionMs); put("mood", m.mood); put("note", m.note)
                    put("at", m.createdAt); put("revealAt", m.revealAt ?: 0L); put("revealed", m.revealed)
                })
            }
        })
        root.toString(2)
    }

    /** Yedeği mevcut kütüphaneye ekler (var olanları silmez). Eklenen öğe sayısını döner. */
    suspend fun import(app: AppContainer, text: String): Int = withContext(Dispatchers.IO) {
        val dao = app.dao
        val root = JSONObject(text)
        var count = 0
        root.optJSONArray("liked")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val song = o.song()
                dao.ensureSong(song)
                dao.setLiked(song.id, o.optLong("likedAt", System.currentTimeMillis()).takeIf { it > 0 } ?: System.currentTimeMillis())
                count++
            }
        }
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val songsArr = o.optJSONArray("songs") ?: JSONArray()
                val songs = (0 until songsArr.length()).map { songsArr.getJSONObject(it).song() }
                dao.createPlaylistWith(o.optString("name", "Liste"), songs, o.optString("sourceUrl").ifBlank { null })
                count++
            }
        }
        root.optJSONArray("followed")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.follow(FollowedArtistEntity(o.getString("name"), o.optString("thumbnail").ifBlank { null }, o.optLong("at", System.currentTimeMillis())))
                count++
            }
        }
        root.optJSONArray("moments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val song = o.getJSONObject("song").song()
                dao.ensureSong(song)
                dao.insertMoment(
                    com.alideniz.adtmusic.data.db.MomentEntity(
                        songId = song.id,
                        positionMs = o.optLong("pos"),
                        mood = o.optString("mood", "nostalgic"),
                        note = o.optString("note"),
                        createdAt = o.optLong("at", System.currentTimeMillis()),
                        revealAt = o.optLong("revealAt", 0L).takeIf { it > 0 },
                        revealed = o.optBoolean("revealed", false),
                    )
                )
                count++
            }
        }
        count
    }
}
