/**
 * adtmusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.adt.di

import com.music.adt.lyrics.LyricsHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsHelperEntryPoint {
    fun lyricsHelper(): LyricsHelper
}
