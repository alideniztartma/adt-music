package com.alideniz.adtmusic.ui.screens

import com.alideniz.adtmusic.platform.Context
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.alideniz.adtmusic.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Badge
import com.alideniz.adtmusic.data.Compatibility
import com.alideniz.adtmusic.data.Insights
import com.alideniz.adtmusic.data.Streak
import com.alideniz.adtmusic.data.Weekly
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.SectionTitle
import com.alideniz.adtmusic.ui.components.SongRow
import com.alideniz.adtmusic.ui.components.TextInput
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.shareText
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ ana sayfa kartları

/** Ana sayfadaki küçük seri rozeti: "🔥 5". */
@Composable
fun StreakChip(streak: Streak, onClick: () -> Unit) {
    if (streak.current <= 0) return
    val c = AdtTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (streak.listenedToday) c.accent else c.surface)
            .press(label = "Dinleme serisi", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("🔥 ${streak.current}", AdtTheme.type.bodyStrong, color = if (streak.listenedToday) c.onAccent else c.ink)
    }
}

/** "Bu hafta" özeti. */
@Composable
fun WeeklyCard(w: Weekly, onClick: () -> Unit) {
    val c = AdtTheme.colors
    val diff = if (w.previousMinutes > 0) ((w.minutes - w.previousMinutes) * 100 / w.previousMinutes).toInt() else null
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(AdtTheme.shape)
            .background(c.surface)
            .press(label = "Haftalık özet", onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt("BU HAFTA", AdtTheme.type.label, color = c.accent)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt("${w.minutes}", AdtTheme.type.display)
                Spacer(Modifier.width(6.dp))
                Txt("dakika", AdtTheme.type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
            }
            Txt(
                listOfNotNull(
                    diff?.let { if (it >= 0) "geçen haftadan %$it fazla ↑" else "geçen haftadan %${-it} az ↓" },
                    "${w.distinctSongs} şarkı",
                ).joinToString(" · "),
                AdtTheme.type.caption, color = c.muted,
            )
            w.topArtist?.let { Txt("Haftanın sanatçısı: ${it.first}", AdtTheme.type.caption, color = c.muted, maxLines = 1) }
        }
        w.topSong?.let { (song, _) ->
            Spacer(Modifier.width(12.dp))
            Cover(song.thumbnail, Modifier.size(72.dp))
        }
    }
}

// ------------------------------------------------------------------ rozetler ve seri

@Composable
fun BadgesScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val data by produceState<Pair<Streak, List<Badge>>?>(null) {
        val rows = app.dao.historySince(0)
        val done = app.downloads.states.value.count { it.value.state == DlState.DONE }
        value = Insights.streak(rows) to Insights.badges(app.dao, done)
        app.checkBadges(announce = false)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("Rozetler", "Dinledikçe kazanılır", onBack = { ui.back() }) }
        val d = data
        if (d == null) {
            item { LoadingBlock() }
        } else {
            val (streak, badges) = d
            item {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(AdtTheme.shape)
                        .background(c.ink)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt("DİNLEME SERİSİ", AdtTheme.type.label, color = c.accent)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt("${streak.current}", AdtTheme.type.hero.copy(fontSize = 64.sp), color = c.paper)
                            Spacer(Modifier.width(8.dp))
                            Txt("gün", AdtTheme.type.title, color = c.paper, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Txt(
                            if (streak.listenedToday) "Bugünkü dinlemeni yaptın ✓" else "Seriyi korumak için bugün bir şarkı dinle!",
                            AdtTheme.type.caption, color = c.paper.copy(alpha = 0.75f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Txt("EN UZUN", AdtTheme.type.label, color = c.paper.copy(alpha = 0.6f))
                        Txt("${streak.longest} gün", AdtTheme.type.headline, color = c.paper)
                        Spacer(Modifier.height(6.dp))
                        Txt("TOPLAM", AdtTheme.type.label, color = c.paper.copy(alpha = 0.6f))
                        Txt("${streak.totalDays} gün", AdtTheme.type.headline, color = c.paper)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Txt(
                    "${badges.count { it.unlocked }} / ${badges.size} rozet kazandın",
                    AdtTheme.type.title, modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            val pairs = badges.sortedByDescending { it.unlocked }.chunked(2)
            itemsIndexed(pairs) { _, pair ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { b -> BadgeCard(b, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

@Composable
private fun BadgeCard(b: Badge, modifier: Modifier) {
    val c = AdtTheme.colors
    Column(
        modifier
            .clip(AdtTheme.shape)
            .background(if (b.unlocked) c.accent.copy(alpha = 0.14f) else c.surface)
            .padding(14.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (b.unlocked) c.accent else c.line),
            contentAlignment = Alignment.Center,
        ) {
            Txt(b.icon, AdtTheme.type.headline.copy(fontSize = 22.sp), color = if (b.unlocked) c.onAccent else c.faint)
        }
        Spacer(Modifier.height(10.dp))
        Txt(b.title, AdtTheme.type.bodyStrong, color = if (b.unlocked) c.ink else c.muted)
        Txt(b.description, AdtTheme.type.caption, color = c.muted, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(b.progress).height(4.dp).background(if (b.unlocked) c.accent else c.ink))
        }
        Spacer(Modifier.height(4.dp))
        Txt(if (b.unlocked) "Kazanıldı ✓" else "${minOf(b.value, b.goal)} / ${b.goal}", AdtTheme.type.caption, color = if (b.unlocked) c.accent else c.faint)
    }
}

// ------------------------------------------------------------------ Mixtape (arkadaşla eşleşme)

private fun qrBitmap(text: String, size: Int = 640, dark: Int, light: Int): java.awt.image.BufferedImage {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until size) for (y in 0 until size) bmp.setRGB(x, y, if (matrix[x, y]) dark else light)
    return bmp
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteScreen() {
    val context = LocalContext.current
    val app = context.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    var name by remember { mutableStateOf(app.settings.profileName.value) }
    var friendCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Compatibility?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val myCode by produceState<String?>(null, name) {
        value = withContext(Dispatchers.Default) { Insights.encode(Insights.myProfile(app.dao, name.ifBlank { "ADT dinleyicisi" })) }
    }
    val inkArgb = c.ink.copy(alpha = 1f).toArgb()
    val paperArgb = c.paper.copy(alpha = 1f).toArgb()
    val qr by produceState<java.awt.image.BufferedImage?>(null, myCode, c.isDark) {
        val code = myCode ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { qrBitmap(code, 640, inkArgb, paperArgb) }.getOrNull() }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Mixtape", "Mixtape'ini arkadaşınla takas et, uyumunuzu gör", onBack = { ui.back() })
            Column(Modifier.padding(horizontal = 20.dp)) {
                Txt("SENİN MIXTAPE'İN", AdtTheme.type.label, color = c.muted)
                Spacer(Modifier.height(8.dp))
                TextInput(
                    value = name,
                    onValueChange = { name = it.take(24); app.settings.profileName.set(name) },
                    placeholder = "Adın (kartta görünür)",
                    style = AdtTheme.type.bodyStrong,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(150.dp).clip(AdtTheme.shape).background(c.surface), contentAlignment = Alignment.Center) {
                        val b = qr
                        if (b != null) Image(b.toComposeImageBitmap(), "Mixtape QR kodu", Modifier.fillMaxSize().padding(6.dp))
                        else LoadingBlock()
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("Mixtape kodunu arkadaşına gönder ya da QR'ı göster. O da kendi kodunu sana yollasın.", AdtTheme.type.caption, color = c.muted)
                        Btn("Paylaş", { myCode?.let { shareText(context, "ADT Music mixtape'im", "ADT Music'te mixtape'lerimizi karşılaştıralım! Kodum:\n\n$it") } }, Modifier.fillMaxWidth(), icon = AdtIcons.Share, style = BtnStyle.INK, height = 42.dp)
                        Btn("Kopyala", {
                            myCode?.let {
                                com.alideniz.adtmusic.platform.copyToClipboard(it)
                                app.toast("Kod kopyalandı")
                            }
                        }, Modifier.fillMaxWidth(), style = BtnStyle.OUTLINE, height = 42.dp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Line()
                Spacer(Modifier.height(16.dp))
                Txt("ARKADAŞININ MIXTAPE'İ", AdtTheme.type.label, color = c.muted)
                Spacer(Modifier.height(8.dp))
                TextInput(
                    value = friendCode,
                    onValueChange = { friendCode = it; error = null },
                    placeholder = "MIX1:… kodunu yapıştır",
                    style = AdtTheme.type.bodyStrong,
                )
                Spacer(Modifier.height(10.dp))
                Btn("Mixtape'leri karşılaştır", {
                    val friend = Insights.decode(friendCode)
                    if (friend == null) { error = "Kod okunamadı. Tamamını kopyaladığından emin ol."; return@Btn }
                    val code = myCode ?: return@Btn
                    val me = Insights.decode(code) ?: return@Btn
                    result = Insights.compare(me, friend)
                }, Modifier.fillMaxWidth(), icon = AdtIcons.Cassette, style = BtnStyle.ACCENT)
                error?.let { Spacer(Modifier.height(8.dp)); Txt(it, AdtTheme.type.bodyStrong, color = c.accent) }
            }
        }
        result?.let { r ->
            item {
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(AdtTheme.shape)
                        .background(c.accent)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Txt("SEN × ${r.friendName.uppercase()}", AdtTheme.type.label, color = c.onAccent)
                    Txt("%${r.percent}", AdtTheme.type.hero.copy(fontSize = 84.sp, lineHeight = 84.sp), color = c.onAccent)
                    Txt(r.label, AdtTheme.type.headline, color = c.onAccent, align = TextAlign.Center)
                }
                if (r.commonArtists.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Ortak sanatçılar")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.commonArtists.forEach { a ->
                            Box(
                                Modifier.clip(CircleShape).background(c.surface).press(label = a) { ui.navigate(Routes.artist(a, null)) }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Txt(a, AdtTheme.type.bodyStrong) }
                        }
                    }
                }
                if (r.commonSongs.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("İkinizin de sevdiği şarkılar")
                    Spacer(Modifier.height(4.dp))
                }
            }
            itemsIndexed(r.commonSongs, key = { i, s -> "c$i-${s.id}" }) { i, s ->
                SongRow(s, { app.player.play(r.commonSongs, i) }, { ui.show(SheetSpec.SongOptions(s)) })
            }
            if (r.suggestions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("${r.friendName} dinliyor, sen henüz değil", action = "Hepsini çal", onAction = { app.player.play(r.suggestions) })
                    Spacer(Modifier.height(4.dp))
                }
                itemsIndexed(r.suggestions, key = { i, s -> "g$i-${s.id}" }) { i, s ->
                    SongRow(s, { app.player.play(r.suggestions, i) }, { ui.show(SheetSpec.SongOptions(s)) })
                }
            }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}
