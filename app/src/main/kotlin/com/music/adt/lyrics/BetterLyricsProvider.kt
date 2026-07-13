/**
 * adtmusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.adt.lyrics

import android.content.Context
import com.music.adt.betterlyrics.BetterLyrics
import com.music.adt.constants.EnableBetterLyricsKey
import com.music.adt.utils.dataStore
import com.music.adt.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
