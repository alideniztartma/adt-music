package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alideniz.adtmusic.AppContainer
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.HomeFeed
import com.alideniz.adtmusic.data.Mood
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.CollectionCard
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.SectionTitle
import com.alideniz.adtmusic.ui.components.SongCard
import com.alideniz.adtmusic.ui.components.SongRow
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.greeting
import kotlinx.coroutines.launch

val MOODS = listOf(
    Mood("Türkçe Pop", "türkçe pop hit şarkılar"),
    Mood("Rap", "türkçe rap"),
    Mood("Rock", "türkçe rock"),
    Mood("Anadolu Rock", "anadolu rock"),
    Mood("Arabesk", "arabesk"),
    Mood("Lo-fi", "lofi hip hop"),
    Mood("Odak", "focus study music"),
    Mood("Spor", "workout motivation music"),
    Mood("Chill", "chill vibes"),
    Mood("Romantik", "aşk şarkıları"),
    Mood("Parti", "party hits"),
    Mood("Elektronik", "electronic dance music"),
    Mood("Akustik", "akustik cover"),
    Mood("Klasik", "classical music"),
)

class HomeViewModel(private val app: AppContainer) : ViewModel() {
    var feed by mutableStateOf<HomeFeed?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            runCatching {
                val seed = app.dao.lastPlayed()?.toSong()
                app.youtube.home(seed)
            }.onSuccess {
                feed = it
                if (it.trending.isEmpty() && it.quickPicks.isEmpty() && it.featured.isEmpty()) {
                    error = "YouTube Music'e ulaşılamadı."
                }
            }.onFailure {
                error = it.message ?: "Bağlantı hatası"
            }
            loading = false
        }
    }
}

@Composable
fun HomeScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val vm: HomeViewModel = viewModel { HomeViewModel(app) }
    val recent by remember { app.dao.recentSongs(12) }.collectAsState(initial = emptyList())
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val feed = vm.feed

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                IconBtn(AdtIcons.Refresh, "Yenile", { vm.load() })
                IconBtn(AdtIcons.Settings, "Ayarlar", { ui.navigate(Routes.SETTINGS) })
            }
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 6.dp)) {
                Txt("${greeting()}.", AdtTheme.type.display)
            }
            Txt(
                "Bugün ne dinlemek istersin?", AdtTheme.type.body, color = c.muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(18.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MOODS) { mood ->
                    Chip(mood.label, false, { ui.navigate(Routes.mood(mood.label, mood.query)) })
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Spacer(Modifier.height(30.dp))
                SectionTitle("Tekrar dinle", action = "Geçmiş", onAction = { ui.navigate(Routes.HISTORY) })
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val songs = recent.map { it.toSong() }
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongCard(
                            s,
                            onClick = { app.player.play(songs, i) },
                            onLongClick = { ui.show(SheetSpec.SongOptions(s)) },
                            width = 132.dp,
                        )
                    }
                }
            }
        }

        when {
            feed == null && vm.loading -> item { LoadingBlock() }
            feed == null && vm.error != null -> item {
                MessageBlock(
                    "Bağlantı kurulamadı",
                    "İnternetini kontrol et. İndirdiğin şarkılar Kütüphane'de çevrimdışı çalar.",
                    icon = AdtIcons.Offline,
                    action = "Tekrar dene",
                    onAction = { vm.load() },
                )
            }
        }

        if (feed != null) {
            if (feed.quickPicks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp))
                    SectionTitle("Senin için", action = "Hepsini çal", onAction = { app.player.play(feed.quickPicks) })
                    feed.quickPicksSeed?.let {
                        Txt(
                            "“${it.title}” temel alınarak", AdtTheme.type.caption, color = c.muted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp), maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val pages = feed.quickPicks.chunked(4)
                    LazyRow {
                        itemsIndexed(pages) { pageIndex, page ->
                            Column(Modifier.width(330.dp)) {
                                page.forEachIndexed { i, s ->
                                    val index = pageIndex * 4 + i
                                    SongRow(
                                        song = s,
                                        onClick = { app.player.play(feed.quickPicks, index) },
                                        onMore = { ui.show(SheetSpec.SongOptions(s)) },
                                        isCurrent = player.current?.id == s.id,
                                        isPlaying = player.isPlaying,
                                        liked = s.id in likedIds,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (feed.trending.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp))
                    SectionTitle("Trendler", action = "Tümü", onAction = { ui.navigate(Routes.mood("Trendler", TRENDING_QUERY)) })
                    Txt(
                        "Türkiye'de şu an en çok dinlenenler", AdtTheme.type.caption, color = c.muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(feed.trending.take(10), key = { _, s -> "t-${s.id}" }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = { app.player.play(feed.trending, i) },
                        onMore = { ui.show(SheetSpec.SongOptions(s)) },
                        index = i + 1,
                        isCurrent = player.current?.id == s.id,
                        isPlaying = player.isPlaying,
                        liked = s.id in likedIds,
                    )
                }
            }

            if (feed.featured.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp))
                    SectionTitle("Öne çıkan listeler")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(feed.featured, key = { it.url }) { ref ->
                            CollectionCard(ref, { ui.navigate(Routes.collection(ref.url, ref.kind)) })
                        }
                    }
                }
            }

            if (feed.newReleases.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp))
                    SectionTitle("Albümler")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(feed.newReleases, key = { it.url }) { ref ->
                            CollectionCard(ref, { ui.navigate(Routes.collection(ref.url, ref.kind)) })
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(40.dp))
            Txt(
                "Takipçi yok. Analiz yok. Tüm verilerin bu cihazda.",
                AdtTheme.type.caption, color = c.faint,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
        }
    }
}

const val TRENDING_QUERY = "__trending__"

/** "ADT" yazısı ve turuncu daire içinde nota: uygulamanın logosu. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Txt("ADT", AdtTheme.type.headline.copy(fontSize = AdtTheme.type.headline.fontSize * 1.15f))
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(bottom = 3.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                AdtIcon(AdtIcons.Note, null, tint = c.onAccent, size = 8.dp)
            }
            Spacer(Modifier.height(2.dp))
            Txt("MUSIC", AdtTheme.type.label.copy(fontSize = AdtTheme.type.label.fontSize * 0.8f))
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = AdtTheme.colors
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconBtn(AdtIcons.Back, "Geri", onBack) else Spacer(Modifier.width(12.dp))
            Spacer(Modifier.weight(1f))
            actions()
        }
        Txt(title, AdtTheme.type.display, modifier = Modifier.padding(horizontal = 20.dp), maxLines = 2)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Txt(subtitle, AdtTheme.type.body, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp), maxLines = 2)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Basılabilir metin bağlantısı. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), AdtTheme.type.label, modifier = modifier.press(label = text, onClick = onClick).padding(8.dp))
}
