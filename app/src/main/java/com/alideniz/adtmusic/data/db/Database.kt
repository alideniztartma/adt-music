package com.alideniz.adtmusic.data.db

import android.content.Context
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
)

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
)

data class ArtistStat(val artist: String, val plays: Int)

// ---------------------------------------------------------------------- DAO

@Dao
abstract class AdtDao {

    // ---- şarkılar
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongIgnore(song: SongEntity)

    @Query("UPDATE songs SET title = :title, artist = :artist, thumbnail = :thumbnail, duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END, artistUrl = COALESCE(:artistUrl, artistUrl) WHERE id = :id")
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

    @Query("SELECT s.*, h.playedAt AS playedAt FROM history h INNER JOIN songs s ON s.id = h.songId ORDER BY h.playedAt DESC LIMIT 500")
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
          (SELECT s.thumbnail FROM playlist_songs ps INNER JOIN songs s ON s.id = ps.songId
             WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1) AS cover
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
    open suspend fun createPlaylistWith(name: String, songs: List<Song>, sourceUrl: String?): Long {
        val id = insertPlaylist(PlaylistEntity(name = name, sourceUrl = sourceUrl))
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

    // ---- Wrapped
    @Query("SELECT s.*, h.playedAt AS playedAt FROM history h INNER JOIN songs s ON s.id = h.songId WHERE h.playedAt >= :since ORDER BY h.playedAt DESC")
    abstract suspend fun historySince(since: Long): List<HistoryRow>

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
        FollowedArtistEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AdtDatabase : RoomDatabase() {
    abstract fun dao(): AdtDao

    companion object {
        fun create(context: Context): AdtDatabase =
            Room.databaseBuilder(context, AdtDatabase::class.java, "adt_music.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /** 1.1: takip edilen sanatçılar tablosu. Mevcut veriler korunur. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `followed_artists` (`name` TEXT NOT NULL, `thumbnail` TEXT, `followedAt` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }
    }
}
