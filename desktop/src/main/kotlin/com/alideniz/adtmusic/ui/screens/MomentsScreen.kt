package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.alideniz.adtmusic.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.db.MomentEntity
import com.alideniz.adtmusic.data.db.MomentRow
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Anı duyguları. */
data class MoodTag(val key: String, val label: String, val color: Color)

val MOMENT_MOODS = listOf(
    MoodTag("happy", "Mutlu", Color(0xFFFFB400)),
    MoodTag("sad", "Hüzünlü", Color(0xFF4D7CFF)),
    MoodTag("nostalgic", "Nostaljik", Color(0xFFB07CFF)),
    MoodTag("energetic", "Enerjik", Color(0xFFFF5A1F)),
    MoodTag("calm", "Huzurlu", Color(0xFF18B36B)),
    MoodTag("love", "Âşık", Color(0xFFFF4D8D)),
    MoodTag("motivated", "Motive", Color(0xFFE5383B)),
    MoodTag("thoughtful", "Düşünceli", Color(0xFF64748B)),
)

fun moodOf(key: String): MoodTag = MOMENT_MOODS.firstOrNull { it.key == key } ?: MOMENT_MOODS[2]

private val trLocale = Locale("tr")

@Composable
fun MoodPill(mood: MoodTag, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val c = AdtTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) mood.color else Color.Transparent)
            .border(1.5.dp, mood.color, CircleShape)
            .then(if (onClick != null) Modifier.press(label = mood.label, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(mood.label, AdtTheme.type.caption, color = if (selected) Color.White else c.ink)
    }
}

// ------------------------------------------------------------------ anı oluştur (alt panel)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMomentContent(spec: SheetSpec.AddMoment, ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf("nostalgic") }
    var note by remember { mutableStateOf("") }
    var capsule by remember { mutableStateOf(0) } // ay
    val focus = remember { FocusRequester() }

    Column(Modifier.padding(horizontal = 24.dp)) {
        Txt("Anı oluştur", AdtTheme.type.headline)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(spec.song.thumbnail, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(spec.song.title, AdtTheme.type.bodyStrong, maxLines = 1)
                Txt("${formatMs(spec.positionMs)} anında", AdtTheme.type.caption, color = c.accent)
            }
        }
        Spacer(Modifier.height(18.dp))
        Txt("BU AN NASIL HİSSETTİRİYOR?", AdtTheme.type.label, color = c.muted)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MOMENT_MOODS.forEach { m -> MoodPill(m, m.key == mood) { mood = m.key } }
        }
        Spacer(Modifier.height(18.dp))
        Txt("NOTUN", AdtTheme.type.label, color = c.muted)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(AdtTheme.shape)
                .border(1.5.dp, c.ink, AdtTheme.shape)
                .padding(14.dp)
        ) {
            if (note.isEmpty()) Txt("Bu şarkıyı ilk kez yaz tatilinde dinlemiştim…", AdtTheme.type.body, color = c.faint)
            BasicTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                textStyle = AdtTheme.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.height(18.dp))
        Txt("ZAMAN KAPSÜLÜ", AdtTheme.type.label, color = c.muted)
        Txt("Seçersen bu anı o tarihte karşına yeniden çıkar; o zamana kadar not gizli kalır.", AdtTheme.type.caption, color = c.muted)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Kapalı", 1 to "1 ay", 6 to "6 ay", 12 to "1 yıl", 60 to "5 yıl").forEach { (m, label) ->
                Chip(label, capsule == m, { capsule = m })
            }
        }
        Spacer(Modifier.height(20.dp))
        Btn(
            "Anıyı kaydet",
            {
                val revealAt = if (capsule > 0) Calendar.getInstance().apply { add(Calendar.MONTH, capsule) }.timeInMillis else null
                scope.launch {
                    app.dao.ensureSong(spec.song)
                    app.dao.insertMoment(
                        MomentEntity(
                            songId = spec.song.id,
                            positionMs = spec.positionMs,
                            mood = mood,
                            note = note.trim(),
                            revealAt = revealAt,
                        )
                    )
                    app.toast(if (revealAt != null) "Zaman kapsülü mühürlendi 💌" else "Anı kaydedildi")
                }
                ui.dismiss()
            },
            Modifier.fillMaxWidth(),
            icon = AdtIcons.Moment,
            style = BtnStyle.ACCENT,
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ zaman kapsülü açıldı

@Composable
fun CapsuleContent(spec: SheetSpec.Capsule, ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val row = spec.row
    val m = row.moment
    val date = remember(m.createdAt) { SimpleDateFormat("d MMMM yyyy", trLocale).format(Date(m.createdAt)) }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Txt("ZAMAN KAPSÜLÜ AÇILDI", AdtTheme.type.label, color = c.accent)
        Spacer(Modifier.height(6.dp))
        Txt("$date tarihinde kendine yazdın:", AdtTheme.type.title)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AdtTheme.shape)
                .background(moodOf(m.mood).color.copy(alpha = 0.14f))
                .padding(18.dp)
        ) {
            Txt(m.note.ifBlank { "(Not yazmamıştın, sadece bu anı işaretlemiştin.)" }, AdtTheme.type.headline)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(row.songThumb, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(row.songTitle.orEmpty(), AdtTheme.type.bodyStrong, maxLines = 1)
                Txt("${row.songArtist.orEmpty()} · ${formatMs(m.positionMs)}", AdtTheme.type.caption, color = c.muted, maxLines = 1)
            }
            MoodPill(moodOf(m.mood))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Kapat", { ui.dismiss() }, Modifier.weight(1f), style = BtnStyle.OUTLINE)
            Btn("O anı dinle", {
                app.player.playAt(row.song(), m.positionMs)
                ui.dismiss()
                ui.openPlayer()
            }, Modifier.weight(1f), icon = AdtIcons.Play, style = BtnStyle.ACCENT)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ oynatıcıdaki işaretler

/** İlerleme çubuğunun üstünde, anıların bulunduğu saniyelerde küçük işaretler. */
@Composable
fun MomentMarkers(moments: List<MomentEntity>, durationMs: Long, onTap: (MomentEntity) -> Unit) {
    if (moments.isEmpty() || durationMs <= 0) return
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
        val w = maxWidth
        moments.forEach { m ->
            val f = (m.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = w * f - 7.dp)
                    .size(14.dp)
                    .press(label = "Anı") { onTap(m) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(moodOf(m.mood).color))
            }
        }
    }
}

// ------------------------------------------------------------------ anılar ekranı

@Composable
fun MomentsScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val moments by remember { app.dao.moments() }.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val dateFmt = remember { SimpleDateFormat("d MMMM yyyy · HH:mm", trLocale) }
    val shown = moments.filter { filter == null || it.moment.mood == filter }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Anıların", "Şarkıların sende bıraktığı izler", onBack = { ui.back() })
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Anılar · ${moments.size}", tab == 0, { tab = 0 })
                Chip("Anı haritası", tab == 1, { tab = 1 })
            }
            Spacer(Modifier.height(14.dp))
        }
        if (tab == 0) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("Hepsi", filter == null, { filter = null }) }
                    items(MOMENT_MOODS) { m -> MoodPill(m, filter == m.key) { filter = if (filter == m.key) null else m.key } }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (shown.isEmpty()) {
                item {
                    MessageBlock(
                        "Henüz anı yok",
                        "Şarkı çalarken oynatıcıdaki ✦ düğmesine dokun: o saniyeye bir his ve not bırak.",
                        icon = AdtIcons.Moment,
                    )
                }
            }
            items(shown, key = { it.moment.id }) { row ->
                val m = row.moment
                val locked = m.revealAt != null && !m.revealed && m.revealAt > now
                val mood = moodOf(m.mood)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .press(label = "Anıyı dinle") {
                            app.player.playAt(row.song(), m.positionMs)
                            ui.openPlayer()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(52.dp).background(mood.color))
                        Spacer(Modifier.width(12.dp))
                        Cover(row.songThumb, Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(row.songTitle.orEmpty(), AdtTheme.type.bodyStrong, maxLines = 1)
                            Txt("${row.songArtist.orEmpty()} · ${formatMs(m.positionMs)}", AdtTheme.type.caption, color = c.muted, maxLines = 1)
                        }
                        IconBtn(AdtIcons.Trash, "Anıyı sil", {
                            ui.show(SheetSpec.Confirm("Anı silinsin mi?", "Bu anı kalıcı olarak silinecek.", "Sil") {
                                scope.launch { app.dao.deleteMoment(m.id) }
                            })
                        }, tint = c.faint, size = 40.dp, iconSize = 18.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoodPill(mood)
                        Spacer(Modifier.width(10.dp))
                        Txt(dateFmt.format(Date(m.createdAt)), AdtTheme.type.caption, color = c.faint)
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        locked -> Txt(
                            "🔒 Zaman kapsülü · ${SimpleDateFormat("d MMMM yyyy", trLocale).format(Date(m.revealAt ?: 0))} tarihinde açılacak",
                            AdtTheme.type.bodyStrong, color = c.accent,
                        )
                        m.note.isNotBlank() -> Txt("“${m.note}”", AdtTheme.type.title)
                    }
                }
                Line()
            }
        } else {
            item { MomentMap() }
        }
        item { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}

private data class MonthEntry(val label: String, val songs: List<Pair<Song, Int>>, val moments: Int, val minutes: Long)

/** Anı haritası: hangi şarkılar hayatının hangi döneminde sana eşlik etti. */
@Composable
private fun MomentMap() {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val months by produceState<List<MonthEntry>?>(null) {
        val rows = app.dao.historySince(0)
        val moments = app.dao.moments()
        val momentList = runCatching { moments.first() }.getOrDefault(emptyList())
        val keyFmt = SimpleDateFormat("yyyy-MM", trLocale)
        val labelFmt = SimpleDateFormat("MMMM yyyy", trLocale)
        val momentsByMonth = momentList.groupBy { keyFmt.format(Date(it.moment.createdAt)) }
        value = rows
            .filter { it.listenedMs < 0 || it.listenedMs >= 30_000 }
            .groupBy { keyFmt.format(Date(it.playedAt)) }
            .toSortedMap(compareByDescending { it })
            .map { (key, list) ->
                val top = list.groupBy { it.song.id }
                    .map { (_, l) -> l.first().song.toSong() to l.size }
                    .sortedByDescending { it.second }
                    .take(3)
                MonthEntry(
                    label = labelFmt.format(keyFmt.parse(key) ?: Date()).replaceFirstChar { it.uppercase() },
                    songs = top,
                    moments = momentsByMonth[key]?.size ?: 0,
                    minutes = list.sumOf { it.listenedMs.coerceAtLeast(0) } / 60_000,
                )
            }
    }
    val list = months
    when {
        list == null -> LoadingBlock()
        list.isEmpty() -> MessageBlock("Harita boş", "Dinledikçe hangi ayın hangi şarkılarla geçtiği burada belirir.", icon = AdtIcons.Moment)
        else -> Column {
            list.forEachIndexed { i, month ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    // zaman çizgisi
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (i == 0) c.accent else c.ink))
                        Box(Modifier.width(2.dp).height(150.dp).background(c.line))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(month.label, AdtTheme.type.headline)
                        Txt(
                            listOfNotNull(
                                if (month.minutes > 0) "${month.minutes} dk" else null,
                                if (month.moments > 0) "${month.moments} anı" else null,
                            ).joinToString(" · ").ifBlank { "Dinlediklerin" },
                            AdtTheme.type.caption, color = c.muted,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            month.songs.forEach { (song, count) ->
                                Column(
                                    Modifier.width(92.dp).press(label = song.title) { app.player.playRadio(song) }
                                ) {
                                    Cover(song.thumbnail, Modifier.size(92.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Txt(song.title, AdtTheme.type.caption, maxLines = 1)
                                    Txt("$count kez", AdtTheme.type.caption, color = c.faint, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
