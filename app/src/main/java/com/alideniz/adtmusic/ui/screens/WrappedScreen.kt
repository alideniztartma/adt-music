package com.alideniz.adtmusic.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.db.HistoryRow
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

enum class WrappedPeriod(val label: String) { MONTH("Bu ay"), YEAR("Bu yıl"), ALL("Tüm zamanlar") }

data class WrappedData(
    val period: WrappedPeriod,
    val minutes: Long,
    val plays: Int,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<String, Int>>,
    val topArtistThumb: String?,
    val peakHour: Int,
    val personality: String,
    val personalityText: String,
    val favoriteDay: String,
)

private val DAYS = listOf("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")

fun computeWrapped(rows: List<HistoryRow>, period: WrappedPeriod): WrappedData? {
    // Yalnızca gerçekten dinlenen süre sayılır. Bir çalma, en az 30 sn dinlendiyse
    // (ya da süresi bilinmeyen eski bir kayıtsa) "dinlendi" kabul edilir.
    val minutes = rows.sumOf { it.listenedMs.coerceAtLeast(0L) } / 60_000L
    @Suppress("NAME_SHADOWING")
    val rows = rows.filter { it.listenedMs < 0 || it.listenedMs >= 30_000L }
    if (rows.isEmpty()) return null
    val songs = rows.map { it.song.toSong() }
    val topSongs = rows.groupBy { it.song.id }
        .map { (_, list) -> list.first().song.toSong() to list.size }
        .sortedByDescending { it.second }
        .take(5)
    val topArtists = rows.groupBy { it.song.artist }
        .filterKeys { it.isNotBlank() }
        .map { (artist, list) -> artist to list.size }
        .sortedByDescending { it.second }
        .take(5)
    val cal = Calendar.getInstance()
    val hours = IntArray(24)
    val days = IntArray(7)
    rows.forEach {
        cal.timeInMillis = it.playedAt
        hours[cal.get(Calendar.HOUR_OF_DAY)]++
        days[cal.get(Calendar.DAY_OF_WEEK) - 1]++
    }
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 20
    val distinct = songs.distinctBy { it.id }.size
    val ratio = distinct.toFloat() / rows.size
    val (title, text) = when {
        peak in 0..4 || peak >= 23 -> "Gece Kuşu" to "Şehir uyurken sen kulaklığını takıyorsun. En çok gece ${"%02d".format(peak)}:00 civarı dinliyorsun."
        peak in 5..10 -> "Erken Kalkan" to "Günü müzikle başlatıyorsun. Zirve saatin ${"%02d".format(peak)}:00."
        ratio > 0.7f -> "Kâşif" to "Dinlediklerinin çoğu yeni şarkılar. Keşfetmeyi seviyorsun."
        ratio < 0.35f -> "Sadık Dinleyici" to "Sevdiğin şarkıları tekrar tekrar dinliyorsun. Favorilerin belli."
        peak in 18..22 -> "Akşam Ruhu" to "Günün yorgunluğunu müzikle atıyorsun. Zirve saatin ${"%02d".format(peak)}:00."
        else -> "Gündüz Enerjisi" to "Gün boyu yanında müzik var. Zirve saatin ${"%02d".format(peak)}:00."
    }
    val topArtist = topArtists.firstOrNull()?.first
    return WrappedData(
        period = period,
        minutes = minutes,
        plays = rows.size,
        distinctSongs = distinct,
        distinctArtists = songs.map { it.artist }.distinct().size,
        topSongs = topSongs,
        topArtists = topArtists,
        topArtistThumb = songs.firstOrNull { it.artist == topArtist }?.thumbnail,
        peakHour = peak,
        personality = title,
        personalityText = text,
        favoriteDay = DAYS[days.indices.maxByOrNull { days[it] } ?: 5],
    )
}

private fun sinceOf(period: WrappedPeriod): Long {
    val cal = Calendar.getInstance()
    return when (period) {
        WrappedPeriod.MONTH -> cal.apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        WrappedPeriod.YEAR -> cal.apply {
            set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        WrappedPeriod.ALL -> 0L
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen() {
    val context = LocalContext.current
    val app = context.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(WrappedPeriod.YEAR) }
    val data by produceState<Load<WrappedData?>>(Load.Loading, period) {
        value = Load.Loading
        value = runCatching { computeWrapped(app.dao.historySince(sinceOf(period)), period) }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "") })
    }
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        when (val d = data) {
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBlock() }
            is Load.Err -> MessageBlock("Hazırlanamadı", d.message)
            is Load.Ok -> {
                val w = d.data
                if (w == null) {
                    Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                        MessageBlock(
                            "Henüz yeterli veri yok",
                            "${period.label} için dinleme kaydı yok. Biraz müzik dinle, Wrapped'in burada oluşsun.",
                            icon = AdtIcons.Wrapped,
                        )
                    }
                } else {
                    val pages = 7
                    val pager = rememberPagerState { pages }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val layer = rememberGraphicsLayer()
                        LaunchedEffect(layer) { layers[page] = layer }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    layer.record { this@drawWithContent.drawContent() }
                                    drawLayer(layer)
                                }
                        ) {
                            WrappedSlide(page, w, visible = pager.currentPage == page)
                        }
                    }
                    // ---- üstte hikâye çubukları
                    Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(pages) { i ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(if (i <= pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                    // ---- altta paylaş
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Btn(
                            "Paylaş",
                            {
                                val layer = layers[pager.currentPage] ?: return@Btn
                                scope.launch {
                                    runCatching {
                                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                                        shareBitmap(context, bmp)
                                    }.onFailure { app.toast("Görsel oluşturulamadı") }
                                }
                            },
                            Modifier.weight(1f),
                            icon = AdtIcons.Share,
                            style = BtnStyle.ACCENT,
                        )
                        Btn(
                            if (pager.currentPage < pages - 1) "Sonraki" else "Baştan",
                            {
                                scope.launch {
                                    pager.animateScrollToPage(if (pager.currentPage < pages - 1) pager.currentPage + 1 else 0)
                                }
                            },
                            Modifier.weight(1f),
                            style = BtnStyle.OUTLINE,
                        )
                    }
                }
            }
        }
        // ---- kapat ve dönem seçimi (her zeminde okunsun diye yarı saydam koyu kutular)
        Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
                    IconBtn(AdtIcons.Close, "Kapat", { ui.back() }, tint = Color.White, size = 40.dp, iconSize = 20.dp)
                }
                Spacer(Modifier.width(10.dp))
                WrappedPeriod.entries.forEach { p ->
                    val selected = p == period
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.35f))
                            .press(label = p.label) { period = p }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Txt(p.label, AdtTheme.type.caption, color = if (selected) Color(0xFF111111) else Color.White)
                    }
                }
            }
        }
    }
}

/** Tek bir Wrapped sayfası. Her sayfa paylaşılabilir bir kart olarak tasarlandı. */
@Composable
private fun WrappedSlide(page: Int, w: WrappedData, visible: Boolean) {
    val c = AdtTheme.colors
    val accent = c.accent
    val onAccent = c.onAccent
    val (bg, fg) = when (page % 3) {
        0 -> Color(0xFF111111) to Color(0xFFF4F2EE)
        1 -> accent to onAccent
        else -> Color(0xFFF4F2EE) to Color(0xFF111111)
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            appear.snapTo(0f)
            appear.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    val a = appear.value
    val big = AdtTheme.type.hero.copy(fontSize = 72.sp, lineHeight = 66.sp)

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 120.dp, bottom = 96.dp),
    ) {
        Txt("ADT WRAPPED · ${w.period.label.uppercase()}", AdtTheme.type.label, color = fg.copy(alpha = 0.7f))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.weight(1f).graphicsLayer { alpha = a; translationY = (1f - a) * 60f }) {
            when (page) {
                0 -> Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Txt("Müzikle\ngeçen\nzamanın.", big, color = fg)
                    Spacer(Modifier.height(20.dp))
                    Txt("Kaydır ve gör →", AdtTheme.type.title, color = fg.copy(alpha = 0.7f))
                }
                1 -> Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Txt("Tam olarak", AdtTheme.type.title, color = fg)
                    Txt("%,d".format((w.minutes * a).toLong()).replace(',', '.'), big.copy(fontSize = 92.sp, lineHeight = 88.sp), color = fg)
                    Txt("dakika dinledin.", AdtTheme.type.display, color = fg)
                    Spacer(Modifier.height(24.dp))
                    Txt(
                        "${w.plays} çalma · ${w.distinctSongs} farklı şarkı · ${w.distinctArtists} sanatçı",
                        AdtTheme.type.bodyStrong, color = fg.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Txt(
                        "Yalnızca gerçekten dinlediğin süre sayılır; 30 saniyeden kısa dinlemeler çalma sayılmaz.",
                        AdtTheme.type.caption, color = fg.copy(alpha = 0.6f),
                    )
                }
                2 -> Column(modifier = Modifier.fillMaxSize()) {
                    Txt("En çok dinlediğin şarkı", AdtTheme.type.title, color = fg)
                    Spacer(Modifier.height(18.dp))
                    val top = w.topSongs.first()
                    Cover(top.first.thumbnail, Modifier.fillMaxWidth(0.82f).aspectRatio(1f))
                    Spacer(Modifier.height(20.dp))
                    Txt(top.first.title, AdtTheme.type.display, color = fg, maxLines = 2)
                    Txt(top.first.artist, AdtTheme.type.title, color = fg.copy(alpha = 0.75f), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Txt("${top.second} kez çaldın", AdtTheme.type.bodyStrong, color = fg)
                }
                3 -> Column(modifier = Modifier.fillMaxSize()) {
                    Txt("Zirvedeki 5 şarkın", AdtTheme.type.display, color = fg)
                    Spacer(Modifier.height(22.dp))
                    w.topSongs.forEachIndexed { i, (song, count) ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt("${i + 1}", AdtTheme.type.display, color = fg, modifier = Modifier.width(44.dp))
                            Cover(song.thumbnail, Modifier.size(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(song.title, AdtTheme.type.bodyStrong, color = fg, maxLines = 1)
                                Txt("${song.artist} · $count", AdtTheme.type.caption, color = fg.copy(alpha = 0.7f), maxLines = 1)
                            }
                        }
                    }
                }
                4 -> Column(modifier = Modifier.fillMaxSize()) {
                    Txt("Bir numaralı sanatçın", AdtTheme.type.title, color = fg)
                    Spacer(Modifier.height(18.dp))
                    Cover(w.topArtistThumb, Modifier.size(170.dp), shape = CircleShape)
                    Spacer(Modifier.height(18.dp))
                    Txt(w.topArtists.firstOrNull()?.first ?: "", big.copy(fontSize = 54.sp, lineHeight = 52.sp), color = fg, maxLines = 2)
                    Spacer(Modifier.height(20.dp))
                    w.topArtists.drop(1).forEachIndexed { i, (artist, count) ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Txt("${i + 2}", AdtTheme.type.title, color = fg.copy(alpha = 0.6f), modifier = Modifier.width(32.dp))
                            Txt(artist, AdtTheme.type.title, color = fg, modifier = Modifier.weight(1f), maxLines = 1)
                            Txt("$count", AdtTheme.type.title, color = fg.copy(alpha = 0.6f))
                        }
                    }
                }
                5 -> Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Txt("Dinleme kişiliğin", AdtTheme.type.title, color = fg)
                    Spacer(Modifier.height(10.dp))
                    Txt(w.personality, big, color = fg)
                    Spacer(Modifier.height(18.dp))
                    Txt(w.personalityText, AdtTheme.type.title, color = fg.copy(alpha = 0.85f))
                    Spacer(Modifier.height(18.dp))
                    Txt("En sevdiğin gün: ${w.favoriteDay}", AdtTheme.type.bodyStrong, color = fg)
                }
                else -> SummaryCard(w, fg)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (bg == accent) fg else accent), contentAlignment = Alignment.Center) {
                AdtIcon(AdtIcons.Note, null, tint = if (bg == accent) bg else onAccent, size = 12.dp)
            }
            Spacer(Modifier.width(8.dp))
            Txt("ADT MUSIC", AdtTheme.type.label, color = fg)
        }
    }
}

@Composable
private fun SummaryCard(w: WrappedData, fg: Color) {
    Column(Modifier.fillMaxSize()) {
        Txt("Özetin", AdtTheme.type.display, color = fg)
        Spacer(Modifier.height(20.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Txt("EN ÇOK ŞARKI", AdtTheme.type.label, color = fg.copy(alpha = 0.6f))
                w.topSongs.take(5).forEachIndexed { i, (s, _) ->
                    Txt("${i + 1}. ${s.title}", AdtTheme.type.bodyStrong, color = fg, maxLines = 1)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Txt("EN ÇOK SANATÇI", AdtTheme.type.label, color = fg.copy(alpha = 0.6f))
                w.topArtists.take(5).forEachIndexed { i, (a, _) ->
                    Txt("${i + 1}. $a", AdtTheme.type.bodyStrong, color = fg, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Txt("DAKİKA", AdtTheme.type.label, color = fg.copy(alpha = 0.6f))
                Txt("%,d".format(w.minutes).replace(',', '.'), AdtTheme.type.display, color = fg)
            }
            Column(Modifier.weight(1f)) {
                Txt("KİŞİLİK", AdtTheme.type.label, color = fg.copy(alpha = 0.6f))
                Txt(w.personality, AdtTheme.type.headline, color = fg, maxLines = 2)
            }
        }
        Spacer(Modifier.height(20.dp))
        Txt(
            "${w.plays} çalma · ${w.distinctSongs} şarkı · ${w.distinctArtists} sanatçı",
            AdtTheme.type.bodyStrong, color = fg.copy(alpha = 0.8f), align = TextAlign.Start,
        )
    }
}

/** Görseli önbelleğe kaydeder ve paylaşım menüsünü açar. */
suspend fun shareBitmap(
    context: Context,
    bitmap: Bitmap,
    name: String = "adt-wrapped",
    text: String = "ADT Music Wrapped'im 🎧",
    title: String = "Wrapped'ini paylaş",
) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "$name-${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
