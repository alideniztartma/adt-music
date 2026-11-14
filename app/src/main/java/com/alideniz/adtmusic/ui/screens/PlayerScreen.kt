package com.alideniz.adtmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Lyrics
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.playback.PlayerUiState
import com.alideniz.adtmusic.playback.Progress
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.SeekBar
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.ui.theme.tint
import com.alideniz.adtmusic.util.formatMs
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val state by app.player.state.collectAsState()
    val progress by app.player.progress.collectAsState()
    val song = state.current ?: return
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val sleep by app.sleepTimer.remaining.collectAsState()
    val scope = rememberCoroutineScope()
    val liked = song.id in likedIds
    val soundChanged = kotlin.math.abs(state.speed - 1f) > 0.001f || kotlin.math.abs(state.semitones) > 0.05f
    val bg by animateColorAsState(c.tint(if (c.isDark) 0.14f else 0.08f), tween(700), label = "bg")

    val dismissOffset = remember { Animatable(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dismissOffset.value }
            .background(bg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dismissOffset.value > 260f) {
                                ui.playerExpanded = false
                                dismissOffset.snapTo(0f)
                            } else dismissOffset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { dismissOffset.animateTo(0f) } },
                    onVerticalDrag = { _, dy ->
                        scope.launch { dismissOffset.snapTo((dismissOffset.value + dy).coerceAtLeast(0f)) }
                    },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // ---- üst çubuk
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).press(label = "Küçült") { ui.playerExpanded = false }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdtIcon(AdtIcons.ChevronDown, "Küçült", size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Txt("ŞU AN ÇALIYOR", AdtTheme.type.label)
                }
                Txt(
                    "${(state.index + 1).coerceAtLeast(1).toString().padStart(2, '0')} / ${state.queue.size.toString().padStart(2, '0')}",
                    AdtTheme.type.caption, color = c.muted,
                )
                IconBtn(AdtIcons.More, "Seçenekler", { ui.show(SheetSpec.SongOptions(song, queueIndex = null)) })
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
            Spacer(Modifier.height(20.dp))

            Crossfade(ui.lyricsMode, label = "lyricsMode", modifier = Modifier.weight(1f)) { lyricsMode ->
                if (lyricsMode) {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Cover(song.thumbnail, Modifier.size(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(song.title, AdtTheme.type.title, maxLines = 1)
                                Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
                            }
                            IconBtn(if (liked) AdtIcons.HeartFilled else AdtIcons.Heart, "Beğen", {
                                scope.launch {
                                    app.dao.ensureSong(song)
                                    app.dao.setLiked(song.id, if (liked) null else System.currentTimeMillis())
                                }
                            }, tint = if (liked) c.accent else c.ink)
                        }
                        Spacer(Modifier.height(12.dp))
                        LyricsPane(song, progress, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        SwipeCover(song, state, Modifier.fillMaxWidth().weight(1f, fill = false))
                        Spacer(Modifier.height(24.dp))
                        AnimatedContent(
                            targetState = song,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                            label = "title",
                        ) { s ->
                            Column {
                                Txt(
                                    s.title,
                                    if (s.title.length > 26) AdtTheme.type.display else AdtTheme.type.hero,
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(8.dp))
                                Txt(
                                    s.artist,
                                    AdtTheme.type.body.copy(fontSize = 16.sp),
                                    color = c.muted,
                                    maxLines = 1,
                                    modifier = Modifier.press(label = "Sanatçıya git") {
                                        ui.navigate(Routes.artist(s.artist, null))
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- ilerleme
            var dragPreview by remember { mutableStateOf<Float?>(null) }
            SeekBar(
                fraction = progress.fraction,
                bufferedFraction = if (progress.duration > 0) progress.buffered.toFloat() / progress.duration else 0f,
                onSeek = { app.player.seekTo((it * progress.duration).toLong()) },
                color = c.ink,
                onDragChange = { dragPreview = it },
            )
            Row(Modifier.fillMaxWidth()) {
                val shown = dragPreview?.let { (it * progress.duration).toLong() } ?: progress.position
                Txt(formatMs(shown), AdtTheme.type.caption, color = c.muted)
                Spacer(Modifier.weight(1f))
                if (state.isBuffering) {
                    Txt("YÜKLENİYOR", AdtTheme.type.label, color = c.accent)
                } else if (soundChanged) {
                    val st = kotlin.math.round(state.semitones).toInt()
                    Txt(
                        listOfNotNull(
                            if (state.speed != 1f) "%.2fx".format(state.speed) else null,
                            if (st != 0) (if (st > 0) "+$st TON" else "$st TON") else null,
                        ).joinToString(" · "),
                        AdtTheme.type.label, color = c.accent,
                        modifier = Modifier.press(label = "Hız ve ton") { ui.show(SheetSpec.Speed) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Txt(formatMs(progress.duration), AdtTheme.type.caption, color = c.muted)
            }
            Spacer(Modifier.height(18.dp))

            // ---- ana kontroller: büyük kare çal düğmesi, önceki, sonraki
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(76.dp)
                        .background(c.accent)
                        .press(label = if (state.isPlaying) "Duraklat" else "Çal") { app.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    AdtIcon(if (state.isPlaying) AdtIcons.Pause else AdtIcons.Play, null, tint = c.onAccent, size = 32.dp)
                }
                Spacer(Modifier.width(12.dp))
                OutlinedControl(AdtIcons.Previous, "Önceki") { app.player.previous() }
                Spacer(Modifier.width(12.dp))
                OutlinedControl(AdtIcons.Next, "Sonraki", enabled = state.hasNext || state.repeatMode != Player.REPEAT_MODE_OFF) { app.player.next() }
                Spacer(Modifier.weight(1f))
                IconBtn(
                    if (liked) AdtIcons.HeartFilled else AdtIcons.Heart, if (liked) "Beğenmekten vazgeç" else "Beğen",
                    {
                        scope.launch {
                            app.dao.ensureSong(song)
                            app.dao.setLiked(song.id, if (liked) null else System.currentTimeMillis())
                        }
                    },
                    tint = if (liked) c.accent else c.ink,
                    size = 52.dp, iconSize = 28.dp,
                )
            }
            Spacer(Modifier.height(18.dp))

            // ---- araç çubuğu
            val dl = downloads[song.id]
            Row(
                Modifier.fillMaxWidth().border(1.dp, c.ink).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(AdtIcons.Shuffle, "Karıştır", { app.player.toggleShuffle() }, tint = if (state.shuffle) c.accent else c.ink, iconSize = 20.dp)
                IconBtn(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) AdtIcons.RepeatOne else AdtIcons.Repeat, "Tekrarla",
                    { app.player.cycleRepeat() },
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) c.accent else c.ink, iconSize = 20.dp,
                )
                IconBtn(AdtIcons.Lyrics, "Sözler", { ui.lyricsMode = !ui.lyricsMode }, tint = if (ui.lyricsMode) c.accent else c.ink, iconSize = 20.dp)
                IconBtn(AdtIcons.Queue, "Sıra", { ui.show(SheetSpec.Queue) }, iconSize = 20.dp)
                IconBtn(
                    if (dl?.state == DlState.DONE) AdtIcons.Check else AdtIcons.Download,
                    "İndir",
                    {
                        when (dl?.state) {
                            DlState.DONE -> app.toast("Bu şarkı zaten indirildi")
                            DlState.DOWNLOADING, DlState.QUEUED -> app.toast("İndiriliyor… %${((dl?.progress ?: 0f) * 100).toInt()}")
                            else -> { app.downloads.download(song); app.toast("İndirme başladı") }
                        }
                    },
                    tint = if (dl?.state == DlState.DONE) c.accent else c.ink, iconSize = 20.dp,
                )
                Box(contentAlignment = Alignment.Center) {
                    IconBtn(AdtIcons.Speed, "Hız ve ton", { ui.show(SheetSpec.Speed) }, tint = if (soundChanged) c.accent else c.ink, iconSize = 20.dp)
                }
                IconBtn(AdtIcons.Moon, "Uyku zamanlayıcısı", { ui.show(SheetSpec.SleepTimer) }, tint = if (sleep != null) c.accent else c.ink, iconSize = 20.dp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OutlinedControl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Box(
        Modifier
            .size(width = 56.dp, height = 76.dp)
            .border(1.dp, if (enabled) c.ink else c.line)
            .press(enabled = enabled, label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AdtIcon(icon, desc, tint = if (enabled) c.ink else c.faint, size = 24.dp)
    }
}

/** Kapak: sağa/sola kaydırınca şarkı değişir, çift dokununca beğenilir. */
@Composable
private fun SwipeCover(song: Song, state: PlayerUiState, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val c = AdtTheme.colors
    var heart by remember { mutableIntStateOf(0) }
    val heartAlpha = remember { Animatable(0f) }

    LaunchedEffect(heart) {
        if (heart > 0) {
            heartAlpha.snapTo(1f)
            heartAlpha.animateTo(0f, tween(700))
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopStart) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    translationX = offset.value
                    alpha = 1f - (abs(offset.value) / 900f).coerceIn(0f, 0.6f)
                    rotationZ = offset.value / 90f
                }
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                when {
                                    v < -160f -> { offset.animateTo(-1200f, tween(160)); app.player.next(); offset.snapTo(0f) }
                                    v > 160f -> { offset.animateTo(1200f, tween(160)); app.player.previous(); offset.snapTo(0f) }
                                    else -> offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        },
                    )
                }
                .pointerInput(song.id) {
                    detectTapGestures(onDoubleTap = {
                        scope.launch {
                            app.dao.ensureSong(song)
                            app.dao.setLiked(song.id, System.currentTimeMillis())
                        }
                        heart++
                    })
                },
        ) {
            Cover(song.thumbnail, Modifier.fillMaxSize(), contentDescription = song.title)
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = heartAlpha.value },
                contentAlignment = Alignment.Center,
            ) {
                AdtIcon(AdtIcons.HeartFilled, null, tint = c.accent, size = 96.dp)
            }
            if (state.isBuffering) {
                Box(Modifier.align(Alignment.BottomStart).background(c.ink).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Txt("YÜKLENİYOR", AdtTheme.type.label, color = c.paper)
                }
            }
        }
    }
}

/** Senkron sözler: çalan satır kayan bir dolguyla vurgulanır, satıra dokununca oraya atlar. */
@Composable
fun LyricsPane(song: Song, progress: Progress, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val textSize by app.settings.lyricsTextSize.flow.collectAsState()
    var reload by remember { mutableIntStateOf(0) }
    val lyrics by produceState<Load<Lyrics?>>(Load.Loading, song.id, reload) {
        value = Load.Loading
        value = runCatching { app.lyrics.lyricsFor(song) }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "") })
    }

    Box(modifier.fillMaxWidth()) {
        when (val l = lyrics) {
            Load.Loading -> LoadingBlock()
            is Load.Err -> LyricsMessage("Sözler yüklenemedi", "İnternet bağlantını kontrol et.") { reload++ }
            is Load.Ok -> {
                val data = l.data
                when {
                    data == null -> LyricsMessage("Söz bulunamadı", "Bu şarkının sözleri LRCLIB'de yok.") {
                        app.appScope.launch { app.lyrics.clearFor(song.id); reload++ }
                    }
                    !data.synced -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Txt(
                            "SENKRON DEĞİL", AdtTheme.type.label, color = c.muted,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        data.lines.forEach { line ->
                            Txt(
                                line.text.ifBlank { " " },
                                AdtTheme.type.title.copy(fontSize = (textSize * 0.75f).sp, lineHeight = (textSize * 1.05f).sp),
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                    else -> SyncedLyrics(data, progress, textSize)
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun SyncedLyrics(lyrics: Lyrics, progress: Progress, textSize: Int) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val listState = rememberLazyListState()
    val lines = lyrics.lines
    val pos = progress.position + 250 // küçük bir öne alma: satır geçişi daha doğal görünür
    val currentIndex = lines.indexOfLast { it.timeMs <= pos }
    val density = LocalDensity.current
    var userScrolling by remember { mutableStateOf(false) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(dragged) {
        if (dragged) userScrolling = true
        else {
            kotlinx.coroutines.delay(2500)
            userScrolling = false
        }
    }
    LaunchedEffect(currentIndex, userScrolling) {
        if (currentIndex >= 0 && !userScrolling) {
            val offset = with(density) { -(120.dp).roundToPx() }
            listState.animateScrollToItem(currentIndex, offset)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 120.dp, bottom = 240.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, line ->
            val isCurrent = i == currentIndex
            val style = AdtTheme.type.headline.copy(
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.15f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
            val text = line.text.ifBlank { "♪" }
            val modifier = Modifier
                .fillMaxWidth()
                .press(label = "Buraya atla") { app.player.seekTo(line.timeMs) }
                .padding(vertical = 10.dp)
            if (isCurrent) {
                val next = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + 4000)
                val f = ((pos - line.timeMs).toFloat() / (next - line.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                val brush = Brush.horizontalGradient(
                    0f to c.accent, f to c.accent, (f + 0.02f).coerceAtMost(1f) to c.ink, 1f to c.ink,
                )
                androidx.compose.foundation.text.BasicText(text, modifier, style = style.merge(TextStyle(brush = brush)))
            } else {
                Txt(text, style, modifier = modifier, color = if (i < currentIndex) c.faint else c.muted)
            }
        }
    }
}

@Composable
private fun LyricsMessage(title: String, message: String, onRetry: () -> Unit) {
    val c = AdtTheme.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Txt(title, AdtTheme.type.headline)
        Spacer(Modifier.height(6.dp))
        Txt(message, AdtTheme.type.body, color = c.muted)
        Spacer(Modifier.height(14.dp))
        Txt("TEKRAR DENE", AdtTheme.type.label, color = c.accent, modifier = Modifier.press(onClick = onRetry).padding(vertical = 8.dp))
    }
}

/** Alt menünün üstündeki küçük oynatıcı. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val state by app.player.state.collectAsState()
    val progress by app.player.progress.collectAsState()
    val song = state.current ?: return
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    Column(
        modifier
            .fillMaxWidth()
            .background(c.paper)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy -> if (dy < -12) ui.openPlayer() }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(c.accent))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .press(label = "Oynatıcıyı aç") { ui.openPlayer() }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .graphicsLayer { translationX = offset.value; alpha = 1f - (abs(offset.value) / 600f).coerceIn(0f, 0.7f) }
                    .pointerInput(song.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offset.value < -120f -> app.player.next()
                                        offset.value > 120f -> app.player.previous()
                                    }
                                    offset.animateTo(0f)
                                }
                            },
                            onHorizontalDrag = { ch, dx -> ch.consume(); scope.launch { offset.snapTo(offset.value + dx) } },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(song.thumbnail, Modifier.size(46.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(song.title, AdtTheme.type.bodyStrong, maxLines = 1)
                    Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
                }
            }
            Box(
                Modifier.size(48.dp).background(c.ink).press(label = if (state.isPlaying) "Duraklat" else "Çal") { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                AdtIcon(if (state.isPlaying) AdtIcons.Pause else AdtIcons.Play, null, tint = c.paper, size = 22.dp)
            }
            IconBtn(AdtIcons.Next, "Sonraki", { app.player.next() }, enabled = state.hasNext)
        }
    }
}
