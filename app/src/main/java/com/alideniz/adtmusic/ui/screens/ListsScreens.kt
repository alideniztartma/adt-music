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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.ListDef
import com.alideniz.adtmusic.data.Mixes
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.YouTubeRepository
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.SectionTitle
import com.alideniz.adtmusic.ui.components.TextInput
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------------ vitrin kartları

@Composable
private fun ListTile(def: ListDef, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val onColor = if (def.color == Color(0xFFFFB400)) Color(0xFF111111) else Color.White
    Column(
        Modifier
            .size(width = 160.dp, height = 176.dp)
            .clip(AdtTheme.shape)
            .background(def.color)
            .press(label = def.title, onClick = onClick)
            .padding(14.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(onColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            AdtIcon(icon, null, tint = onColor, size = 20.dp)
        }
        Spacer(Modifier.weight(1f))
        Txt(def.title, AdtTheme.type.title, color = onColor, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Txt(def.subtitle, AdtTheme.type.caption, color = onColor.copy(alpha = 0.8f), maxLines = 2)
    }
}

/** Ana sayfa rafı: kişisel karışımlar. */
@Composable
fun MixShelf() {
    val ui = LocalAppUi.current
    Column {
        SectionTitle("Senin için karışımlar")
        Spacer(Modifier.height(14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Mixes.MIXES) { def -> ListTile(def, AdtIcons.Mix) { ui.navigate(Routes.mix(def.kind)) } }
        }
    }
}

/** Kütüphane rafı: akıllı listeler. */
@Composable
fun SmartShelf() {
    val ui = LocalAppUi.current
    Column {
        SectionTitle("Akıllı listeler")
        Txt(
            "Dinledikçe kendiliğinden güncellenir.", AdtTheme.type.caption, color = AdtTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Mixes.SMART) { def -> ListTile(def, AdtIcons.Stats) { ui.navigate(Routes.smart(def.kind)) } }
        }
    }
}

// ------------------------------------------------------------------ karışım / akıllı liste ekranı

@Composable
fun MixScreen(kind: String, smart: Boolean) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val def = Mixes.def(kind)
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<Load<List<Song>>>(Load.Loading, kind, reload) {
        value = Load.Loading
        value = runCatching {
            if (smart) app.mixes.smart(kind) else app.mixes.mix(kind, refresh = reload > 0)
        }.fold({ Load.Ok(it) }, { Load.Err(it.message ?: "") })
    }
    when (val s = state) {
        Load.Loading -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(def?.title ?: "", def?.subtitle, onBack = { ui.back() })
            LoadingBlock()
        }
        is Load.Err -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(def?.title ?: "", def?.subtitle, onBack = { ui.back() })
            MessageBlock("Hazırlanamadı", "İnternet bağlantını kontrol et.", icon = AdtIcons.Offline, action = "Tekrar dene", onAction = { reload++ })
        }
        is Load.Ok -> SongListScreen(
            title = def?.title ?: "",
            subtitle = "${def?.subtitle ?: ""} · ${s.data.size} şarkı",
            songs = s.data,
            emptyTitle = "Henüz yeterli veri yok",
            emptyMessage = "Biraz daha müzik dinle; bu liste dinlediklerine göre oluşur.",
            emptyIcon = if (smart) AdtIcons.Stats else AdtIcons.Mix,
            extra = {
                if (!smart) IconBtn(AdtIcons.Refresh, "Yeniden karıştır", { reload++ })
            },
        )
    }
}

// ------------------------------------------------------------------ içe aktarma

private data class ImportState(
    val running: Boolean = false,
    val title: String = "",
    val total: Int = 0,
    val done: Int = 0,
    val found: Int = 0,
    val error: String? = null,
)

@Composable
fun ImportScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var st by remember { mutableStateOf(ImportState()) }

    fun start() {
        val url = link.trim()
        if (url.isEmpty() || st.running) return
        st = ImportState(running = true)
        scope.launch {
            runCatching {
                when {
                    url.contains("spotify.com") -> {
                        val (name, tracks) = withContext(Dispatchers.IO) { fetchSpotify(app.httpClient, url) }
                        if (tracks.isEmpty()) error("Listede şarkı bulunamadı. Liste herkese açık mı?")
                        st = st.copy(title = name, total = tracks.size)
                        val sem = Semaphore(6)
                        val matched = coroutineScope {
                            tracks.map { (title, artist) ->
                                async {
                                    sem.withPermit {
                                        val song = runCatching { app.youtube.searchSongs("$title $artist", 3).firstOrNull() }.getOrNull()
                                        st = st.copy(done = st.done + 1, found = st.found + if (song != null) 1 else 0)
                                        song
                                    }
                                }
                            }.awaitAll()
                        }.filterNotNull().distinctBy { it.id }
                        app.dao.createPlaylistWith(name.ifBlank { "Spotify listesi" }, matched, url)
                    }
                    YouTubeRepository.playlistIdOf(url) != null -> {
                        val list = YouTubeRepository.playlistIdOf(url)
                        val d = app.youtube.collection("https://www.youtube.com/playlist?list=$list")
                        st = st.copy(title = d.title, total = d.songs.size, done = d.songs.size, found = d.songs.size)
                        app.dao.createPlaylistWith(d.title.ifBlank { "YouTube listesi" }, d.songs, url)
                    }
                    else -> error("Spotify ya da YouTube çalma listesi bağlantısı yapıştır.")
                }
            }.onSuccess { id ->
                st = st.copy(running = false)
                app.toast("Liste kütüphanene eklendi")
                ui.navigate(Routes.playlist(id))
            }.onFailure { e ->
                st = st.copy(running = false, error = e.message ?: "İçe aktarılamadı")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Liste aktar", "Spotify ya da YouTube listeni kütüphanene taşı", onBack = { ui.back() })
        Column(Modifier.padding(horizontal = 20.dp)) {
            TextInput(
                value = link,
                onValueChange = { link = it },
                placeholder = "Liste bağlantısını yapıştır",
                style = AdtTheme.type.bodyStrong,
                onSubmit = { start() },
                leading = { AdtIcon(AdtIcons.Link, null, size = 20.dp) },
            )
            Spacer(Modifier.height(14.dp))
            Btn(if (st.running) "Aktarılıyor…" else "İçe aktar", { start() }, Modifier.fillMaxWidth(), icon = AdtIcons.Import, style = BtnStyle.ACCENT)
            Spacer(Modifier.height(20.dp))
            if (st.running || st.total > 0) {
                Txt(st.title.ifBlank { "Liste okunuyor…" }, AdtTheme.type.title)
                Spacer(Modifier.height(8.dp))
                val f = if (st.total > 0) st.done.toFloat() / st.total else 0f
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.line)) {
                    Box(Modifier.fillMaxWidth(f).height(6.dp).background(c.accent))
                }
                Spacer(Modifier.height(6.dp))
                Txt("${st.done} / ${st.total} şarkı tarandı · ${st.found} eşleşti", AdtTheme.type.caption, color = c.muted)
            }
            st.error?.let {
                Spacer(Modifier.height(12.dp))
                Txt(it, AdtTheme.type.bodyStrong, color = c.accent)
            }
            Spacer(Modifier.height(24.dp))
            Line()
            Spacer(Modifier.height(12.dp))
            Txt("NASIL ÇALIŞIR?", AdtTheme.type.label, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Txt(
                "• Spotify: Listede ⋯ → Paylaş → Bağlantıyı kopyala. Liste herkese açık olmalı. Her şarkı YouTube Music'te aranıp eşleştirilir (en fazla 100 şarkı).\n" +
                    "• YouTube / YouTube Music: Listenin bağlantısını yapıştır, şarkılar doğrudan aktarılır.\n" +
                    "• Hesap bilgisi istenmez; hiçbir veri dışarı gönderilmez.",
                AdtTheme.type.body, color = c.muted,
            )
        }
        Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
    }
}

/**
 * Spotify'ın herkese açık "embed" sayfasındaki gömülü veriden liste adını ve
 * (şarkı adı, sanatçı) çiftlerini okur. Hesap ya da API anahtarı gerekmez.
 */
private fun fetchSpotify(client: okhttp3.OkHttpClient, url: String): Pair<String, List<Pair<String, String>>> {
    val m = Regex("(playlist|album)/([A-Za-z0-9]+)").find(url) ?: error("Spotify bağlantısı tanınmadı")
    val embed = "https://open.spotify.com/embed/${m.groupValues[1]}/${m.groupValues[2]}"
    val html = client.newCall(
        Request.Builder().url(embed).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Gecko/20100101 Firefox/128.0").build()
    ).execute().use { it.body?.string().orEmpty() }
    val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1) ?: error("Spotify sayfası okunamadı")
    val root = JSONObject(json)
    var name = ""
    val tracks = mutableListOf<Pair<String, String>>()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> {
                if (name.isEmpty() && node.has("trackList") && node.has("name")) name = node.optString("name")
                if (name.isEmpty() && node.has("trackList") && node.has("title")) name = node.optString("title")
                val list = node.optJSONArray("trackList")
                if (list != null && tracks.isEmpty()) {
                    for (i in 0 until list.length()) {
                        val t = list.optJSONObject(i) ?: continue
                        val title = t.optString("title")
                        val artist = t.optString("subtitle")
                        if (title.isNotBlank()) tracks += title to artist
                    }
                }
                node.keys().forEach { walk(node.opt(it)) }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
        }
    }
    walk(root)
    return name to tracks.take(100)
}
