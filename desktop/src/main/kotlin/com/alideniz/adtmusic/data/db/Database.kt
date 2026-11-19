package com.alideniz.adtmusic.data.db

import com.alideniz.adtmusic.platform.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.alideniz.adtmusic.data.Song
import kotlinx.coroutines.flow.Flow

// ------------------------------------------------------------------ tablolar

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Long,
    val artistUrl: String?,
    val likedAt: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val totalPlayMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toSong() = Song(id, title, artist, thumbnail, duration, artistUrl)
}

fun Song.toEntity() = SongEntity(
    id = id, title = title, artist = artist, thumbnail = thumbnail,
    duration = durationSec, artistUrl = artistUrl,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceUrl: String? = null,
    @ColumnInfo(defaultValue = "") val description: String = "",
    /** Oluşturulan kapak görselinin dosya yolu (yoksa şarkı kapakları kullanılır). */
    val coverPath: String? = null,
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
    /** Gerçekten dinlenen süre. -1: eski kayıt (süre bilinmiyor). */
    @ColumnInfo(defaultValue = "-1") val listenedMs: Long = 0,
)

/** Music Moments: bir şarkının belli bir anına bağlı anı. */
@Entity(tableName = "moments")
data class MomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val positionMs: Long,
    val mood: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Zaman kapsülü: bu tarihte yeniden karşına çıkar. */
    val revealAt: Long? = null,
    val revealed: Boolean = false,
)

data class MomentRow(
    @Embedded val moment: MomentEntity,
    val songTitle: String?,
    val songArtist: String?,
    val songThumb: String?,
    val songDuration: Long?,
) {
    fun song(): Song = Song(moment.songId, songTitle.orEmpty(), songArtist.orEmpty(), songThumb, songDuration ?: -1)
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val at: Long,
)

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val name: String,
    val thumbnail: String?,
    val followedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: String,
    val synced: String?,
    val plain: String?,
    val fetchedAt: Long,
)

// ------------------------------------------------------------ sorgu sonuçları

data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
    val cover: String?,
)

data class HistoryRow(
    @Embedded val song: SongEntity,
    val playedAt: Long,
    val listenedMs: Long = -1,
)

data class ArtistStat(val artist: String, val plays: Int)

// ---------------------------------------------------------------------- DAO

@Dao
abstract class AdtDao {

    // ---- şarkılar
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongIgnore(song: SongEntity)

    @Query("UPDATE songs SET title = CASE WHEN :title != '' THEN :title ELSE title END, artist = CASE WHEN :artist != '' THEN :artist ELSE artist END, thumbnail = COALESCE(:thumbnail, thumbnail), duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END, artistUrl = COALESCE(:artistUrl, artistUrl) WHERE id = :id")
    abstract suspend fun updateSongMeta(id: String, title: String, artist: String, thumbnail: String?, duration: Long, artistUrl: String?)

    @Transaction
    open suspend fun ensureSong(song: Song) {
        insertSongIgnore(song.toEntity())
        updateSongMeta(song.id, song.title, song.artist, song.thumbnail, song.durationSec, song.artistUrl)
    }

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun songsByIds(ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract fun songsByIdsFlow(ids: List<String>): Flow<List<SongEntity>>

    // ---- beğeniler
    @Query("SELECT * FROM songs WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    abstract fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT id FROM songs WHERE likedAt IS NOT NULL")
    abstract fun likedIds(): Flow<List<String>>

    @Query("UPDATE songs SET likedAt = :at WHERE id = :id")
    abstract suspend fun setLiked(id: String, at: Long?)

    // ---- dinleme geçmişi ve istatistik
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :at WHERE id = :id")
    abstract suspend fun markPlayed(id: String, at: Long)

    @Query("UPDATE songs SET totalPlayMs = totalPlayMs + :ms WHERE id = :id")
    abstract suspend fun addPlayTime(id: String, ms: Long)

    @Insert
    abstract suspend fun insertHistory(h: HistoryEntity)

    @Query("SELECT s.*, h.playedAt AS playedAt, h.listenedMs AS listenedMs FROM history h INNER JOIN songs s ON s.id = h.songId ORDER BY h.playedAt DESC LIMIT 500")
    abstract fun history(): Flow<List<HistoryRow>>

    @Query("DELETE FROM history")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM history WHERE songId = :songId")
    abstract suspend fun removeFromHistory(songId: String)

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    abstract fun recentSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    abstract suspend fun lastPlayed(): SongEntity?

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    abstract fun topSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT s.artist AS artist, COUNT(*) AS plays FROM history h INNER JOIN songs s ON s.id = h.songId GROUP BY s.artist ORDER BY plays DESC LIMIT :limit")
    abstract fun topArtists(limit: Int): Flow<List<ArtistStat>>

    @Query("SELECT COUNT(*) FROM history")
    abstract fun totalPlays(): Flow<Int>

    @Query("SELECT IFNULL(SUM(totalPlayMs), 0) FROM songs")
    abstract fun totalListenMs(): Flow<Long>

    @Query("SELECT COUNT(DISTINCT songId) FROM history")
    abstract fun distinctSongs(): Flow<Int>

    // ---- çalma listeleri
    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount,
          COALESCE(p.coverPath, (SELECT s.thumbnail FROM playlist_songs ps INNER JOIN songs s ON s.id = ps.songId
             WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1)) AS cover
        FROM playlists p ORDER BY p.createdAt DESC
        """
    )
    abstract fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT s.* FROM playlist_songs ps INNER JOIN songs s ON s.id = ps.songId WHERE ps.playlistId = :id ORDER BY ps.position")
    abstract fun playlistSongs(id: Long): Flow<List<SongEntity>>

    @Query("SELECT s.* FROM playlist_songs ps INNER JOIN songs s ON s.id = ps.songId WHERE ps.playlistId = :id ORDER BY ps.position")
    abstract suspend fun playlistSongsOnce(id: Long): List<SongEntity>

    @Insert
    abstract suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)

    @Query("UPDATE playlists SET description = :text WHERE id = :id")
    abstract suspend fun setPlaylistDescription(id: Long, text: String)

    @Query("UPDATE playlists SET coverPath = :path WHERE id = :id")
    abstract suspend fun setPlaylistCover(id: Long, path: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    abstract suspend fun clearPlaylist(id: Long)

    @Transaction
    open suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    @Query("INSERT OR IGNORE INTO playlist_songs (playlistId, songId, position, addedAt) VALUES (:playlistId, :songId, (SELECT IFNULL(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId), :at)")
    abstract suspend fun addToPlaylistRaw(playlistId: Long, songId: String, at: Long)

    @Transaction
    open suspend fun addToPlaylist(playlistId: Long, song: Song) {
        ensureSong(song)
        addToPlaylistRaw(playlistId, song.id, System.currentTimeMillis())
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun setPosition(playlistId: Long, songId: String, position: Int)

    @Transaction
    open suspend fun reorderPlaylist(playlistId: Long, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setPosition(playlistId, id, index) }
    }

    @Transaction
    open suspend fun createPlaylistWith(name: String, songs: List<Song>, sourceUrl: String?, description: String = ""): Long {
        val id = insertPlaylist(PlaylistEntity(name = name, sourceUrl = sourceUrl, description = description))
        songs.forEach { addToPlaylist(id, it) }
        return id
    }

    // ---- arama geçmişi
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveSearch(e: SearchHistoryEntity)

    @Query("SELECT `query` FROM search_history ORDER BY at DESC LIMIT 12")
    abstract fun searchHistory(): Flow<List<String>>

    @Query("DELETE FROM search_history WHERE `query` = :q")
    abstract suspend fun deleteSearch(q: String)

    @Query("DELETE FROM search_history")
    abstract suspend fun clearSearches()

    // ---- takip edilen sanatçılar
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun follow(e: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE name = :name")
    abstract suspend fun unfollow(name: String)

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    abstract fun followedArtists(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    abstract suspend fun followedArtistsOnce(): List<FollowedArtistEntity>

    @Query("SELECT name FROM followed_artists")
    abstract fun followedNames(): Flow<List<String>>

    /** Son dinleme kaydına gerçekten dinlenen süreyi ekler. */
    @Query("UPDATE history SET listenedMs = MAX(listenedMs, 0) + :ms WHERE id = (SELECT id FROM history WHERE songId = :songId ORDER BY playedAt DESC LIMIT 1)")
    abstract suspend fun addListened(songId: String, ms: Long)

    // ---- Music Moments
    @Insert
    abstract suspend fun insertMoment(m: MomentEntity): Long

    @Query("DELETE FROM moments WHERE id = :id")
    abstract suspend fun deleteMoment(id: Long)

    @Query("SELECT m.*, s.title AS songTitle, s.artist AS songArtist, s.thumbnail AS songThumb, s.duration AS songDuration FROM moments m LEFT JOIN songs s ON s.id = m.songId ORDER BY m.createdAt DESC")
    abstract fun moments(): Flow<List<MomentRow>>

    @Query("SELECT * FROM moments WHERE songId = :songId ORDER BY positionMs")
    abstract fun momentsForSong(songId: String): Flow<List<MomentEntity>>

    @Query("SELECT m.*, s.title AS songTitle, s.artist AS songArtist, s.thumbnail AS songThumb, s.duration AS songDuration FROM moments m LEFT JOIN songs s ON s.id = m.songId WHERE m.revealAt IS NOT NULL AND m.revealAt <= :now AND m.revealed = 0 ORDER BY m.revealAt")
    abstract suspend fun dueCapsules(now: Long): List<MomentRow>

    @Query("UPDATE moments SET revealed = 1 WHERE id = :id")
    abstract suspend fun markRevealed(id: Long)

    @Query("SELECT m.*, s.title AS songTitle, s.artist AS songArtist, s.thumbnail AS songThumb, s.duration AS songDuration FROM moments m LEFT JOIN songs s ON s.id = m.songId")
    abstract suspend fun momentsOnce(): List<MomentRow>

    @Query("SELECT COUNT(*) FROM moments")
    abstract fun momentCount(): Flow<Int>

    // ---- şarkı hakkında
    @Query("SELECT MIN(playedAt) FROM history WHERE songId = :id")
    abstract suspend fun firstPlayed(id: String): Long?

    @Query("SELECT COUNT(*) FROM history WHERE songId = :id AND (listenedMs < 0 OR listenedMs >= 30000)")
    abstract suspend fun validPlays(id: String): Int

    @Query("SELECT COALESCE(SUM(CASE WHEN listenedMs > 0 THEN listenedMs ELSE 0 END), 0) FROM history WHERE songId = :id")
    abstract suspend fun listenedTotal(id: String): Long

    @Query("SELECT COUNT(*) FROM moments WHERE songId = :id")
    abstract suspend fun momentCountFor(id: String): Int

    // ---- Wrapped
    @Query("SELECT s.*, h.playedAt AS playedAt, h.listenedMs AS listenedMs FROM history h INNER JOIN songs s ON s.id = h.songId WHERE h.playedAt >= :since ORDER BY h.playedAt DESC")
    abstract suspend fun historySince(since: Long): List<HistoryRow>

    @Query("SELECT * FROM songs")
    abstract suspend fun allSongsOnce(): List<SongEntity>

    // ---- yedekleme
    @Query("SELECT * FROM songs WHERE likedAt IS NOT NULL")
    abstract suspend fun likedSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM playlists")
    abstract suspend fun playlistsOnce(): List<PlaylistEntity>

    // ---- sözler
    @Query("SELECT * FROM lyrics WHERE songId = :id")
    abstract suspend fun lyrics(id: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveLyrics(e: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE songId = :id")
    abstract suspend fun deleteLyrics(id: String)
}

@Database(
    entities = [
        SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        HistoryEntity::class, SearchHistoryEntity::class, LyricsEntity::class,
        FollowedArtistEntity::class, MomentEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AdtDatabase : RoomDatabase() {
    abstract fun dao(): AdtDao

    companion object {
        /** Masaüstünde veritabanı sıfırdan, en son şemayla oluşturulur (eski sürüm yok). */
        fun create(context: Context): AdtDatabase =
            Room.databaseBuilder<AdtDatabase>(name = File(context.filesDir, "adt_music.db").absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
