package com.alideniz.adtmusic.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.Song

sealed interface SheetSpec {
    data class SongOptions(val song: Song, val playlistId: Long? = null, val queueIndex: Int? = null) : SheetSpec
    data class AddToPlaylist(val songs: List<Song>) : SheetSpec
    data object SleepTimer : SheetSpec
    data object Speed : SheetSpec
    data object Queue : SheetSpec
    data class Input(
        val title: String,
        val initial: String,
        val confirm: String,
        val onConfirm: (String) -> Unit,
    ) : SheetSpec
    data class Confirm(
        val title: String,
        val message: String,
        val confirm: String,
        val onConfirm: () -> Unit,
    ) : SheetSpec
}

/** Ekranlar arası paylaşılan arayüz durumu: açık sayfa, oynatıcı, alt panel. */
@Stable
class AppUi(
    private val navigateImpl: (String) -> Unit,
    private val backImpl: () -> Unit,
) {
    var sheet by mutableStateOf<SheetSpec?>(null)
        private set
    var playerExpanded by mutableStateOf(false)
    var lyricsMode by mutableStateOf(false)

    fun show(spec: SheetSpec) {
        sheet = spec
    }

    fun dismiss() {
        sheet = null
    }

    fun navigate(route: String) {
        playerExpanded = false
        sheet = null
        navigateImpl(route)
    }

    fun back() = backImpl()

    fun openPlayer(lyrics: Boolean = false) {
        lyricsMode = lyrics
        playerExpanded = true
    }
}

val LocalAppUi = staticCompositionLocalOf<AppUi> { error("AppUi sağlanmadı") }

/** Mini oynatıcı ve alt menünün kapladığı yükseklik; listelerin sonuna boşluk olarak eklenir. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val COLLECTION = "collection?url={url}&kind={kind}"
    const val ARTIST = "artist?name={name}&thumb={thumb}"
    const val PLAYLIST = "playlist/{id}"
    const val MOOD = "mood?label={label}&query={query}"

    fun collection(url: String, kind: CollectionKind) = "collection?url=${Uri.encode(url)}&kind=${kind.name}"
    fun artist(name: String, thumb: String?) = "artist?name=${Uri.encode(name)}&thumb=${Uri.encode(thumb ?: "")}"
    fun playlist(id: Long) = "playlist/$id"
    fun mood(label: String, query: String) = "mood?label=${Uri.encode(label)}&query=${Uri.encode(query)}"
}
