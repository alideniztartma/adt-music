package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alideniz.adtmusic.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.ArtistDetail
import com.alideniz.adtmusic.data.CollectionDetail
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.CollectionRef
import com.alideniz.adtmusic.data.SearchFilter
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.CollectionCard
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.DownloadButton
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.ListFilterField
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.matchesQuery
import com.alideniz.adtmusic.ui.components.queryWords
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.SectionTitle
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.components.SongRow
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatDuration
import com.alideniz.adtmusic.util.shareText
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** Şarkı listesi: numara, çalan şarkı vurgusu, beğeni ve indirme rozetleri. */
fun LazyListScope.songList(
    songs: List<Song>,
    currentId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    downloads: Map<String, com.alideniz.adtmusic.playback.DlInfo>,
    numbered: Boolean,
    onPlay: (Int) -> Unit,
    onMore: (Song) -> Unit,
    keyPrefix: String = "s",
) {
    itemsIndexed(songs, key = { i, s -> "$keyPrefix-$i-${s.id}" }) { i, s ->
        SongRow(
            song = s,
            onClick = { onPlay(i) },
            onMore = { onMore(s) },
            index = if (numbered) i + 1 else null,
            isCurrent = currentId == s.id,
            isPlaying = isPlaying,
            liked = s.id in likedIds,
            download = downloads[s.id],
        )
    }
}

@Composable
private fun PlayButtons(
    songs: List<Song>,
    extra: @Composable () -> Unit = {},
) {
    val app = LocalContext.current.container
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Btn("Çal", { app.player.play(songs) }, icon = AdtIcons.Play, style = BtnStyle.ACCENT)
        Btn("Karıştır", { app.player.play(songs, shuffle = true) }, icon = AdtIcons.Shuffle, style = BtnStyle.OUTLINE)
        Spacer(Modifier.weight(1f))
        extra()
    }
}

@Composable
fun CollectionScreen(url: String, kind: CollectionKind) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<Load<CollectionDetail>>(Load.Loading, url, reload) {
        value = Load.Loading
        value = runCatching { app.youtube.collection(url) }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "Yüklenemedi") })
    }
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = remember(likedIds) { likedIds.toSet() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconBtn(AdtIcons.Back, "Geri", { ui.back() })
            }
        }
        when (val s = state) {
            Load.Loading -> item { LoadingBlock() }
            is Load.Err -> item {
                MessageBlock("Liste açılamadı", s.message, icon = AdtIcons.Offline, action = "Tekrar dene", onAction = { reload++ })
            }
            is Load.Ok -> {
                val d = s.data
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Cover(d.thumbnail, Modifier.size(220.dp))
                        Spacer(Modifier.height(20.dp))
                        Txt(if (kind == CollectionKind.ALBUM) "ALBÜM" else "ÇALMA LİSTESİ", AdtTheme.type.label, color = c.accent)
                        Spacer(Modifier.height(6.dp))
                        Txt(d.title, AdtTheme.type.display, maxLines = 3)
                        Spacer(Modifier.height(6.dp))
                        val total = d.songs.sumOf { it.durationSec.coerceAtLeast(0) }
                        Txt(
                            listOfNotNull(
                                d.subtitle.ifBlank { null },
                                "${d.songs.size} şarkı",
                                formatDuration(total).ifBlank { null },
                            ).joinToString(" · "),
                            AdtTheme.type.body, color = c.muted,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    PlayButtons(d.songs) {
                        DownloadButton(aggregateDownload(d.songs, downloads), {
                            app.downloads.downloadAll(d.songs)
                            app.toast("${d.songs.size} şarkı indirme sırasına alındı")
                        })
                        IconBtn(AdtIcons.PlaylistAdd, "Kütüphaneye kaydet", {
                            scope.launch {
                                val id = app.dao.createPlaylistWith(d.title, d.songs, d.url)
                                app.toast("Kütüphaneye kaydedildi")
                                ui.navigate(Routes.playlist(id))
                            }
                        })
                        IconBtn(AdtIcons.Share, "Paylaş", {
                            shareText(app.context, d.title, d.url.replace("www.youtube.com", "music.youtube.com"))
                        })
                    }
                    Spacer(Modifier.height(14.dp))
                    Line()
                }
                songList(
                    d.songs, player.current?.id, player.isPlaying, liked, downloads,
                    numbered = kind == CollectionKind.ALBUM,
                    onPlay = { app.player.play(d.songs, it) },
                    onMore = { ui.show(SheetSpec.SongOptions(it)) },
                )
                if (kind == CollectionKind.ALBUM) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        val artist = d.subtitle.removeSuffix(" - Topic").ifBlank { d.songs.firstOrNull()?.artist.orEmpty() }
                        AlbumBio(d.title.removePrefix("Album - ").removePrefix("Albüm - "), artist)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

@Composable
fun ArtistScreen(name: String, thumb: String?) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    var reload by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    val state by produceState<Load<ArtistDetail>>(Load.Loading, name, reload) {
        value = Load.Loading
        value = runCatching { app.youtube.artist(name, thumb) }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "Yüklenemedi") })
    }
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = remember(likedIds) { likedIds.toSet() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconBtn(AdtIcons.Back, "Geri", { ui.back() })
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                val art = (state as? Load.Ok<ArtistDetail>)?.data?.let { it.thumbnail ?: it.songs.firstOrNull()?.thumbnail } ?: thumb
                Cover(art, Modifier.size(150.dp), shape = CircleShape)
                Spacer(Modifier.height(18.dp))
                Txt("SANATÇI", AdtTheme.type.label, color = c.accent)
                Spacer(Modifier.height(4.dp))
                Txt(name, AdtTheme.type.hero, maxLines = 3)
                Spacer(Modifier.height(14.dp))
                FollowButton(name, art)
            }
            Spacer(Modifier.height(18.dp))
        }
        when (val s = state) {
            Load.Loading -> item { LoadingBlock() }
            is Load.Err -> item {
                MessageBlock("Sanatçı yüklenemedi", s.message, icon = AdtIcons.Offline, action = "Tekrar dene", onAction = { reload++ })
            }
            is Load.Ok -> {
                val d = s.data
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Btn("Radyo", { d.songs.firstOrNull()?.let { app.player.playRadio(it) } }, icon = AdtIcons.Radio, style = BtnStyle.ACCENT)
                        Btn("Karıştır", { app.player.play(d.songs, shuffle = true) }, icon = AdtIcons.Shuffle, style = BtnStyle.OUTLINE)
                    }
                    Spacer(Modifier.height(20.dp))
                    ArtistBio(name)
                    Spacer(Modifier.height(28.dp))
                    SectionTitle(
                        "Popüler",
                        action = if (d.songs.size > 6) (if (showAll) "Daha az" else "Tümü") else null,
                        onAction = { showAll = !showAll },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val shown = if (showAll) d.songs else d.songs.take(6)
                songList(
                    shown, player.current?.id, player.isPlaying, liked, downloads, numbered = true,
                    onPlay = { app.player.play(d.songs, it) },
                    onMore = { ui.show(SheetSpec.SongOptions(it)) },
                )
                if (d.albums.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(28.dp))
                        SectionTitle("Albümler ve tekliler")
                        Spacer(Modifier.height(14.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(d.albums.distinctBy { it.url }, key = { it.url }) { ref ->
                                CollectionCard(ref, { ui.navigate(Routes.collection(ref.url, ref.kind)) })
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

/** Ruh hali / tür sayfası (ana sayfadaki kutucuklar). */
@Composable
fun MoodScreen(label: String, query: String) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<Load<Pair<List<Song>, List<CollectionRef>>>>(Load.Loading, query, reload) {
        value = Load.Loading
        value = runCatching {
            if (query == TRENDING_QUERY) {
                app.youtube.trending() to emptyList()
            } else {
                app.youtube.searchSongs(query, 40) to
                    app.youtube.searchCollections(query, SearchFilter.PLAYLISTS).take(10)
            }
        }.fold({ Load.Ok(it) }, { Load.Err(it.message ?: "Yüklenemedi") })
    }
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = remember(likedIds) { likedIds.toSet() }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader(label, onBack = { ui.back() }) }
        when (val s = state) {
            Load.Loading -> item { LoadingBlock() }
            is Load.Err -> item {
                MessageBlock("Yüklenemedi", s.message, icon = AdtIcons.Offline, action = "Tekrar dene", onAction = { reload++ })
            }
            is Load.Ok -> {
                val (songs, lists) = s.data
                item {
                    PlayButtons(songs)
                    Spacer(Modifier.height(10.dp))
                }
                if (lists.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(14.dp))
                        SectionTitle("Listeler")
                        Spacer(Modifier.height(14.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(lists.distinctBy { it.url }, key = { it.url }) { ref ->
                                CollectionCard(ref, { ui.navigate(Routes.collection(ref.url, ref.kind)) })
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Şarkılar")
                        Spacer(Modifier.height(8.dp))
                    }
                }
                songList(
                    songs, player.current?.id, player.isPlaying, liked, downloads,
                    numbered = query == TRENDING_QUERY,
                    onPlay = { app.player.play(songs, it) },
                    onMore = { ui.show(SheetSpec.SongOptions(it)) },
                )
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

/** Kullanıcının kendi çalma listesi. */
@Composable
fun LocalPlaylistScreen(id: Long) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val playlist by remember(id) { app.dao.playlist(id) }.collectAsState(initial = null)
    val entities by remember(id) { app.dao.playlistSongs(id) }.collectAsState(initial = emptyList())
    val songs = remember(entities) { entities.map { it.toSong() } }
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = remember(likedIds) { likedIds.toSet() }
    val name = playlist?.name ?: ""
    var query by rememberSaveable(id) { mutableStateOf("") }
    val words = remember(query) { queryWords(query) }
    val shownSongs = remember(songs, words) { if (words.isEmpty()) songs else songs.filter { it.matchesQuery(words) } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(AdtIcons.Back, "Geri", { ui.back() })
                Spacer(Modifier.weight(1f))
                IconBtn(AdtIcons.Palette, "Kapak oluştur", {
                    ui.show(
                        SheetSpec.PlaylistCover(
                            id, name, songs.size, songs.map { it.thumbnail }.take(12),
                            hasCustom = playlist?.coverPath != null,
                        )
                    )
                })
                IconBtn(AdtIcons.Edit, "Düzenle", {
                    ui.show(SheetSpec.EditPlaylist(id, name, playlist?.description.orEmpty()))
                })
                IconBtn(AdtIcons.Trash, "Listeyi sil", {
                    ui.show(SheetSpec.Confirm("Liste silinsin mi?", "\"$name\" kalıcı olarak silinecek. Şarkılar beğenilerinde ve indirmelerinde kalır.", "Sil") {
                        scope.launch { app.dao.deletePlaylist(id) }
                        ui.back()
                    })
                })
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                val customCover = playlist?.coverPath
                val coverModifier = Modifier.size(200.dp).press(label = "Kapak oluştur") {
                    ui.show(
                        SheetSpec.PlaylistCover(
                            id, name, songs.size, songs.map { it.thumbnail }.take(12),
                            hasCustom = customCover != null,
                        )
                    )
                }
                if (customCover != null) Cover(customCover, coverModifier)
                else MosaicCover(songs.map { it.thumbnail }, coverModifier)
                Spacer(Modifier.height(18.dp))
                Txt("ÇALMA LİSTEN", AdtTheme.type.label, color = c.accent)
                Spacer(Modifier.height(6.dp))
                Txt(name, AdtTheme.type.display, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                val description = playlist?.description.orEmpty()
                if (description.isNotBlank()) {
                    Txt(
                        description,
                        AdtTheme.type.body,
                        color = c.ink,
                        modifier = Modifier.press(label = "Açıklamayı düzenle") {
                            ui.show(SheetSpec.EditPlaylist(id, name, description))
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (playlist != null) {
                    Txt(
                        "+ AÇIKLAMA EKLE",
                        AdtTheme.type.label,
                        color = c.faint,
                        modifier = Modifier
                            .press(label = "Açıklama ekle") { ui.show(SheetSpec.EditPlaylist(id, name, "")) }
                            .padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                val total = songs.sumOf { it.durationSec.coerceAtLeast(0) }
                Txt(
                    listOfNotNull("${songs.size} şarkı", formatDuration(total).ifBlank { null }).joinToString(" · "),
                    AdtTheme.type.body, color = c.muted,
                )
            }
            Spacer(Modifier.height(18.dp))
            if (songs.isNotEmpty()) {
                PlayButtons(songs) {
                    DownloadButton(aggregateDownload(songs, downloads), {
                        app.downloads.downloadAll(songs)
                        app.toast("İndirme sırasına alındı")
                    })
                    IconBtn(AdtIcons.Queue, "Sıraya ekle", { app.player.addToQueue(songs) })
                }
                if (songs.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    ListFilterField(query, { query = it }, Modifier.padding(horizontal = 20.dp))
                }
                Spacer(Modifier.height(14.dp))
                Line()
            }
        }
        if (songs.isEmpty() && playlist != null) {
            item {
                MessageBlock(
                    "Liste boş",
                    "Bir şarkının yanındaki üç noktaya dokun ve \"Çalma listesine ekle\"yi seç.",
                    icon = AdtIcons.PlaylistAdd,
                    action = "Şarkı ara",
                    onAction = { ui.navigate(Routes.SEARCH) },
                )
            }
        }
        if (songs.isNotEmpty() && shownSongs.isEmpty()) {
            item { MessageBlock("Sonuç yok", "\"$query\" bu listede bulunamadı.", icon = AdtIcons.Search) }
        }
        songList(
            shownSongs, player.current?.id, player.isPlaying, liked, downloads, numbered = words.isEmpty(),
            onPlay = { app.player.play(shownSongs, it) },
            onMore = { ui.show(SheetSpec.SongOptions(it, playlistId = id)) },
        )
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

/** Dört kapaktan oluşan kare liste görseli. */
@Composable
fun MosaicCover(urls: List<String?>, modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    val distinct = urls.filterNotNull().distinct()
    if (distinct.size < 4) {
        Box(modifier.background(c.ink), contentAlignment = Alignment.Center) {
            if (distinct.isNotEmpty()) Cover(distinct.first(), Modifier.fillMaxSize())
            else Txt("♪", AdtTheme.type.hero, color = c.accent)
        }
        return
    }
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Cover(distinct[0], Modifier.weight(1f).aspectRatio(1f))
            Cover(distinct[1], Modifier.weight(1f).aspectRatio(1f))
        }
        Row(Modifier.weight(1f)) {
            Cover(distinct[2], Modifier.weight(1f).aspectRatio(1f))
            Cover(distinct[3], Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

/** Bir listenin toplam indirme durumu: hepsi indiyse tik, iniyorsa ortalama ilerleme. */
fun aggregateDownload(songs: List<Song>, downloads: Map<String, com.alideniz.adtmusic.playback.DlInfo>): com.alideniz.adtmusic.playback.DlInfo? {
    if (songs.isEmpty()) return null
    val infos = songs.map { downloads[it.id] }
    if (infos.all { it?.state == DlState.DONE }) return com.alideniz.adtmusic.playback.DlInfo(DlState.DONE, 1f)
    val active = infos.any { it?.state == DlState.DOWNLOADING || it?.state == DlState.QUEUED }
    if (!active) return null
    val progress = infos.sumOf { (if (it?.state == DlState.DONE) 1f else it?.progress ?: 0f).toDouble() } / songs.size
    return com.alideniz.adtmusic.playback.DlInfo(DlState.DOWNLOADING, progress.toFloat())
}

/** Sanatçıyı takip et / takibi bırak. Takip ettiklerin ana sayfada "Takip ettiklerinden" rafında görünür. */
@Composable
fun FollowButton(name: String, thumbnail: String?) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val followed by app.followed.collectAsState()
    val isFollowing = name in followed
    androidx.compose.animation.AnimatedContent(
        targetState = isFollowing,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.6f), initialScale = 0.9f)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
        },
        label = "follow",
    ) { following ->
        Btn(
            if (following) "Takip ediliyor" else "Takip et",
            {
                scope.launch {
                    if (following) {
                        app.dao.unfollow(name)
                    } else {
                        app.dao.follow(com.alideniz.adtmusic.data.db.FollowedArtistEntity(name, thumbnail))
                        app.toast("$name takip ediliyor")
                    }
                }
            },
            icon = if (following) AdtIcons.Following else AdtIcons.FollowAdd,
            style = if (following) BtnStyle.OUTLINE else BtnStyle.INK,
            height = 42.dp,
        )
    }
}
