/**
 * adtmusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.adt.lyrics

import android.content.Context
import com.music.musixmatch.Musixmatch
import com.music.adt.constants.EnableMusixmatchKey
import com.music.adt.utils.dataStore
import com.music.adt.utils.get

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        Musixmatch.getLyrics(title, artist, duration, album)
}
