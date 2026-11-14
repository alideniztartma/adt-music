package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================ Şarkı hakkında

private data class OnlineInfo(
    val uploaded: String?,
    val views: Long,
    val likes: Long,
    val category: String?,
    val tags: List<String>,
    val description: String,
    val channel: String?,
    val subscribers: Long,
)

private data class PersonalInfo(
    val plays: Int,
    val listenedMs: Long,
    val firstPlayed: Long?,
    val likedAt: Long?,
    val moments: Int,
)

private val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale("tr"))

private fun compact(n: Long): String = when {
    n < 0 -> "—"
    n >= 1_000_000_000 -> "%.1f Mr".format(Locale("tr"), n / 1e9)
    n >= 1_000_000 -> "%.1f Mn".format(Locale("tr"), n / 1e6)
    n >= 10_000 -> "%.0f B".format(Locale("tr"), n / 1e3)
    n >= 1_000 -> "%.1f B".format(Locale("tr"), n / 1e3)
    else -> n.toString()
}

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min >= 60 -> "${min / 60} sa ${min % 60} dk"
        min > 0 -> "$min dk"
        else -> "${ms / 1000} sn"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoContent(spec: SheetSpec.SongInfo, ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val song = spec.song
    var expanded by remember { mutableStateOf(false) }

    val online by produceState<OnlineInfo?>(null, song.id) {
        value = runCatching {
            val info = app.youtube.streamInfo(song.id)
            val raw = info.description?.content.orEmpty()
            val text = if (raw.contains('<')) {
                androidx.core.text.HtmlCompat.fromHtml(raw, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            } else raw
            OnlineInfo(
                uploaded = runCatching { info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }
                    .getOrNull()?.let { dateFmt.format(Date(it)) } ?: info.textualUploadDate,
                views = info.viewCount,
                likes = info.likeCount,
                category = info.category?.takeIf { it.isNotBlank() },
                tags = info.tags.orEmpty().filter { it.isNotBlank() }.take(14),
                description = text.trim(),
                channel = info.uploaderName?.removeSuffix(" - Topic"),
                subscribers = info.uploaderSubscriberCount,
            )
        }.getOrNull() ?: OnlineInfo(null, -1, -1, null, emptyList(), "", null, -1)
    }
    val personal by produceState<PersonalInfo?>(null, song.id) {
        value = runCatching {
            PersonalInfo(
                plays = app.dao.validPlays(song.id),
                listenedMs = app.dao.listenedTotal(song.id),
                firstPlayed = app.dao.firstPlayed(song.id),
                likedAt = app.dao.song(song.id)?.likedAt,
                moments = app.dao.momentCountFor(song.id),
            )
        }.getOrNull()
    }

    Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song.thumbnail, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Txt("ŞARKI HAKKINDA", AdtTheme.type.label, color = c.accent)
                Txt(song.title, AdtTheme.type.headline, maxLines = 2)
                Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
            }
        }
        Line(Modifier.padding(vertical = 8.dp))

        val o = online
        if (o == null) {
            Txt("Bilgiler yükleniyor…", AdtTheme.type.body, color = c.muted, modifier = Modifier.padding(24.dp))
        } else {
            StatGrid(
                listOf(
                    "YAYIN" to (o.uploaded ?: "—"),
                    "SÜRE" to if (song.durationSec > 0) "%d:%02d".format(song.durationSec / 60, song.durationSec % 60) else "—",
                    "GÖRÜNTÜLENME" to compact(o.views),
                    "BEĞENİ" to compact(o.likes),
                    "KANAL" to (o.channel ?: song.artist.ifBlank { "—" }),
                    "ABONE" to compact(o.subscribers),
                ),
            )
            o.category?.let {
                Txt("Kategori: $it", AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
        }

        personal?.let { p ->
            Spacer(Modifier.height(10.dp))
            Txt("SENİN İÇİN", AdtTheme.type.label, color = c.accent, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            StatGrid(
                listOf(
                    "DİNLEME" to "${p.plays} kez",
                    "TOPLAM" to if (p.listenedMs > 0) duration(p.listenedMs) else "—",
                    "İLK DİNLEYİŞ" to (p.firstPlayed?.let { dateFmt.format(Date(it)) } ?: "Henüz yok"),
                    "BEĞENDİN" to (p.likedAt?.let { dateFmt.format(Date(it)) } ?: "—"),
                    "ANILAR" to "${p.moments}",
                    "SIRALAMA" to when {
                        p.plays >= 50 -> "Efsane"
                        p.plays >= 20 -> "Favori"
                        p.plays >= 5 -> "Tanıdık"
                        p.plays > 0 -> "Yeni"
                        else -> "Keşfet"
                    },
                ),
            )
        }

        Btn(
            "Yorumları gör",
            { ui.show(SheetSpec.Comments(song)) },
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth(),
            icon = AdtIcons.Comment,
            style = BtnStyle.OUTLINE,
        )

        if (o != null && o.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Txt("AÇIKLAMA", AdtTheme.type.label, color = c.accent, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            Txt(
                o.description,
                AdtTheme.type.body,
                color = c.muted,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                modifier = Modifier.padding(horizontal = 24.dp).press(label = "Aç/kapat") { expanded = !expanded },
            )
            Txt(
                if (expanded) "DAHA AZ" else "DEVAMI",
                AdtTheme.type.label,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).press { expanded = !expanded },
            )
        }

        if (o != null && o.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Txt("ETİKETLER", AdtTheme.type.label, color = c.accent, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            FlowRow(
                Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                o.tags.forEach { tag ->
                    Chip("#$tag", false, { ui.dismiss(); ui.navigate(com.alideniz.adtmusic.ui.Routes.SEARCH); app.pendingSearch.value = tag })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    val c = AdtTheme.colors
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, v) ->
                    Column(
                        Modifier.weight(1f).background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Txt(k, AdtTheme.type.label, color = c.muted, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Txt(v, AdtTheme.type.bodyStrong, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ============================================================ Söz kartı

private enum class CardStyle(val label: String) { COLOR("Renkli"), DARK("Koyu"), LIGHT("Açık") }

@Composable
fun LyricCardContent(spec: SheetSpec.LyricCard, ui: AppUi) {
    val context = LocalContext.current
    val app = context.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(CardStyle.COLOR) }
    val layer = rememberGraphicsLayer()
    val song = spec.song

    val (bg, fg, mark) = when (style) {
        CardStyle.COLOR -> Triple(c.accent, c.onAccent, c.onAccent)
        CardStyle.DARK -> Triple(Color(0xFF111111), Color(0xFFF4F2EE), c.accent)
        CardStyle.LIGHT -> Triple(Color(0xFFF4F2EE), Color(0xFF111111), c.accent)
    }
    val size = when {
        spec.line.length > 90 -> 24.sp
        spec.line.length > 55 -> 30.sp
        spec.line.length > 30 -> 36.sp
        else -> 44.sp
    }

    Txt("Söz kartı", AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Box(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
    ) {
        Column(Modifier.fillMaxSize().background(bg).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(song.thumbnail, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(song.title, AdtTheme.type.bodyStrong, color = fg, maxLines = 1)
                    Txt(song.artist, AdtTheme.type.caption, color = fg.copy(alpha = 0.7f), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Txt("“", AdtTheme.type.hero.copy(fontSize = 72.sp, lineHeight = 60.sp), color = mark)
            Txt(
                spec.line,
                AdtTheme.type.hero.copy(fontSize = size, lineHeight = size * 1.08f, fontWeight = FontWeight.ExtraBold),
                color = fg,
                maxLines = 6,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                    Txt("ADT", AdtTheme.type.label.copy(fontSize = 8.sp), color = Color(0xFFF4F2EE))
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(6.dp).clip(CircleShape)
                            .background(com.alideniz.adtmusic.ui.theme.AdtOrange)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Txt("ADT MUSIC", AdtTheme.type.label, color = fg)
            }
        }
    }
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardStyle.entries.forEach { s -> Chip(s.label, s == style, { style = s }) }
    }
    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(
            "Paylaş",
            {
                scope.launch {
                    runCatching {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        shareBitmap(
                            context, bmp, name = "adt-lyric",
                            text = "“${spec.line}” — ${song.title}, ${song.artist}",
                            title = "Söz kartını paylaş",
                        )
                    }.onFailure { app.toast("Görsel oluşturulamadı") }
                }
            },
            Modifier.weight(1f),
            icon = AdtIcons.Share,
            style = BtnStyle.ACCENT,
        )
        Btn("Kapat", { ui.dismiss() }, style = BtnStyle.OUTLINE)
    }
    Spacer(Modifier.height(8.dp))
}
