package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.DownloadButton
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.ListFilterField
import com.alideniz.adtmusic.ui.components.matchesQuery
import com.alideniz.adtmusic.ui.components.queryWords
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.SongRow
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatBytes
import com.alideniz.adtmusic.util.formatListenTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val liked by remember { app.dao.likedSongs() }.collectAsState(initial = emptyList())
    val playlists by remember { app.dao.playlists() }.collectAsState(initial = emptyList())
    val downloads by app.downloads.states.collectAsState()
    val totalPlays by remember { app.dao.totalPlays() }.collectAsState(initial = 0)
    val doneCount = downloads.count { it.value.state == DlState.DONE }
    val followed by app.followed.collectAsState()
    val momentCount by remember { app.dao.momentCount() }.collectAsState(initial = 0)
    val incognito by app.settings.pauseHistory.flow.collectAsState()

    fun newPlaylist() = ui.show(
        SheetSpec.Input("Yeni çalma listesi", "", "Oluştur") { name ->
            scope.launch {
                val id = app.dao.createPlaylistWith(name.ifBlank { "Yeni liste" }, emptyList(), null)
                ui.navigate(Routes.playlist(id))
            }
        }
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("Kütüphane", AdtTheme.type.display, modifier = Modifier.weight(1f))
                IconBtn(
                    AdtIcons.Incognito, if (incognito) "Gizli modu kapat" else "Gizli mod",
                    {
                        app.settings.pauseHistory.set(!incognito)
                        app.toast(if (!incognito) "Gizli mod açık: dinlediklerin kaydedilmez" else "Gizli mod kapandı")
                    },
                    tint = if (incognito) c.accent else c.ink,
                )
                IconBtn(AdtIcons.Settings, "Ayarlar", { ui.navigate(Routes.SETTINGS) })
            }
            if (incognito) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 6.dp).background(c.ink).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdtIcon(AdtIcons.Incognito, null, tint = c.paper, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Txt("GİZLİ MOD AÇIK", AdtTheme.type.label, color = c.paper)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("Çalma listelerim", AdtTheme.type.headline, modifier = Modifier.weight(1f))
                if (playlists.isNotEmpty()) Txt("${playlists.size}", AdtTheme.type.headline, color = c.accent)
                Spacer(Modifier.width(14.dp))
                TextLink("+ Yeni", { newPlaylist() })
            }
            Spacer(Modifier.height(8.dp))
        }
        if (playlists.isEmpty()) {
            item {
                MessageBlock(
                    "Henüz listen yok",
                    "Kendi listelerini oluştur ya da bir albümü kaydet.",
                    icon = AdtIcons.PlaylistAdd,
                    action = "Liste oluştur",
                    onAction = { newPlaylist() },
                )
            }
        }
        items(playlists, key = { it.playlist.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .press(label = p.playlist.name) { ui.navigate(Routes.playlist(p.playlist.id)) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p.cover != null) Cover(p.cover, Modifier.size(60.dp))
                else MosaicCover(emptyList(), Modifier.size(60.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(p.playlist.name, AdtTheme.type.bodyStrong, maxLines = 1)
                    Txt(
                        listOfNotNull("${p.songCount} şarkı", p.playlist.description.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        AdtTheme.type.caption, color = c.muted, maxLines = 1,
                    )
                }
                AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 18.dp)
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Line(color = c.ink)
            LibraryRow(AdtIcons.Heart, "Beğenilenler", liked.size.toString()) { ui.navigate(Routes.LIKED) }
            LibraryRow(AdtIcons.Download, "İndirilenler", doneCount.toString()) { ui.navigate(Routes.DOWNLOADS) }
            LibraryRow(AdtIcons.History, "Geçmiş", totalPlays.toString()) { ui.navigate(Routes.HISTORY) }
            LibraryRow(AdtIcons.Following, "Takip ettiğin sanatçılar", followed.size.toString()) { ui.navigate(Routes.FOLLOWING) }
            LibraryRow(AdtIcons.Moment, "Anıların", momentCount.toString()) { ui.navigate(Routes.MOMENTS) }
            LibraryRow(AdtIcons.Wrapped, "ADT Wrapped", "") { ui.navigate(Routes.WRAPPED) }
            LibraryRow(AdtIcons.Badge, "Rozetler", "") { ui.navigate(Routes.BADGES) }
            LibraryRow(AdtIcons.Cassette, "Mixtape · arkadaşınla takas et", "") { ui.navigate(Routes.TASTE) }
            LibraryRow(AdtIcons.Import, "Spotify / YouTube listesi aktar", "") { ui.navigate(Routes.IMPORT) }
            LibraryRow(AdtIcons.Stats, "Dinleme istatistiklerin", "") { ui.navigate(Routes.STATS) }
            LibraryRow(AdtIcons.Equalizer, "Ekolayzer", "") { ui.navigate(Routes.EQUALIZER) }
            Spacer(Modifier.height(28.dp))
            SmartShelf()
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, label: String, count: String, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .press(label = label, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdtIcon(icon, null, size = 22.dp)
            Spacer(Modifier.width(16.dp))
            Txt(label, AdtTheme.type.title, modifier = Modifier.weight(1f))
            if (count.isNotEmpty()) Txt(count, AdtTheme.type.headline, color = c.accent)
            Spacer(Modifier.width(8.dp))
            AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 18.dp)
        }
        Line()
    }
}

@Composable
fun SongListScreen(
    title: String,
    subtitle: String?,
    songs: List<Song>,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    extra: (@Composable () -> Unit)? = null,
    /** Satırları gruplamak için (ör. geçmişte gün). Grup değişince başlık gösterilir. */
    groupOf: ((Int) -> String)? = null,
) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = remember(likedIds) { likedIds.toSet() }
    var query by rememberSaveable { mutableStateOf("") }
    val words = remember(query) { queryWords(query) }
    // Görünen satırların asıl listedeki sırası
    val shown = remember(songs, words) {
        if (words.isEmpty()) songs.indices.toList()
        else songs.indices.filter { songs[it].matchesQuery(words) }
    }
    val shownSongs = remember(songs, shown) { shown.map { songs[it] } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(title, subtitle, onBack = { ui.back() })
            if (songs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Btn("Çal", { if (shownSongs.isNotEmpty()) app.player.play(shownSongs) }, icon = AdtIcons.Play, style = BtnStyle.ACCENT)
                    Btn("Karıştır", { if (shownSongs.isNotEmpty()) app.player.play(shownSongs, shuffle = true) }, icon = AdtIcons.Shuffle, style = BtnStyle.OUTLINE)
                    Spacer(Modifier.weight(1f))
                    extra?.invoke()
                }
                if (songs.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    ListFilterField(query, { query = it }, Modifier.padding(horizontal = 20.dp))
                }
                Spacer(Modifier.height(14.dp))
                Line()
            }
        }
        if (songs.isEmpty()) {
            item { MessageBlock(emptyTitle, emptyMessage, icon = emptyIcon) }
        } else if (shown.isEmpty()) {
            item { MessageBlock("Sonuç yok", "\"$query\" bu listede bulunamadı.", icon = AdtIcons.Search) }
        }
        itemsIndexed(shownSongs, key = { j, s -> "${shown[j]}-${s.id}" }) { j, s ->
            Column {
                groupOf?.let { g ->
                    val header = g(shown[j])
                    if (j == 0 || g(shown[j - 1]) != header) {
                        Txt(
                            header.uppercase(), AdtTheme.type.label, color = c.muted,
                            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                }
                SongRow(
                    song = s,
                    // Arama yokken tüm listeyi, varsa yalnızca bulunanları sıraya koy
                    onClick = { app.player.play(shownSongs, j) },
                    onMore = { ui.show(SheetSpec.SongOptions(s)) },
                    isCurrent = player.current?.id == s.id,
                    isPlaying = player.isPlaying,
                    liked = s.id in liked,
                    download = downloads[s.id],
                )
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

@Composable
fun LikedScreen() {
    val app = LocalContext.current.container
    val liked by remember { app.dao.likedSongs() }.collectAsState(initial = emptyList())
    val songs = remember(liked) { liked.map { it.toSong() } }
    SongListScreen(
        title = "Beğenilenler",
        subtitle = "${songs.size} şarkı",
        songs = songs,
        emptyTitle = "Henüz beğeni yok",
        emptyMessage = "Çalan şarkıda kalbe dokun, burada birikir.",
        emptyIcon = AdtIcons.Heart,
        extra = {
            val downloads by app.downloads.states.collectAsState()
            DownloadButton(aggregateDownload(songs, downloads), {
                app.downloads.downloadAll(songs)
                app.toast("Beğenilenler indiriliyor")
            })
        },
    )
}

@Composable
fun DownloadsScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val states by app.downloads.states.collectAsState()
    val ids = remember(states) { states.keys.toList() }
    val entities by remember(ids) { app.dao.songsByIdsFlow(ids) }.collectAsState(initial = emptyList())
    val songs = remember(entities, states) {
        val order = states.entries.sortedBy { if (it.value.state == DlState.DONE) 1 else 0 }.map { it.key }
        val map = entities.associateBy { it.id }
        order.mapNotNull { map[it]?.toSong() }
    }
    val done = states.count { it.value.state == DlState.DONE }
    val active = states.count { it.value.state == DlState.DOWNLOADING || it.value.state == DlState.QUEUED }
    SongListScreen(
        title = "İndirilenler",
        subtitle = buildString {
            append("$done şarkı · ${formatBytes(app.media.downloadCacheBytes())}")
            if (active > 0) append(" · $active indiriliyor")
        },
        songs = songs,
        emptyTitle = "İndirme yok",
        emptyMessage = "Bir şarkının menüsünden \"İndir\"i seç; internetsiz de dinlersin.",
        emptyIcon = AdtIcons.Download,
        extra = {
            IconBtn(AdtIcons.Trash, "Tümünü sil", {
                ui.show(SheetSpec.Confirm("İndirmeler silinsin mi?", "İndirilen tüm şarkılar cihazdan kaldırılacak.", "Tümünü sil") {
                    app.downloads.removeAll()
                })
            })
        },
    )
}

@Composable
fun HistoryScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val rows by remember { app.dao.history() }.collectAsState(initial = emptyList())
    val paused by app.settings.pauseHistory.flow.collectAsState()
    val songs = remember(rows) { rows.map { it.song.toSong() } }
    val dayFormat = remember { SimpleDateFormat("d MMMM EEEE", Locale("tr")) }
    val days = remember(rows) { rows.map { dayLabel(it.playedAt, dayFormat) } }
    SongListScreen(
        title = "Geçmiş",
        subtitle = if (paused) "Geçmiş kaydı duraklatıldı" else "Yalnızca bu cihazda saklanır",
        songs = songs,
        emptyTitle = "Geçmiş boş",
        emptyMessage = "Dinlediğin şarkılar burada görünür.",
        emptyIcon = AdtIcons.History,
        extra = {
            IconBtn(AdtIcons.Trash, "Geçmişi temizle", {
                ui.show(SheetSpec.Confirm("Geçmiş temizlensin mi?", "Dinleme geçmişin ve istatistiklerin silinecek.", "Temizle") {
                    scope.launch { app.dao.clearHistory() }
                })
            })
        },
        groupOf = { i -> days[i] },
    )
}

private fun dayLabel(at: Long, format: SimpleDateFormat): String {
    // Gün sınırı yerel saate göre (UTC'ye göre gece 03:00'te değişmesin)
    val dayMs = 24 * 3600_000L
    val tz = java.util.TimeZone.getDefault()
    val now = System.currentTimeMillis()
    val today = (now + tz.getOffset(now)) / dayMs
    val d = (at + tz.getOffset(at)) / dayMs
    return when (today - d) {
        0L -> "Bugün"
        1L -> "Dün"
        else -> format.format(Date(at))
    }
}

@Composable
fun StatsScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val plays by remember { app.dao.totalPlays() }.collectAsState(initial = 0)
    val listenMs by remember { app.dao.totalListenMs() }.collectAsState(initial = 0L)
    val distinct by remember { app.dao.distinctSongs() }.collectAsState(initial = 0)
    val topSongs by remember { app.dao.topSongs(10) }.collectAsState(initial = emptyList())
    val topArtists by remember { app.dao.topArtists(8) }.collectAsState(initial = emptyList())
    val songs = remember(topSongs) { topSongs.map { it.toSong() } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("İstatistikler", "Tamamen cihazında hesaplanır", onBack = { ui.back() })
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigNumber(formatListenTime(listenMs), "dinleme", Modifier.weight(1f))
                BigNumber(plays.toString(), "çalma", Modifier.weight(1f))
                BigNumber(distinct.toString(), "farklı şarkı", Modifier.weight(1f))
            }
            Spacer(Modifier.height(32.dp))
        }
        if (topArtists.isNotEmpty()) {
            item {
                Txt("En çok dinlediğin sanatçılar", AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
            }
            val max = topArtists.maxOf { it.plays }.coerceAtLeast(1)
            itemsIndexed(topArtists, key = { _, a -> "a-${a.artist}" }) { i, a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .press(label = a.artist) { ui.navigate(Routes.artist(a.artist, null)) }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("%02d".format(i + 1), AdtTheme.type.caption, color = c.faint, modifier = Modifier.width(30.dp))
                        Txt(a.artist, AdtTheme.type.bodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
                        Txt("${a.plays}", AdtTheme.type.caption, color = c.muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.padding(start = 30.dp).fillMaxWidth().height(6.dp).background(c.surface)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(a.plays.toFloat() / max).background(if (i == 0) c.accent else c.ink))
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
        if (songs.isNotEmpty()) {
            item {
                Txt("En çok dinlediğin şarkılar", AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(songs, key = { _, s -> "ts-${s.id}" }) { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(
                            song = s,
                            onClick = { app.player.play(songs, i) },
                            onMore = { ui.show(SheetSpec.SongOptions(s)) },
                            index = i + 1,
                        )
                    }
                }
            }
        } else {
            item { MessageBlock("Henüz veri yok", "Biraz müzik dinle, istatistiklerin burada oluşsun.", icon = AdtIcons.Stats) }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    Column(modifier.background(c.surface).padding(14.dp)) {
        Txt(value, AdtTheme.type.title, maxLines = 1)
        Txt(label.uppercase(), AdtTheme.type.label, color = c.muted, maxLines = 1)
    }
}

@Composable
fun FollowingScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val artists by remember { app.dao.followedArtists() }.collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale("tr")) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("Sanatçıların", "${artists.size} sanatçı takip ediyorsun", onBack = { ui.back() }) }
        if (artists.isEmpty()) {
            item {
                MessageBlock(
                    "Henüz kimseyi takip etmiyorsun",
                    "Bir sanatçının sayfasında \"Takip et\"e dokun. Yeni şarkıları ana sayfanda görünür.",
                    icon = AdtIcons.FollowAdd,
                    action = "Sanatçı ara",
                    onAction = { ui.navigate(Routes.SEARCH) },
                )
            }
        }
        items(artists, key = { it.name }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .press(label = a.name) { ui.navigate(Routes.artist(a.name, a.thumbnail)) }
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(a.thumbnail, Modifier.size(60.dp), shape = androidx.compose.foundation.shape.CircleShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(a.name, AdtTheme.type.bodyStrong, maxLines = 1)
                    Txt("${dateFormat.format(Date(a.followedAt))} tarihinden beri", AdtTheme.type.caption, color = c.muted, maxLines = 1)
                }
                IconBtn(AdtIcons.Close, "Takibi bırak", {
                    scope.launch { app.dao.unfollow(a.name) }
                    app.toast("${a.name} takipten çıkarıldı")
                }, tint = c.faint, iconSize = 18.dp)
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}
