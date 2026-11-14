package com.alideniz.adtmusic.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.clip
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
import com.alideniz.adtmusic.ui.components.DownloadButton
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
    val songMomentsAll by remember(song.id) { app.dao.momentsForSong(song.id) }.collectAsState(initial = emptyList())
    val nowMs = System.currentTimeMillis()
    val songMoments = songMomentsAll.filter { it.revealAt == null || it.revealed || it.revealAt <= nowMs }
    // Çalarken bir anının saniyesine gelince notunu kısaca göster.
    val shownMoments = remember(song.id) { mutableSetOf<Long>() }
    LaunchedEffect(progress.position / 1000, song.id) {
        val pos = progress.position
        songMoments.firstOrNull { it.id !in shownMoments && pos in it.positionMs..(it.positionMs + 1500) }?.let { m ->
            shownMoments += m.id
            app.toast("✦ ${moodOf(m.mood).label}" + if (m.note.isNotBlank()) " · ${m.note}" else "")
        }
    }
    val bgStyle by app.settings.playerBackground.flow.collectAsState()
    val bg by animateColorAsState(
        if (bgStyle == "plain") c.paper else c.tint(if (c.isDark) 0.14f else 0.08f),
        tween(700), label = "bg",
    )
    val heartScale = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            heartScale.animateTo(1.35f, spring(dampingRatio = 0.5f, stiffness = 800f))
            heartScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }

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
        if (bgStyle == "blur") {
            coil.compose.AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).graphicsLayer { alpha = 0.55f },
            )
            Box(Modifier.fillMaxSize().background(c.paper.copy(alpha = if (c.isDark) 0.55f else 0.62f)))
        }
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
                        PlayerVisualizer(state.isPlaying)
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
            MomentMarkers(songMoments, progress.duration) { m ->
                app.player.seekTo(m.positionMs)
                app.toast("✦ ${moodOf(m.mood).label}" + if (m.note.isNotBlank()) " · ${m.note}" else "")
            }
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
                        .clip(AdtTheme.shape)
                        .background(c.accent)
                        .press(label = if (state.isPlaying) "Duraklat" else "Çal") { app.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(state.isPlaying, c.onAccent, 32.dp)
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
                    modifier = Modifier.graphicsLayer { scaleX = heartScale.value; scaleY = heartScale.value },
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
                IconBtn(AdtIcons.Shuffle, "Karıştır", { app.player.toggleShuffle() }, tint = if (state.shuffle) c.accent else c.ink, iconSize = 20.dp, size = 40.dp)
                IconBtn(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) AdtIcons.RepeatOne else AdtIcons.Repeat, "Tekrarla",
                    { app.player.cycleRepeat() },
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) c.accent else c.ink, iconSize = 20.dp, size = 40.dp,
                )
                IconBtn(AdtIcons.Lyrics, "Sözler", { ui.lyricsMode = !ui.lyricsMode }, tint = if (ui.lyricsMode) c.accent else c.ink, iconSize = 20.dp, size = 40.dp)
                IconBtn(AdtIcons.Queue, "Sıra", { ui.show(SheetSpec.Queue) }, iconSize = 20.dp, size = 40.dp)
                DownloadButton(dl, {
                    when (dl?.state) {
                        DlState.DONE -> app.toast("Bu şarkı indirildi ✓")
                        DlState.DOWNLOADING, DlState.QUEUED -> app.toast("İndiriliyor… %${((dl?.progress ?: 0f) * 100).toInt()}")
                        else -> { app.downloads.download(song); app.toast("İndirme başladı") }
                    }
                }, iconSize = 20.dp, size = 40.dp)
                IconBtn(AdtIcons.Moment, "Anı oluştur", { ui.show(SheetSpec.AddMoment(song, progress.position)) }, tint = if (songMoments.isNotEmpty()) c.accent else c.ink, iconSize = 20.dp, size = 40.dp)
                Box(contentAlignment = Alignment.Center) {
                    IconBtn(AdtIcons.Speed, "Hız ve ton", { ui.show(SheetSpec.Speed) }, tint = if (soundChanged) c.accent else c.ink, iconSize = 20.dp, size = 40.dp)
                }
                IconBtn(AdtIcons.Moon, "Uyku zamanlayıcısı", { ui.show(SheetSpec.SleepTimer) }, tint = if (sleep != null) c.accent else c.ink, iconSize = 20.dp, size = 40.dp)
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

    // Kapak animasyonu: nefes (yavaş büyüyüp küçülme) ya da ritme göre atım
    val anim by app.settings.coverAnimation.flow.collectAsState()
    val mode = if (AdtTheme.reduceMotion) "off" else anim
    val amount by animateFloatAsState(
        if (state.isPlaying && mode != "off") 1f else 0f, tween(600), label = "coverAmt",
    )
    val beatMode = mode == "beat" && app.spectrum.hasPermission()
    if (beatMode) {
        DisposableEffect(Unit) {
            app.spectrum.acquire()
            onDispose { app.spectrum.release() }
        }
    }
    val beatActive by app.spectrum.active.collectAsState()
    val level = app.spectrum.level.collectAsState()
    val breath = if (mode != "off") {
        rememberInfiniteTransition(label = "breath").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathV",
        )
    } else null
    val useBeat = beatMode && beatActive

    BoxWithConstraints(modifier, contentAlignment = Alignment.TopStart) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    val pulse = when {
                        amount == 0f -> 0f
                        useBeat -> level.value
                        else -> breath?.value ?: 0f
                    }
                    val sc = 1f - 0.035f * amount + 0.035f * amount * pulse
                    scaleX = sc; scaleY = sc
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

/** Kapağın altında çalan müziğe göre hareket eden çubuklar ya da dalga. */
@Composable
private fun PlayerVisualizer(playing: Boolean) {
    val app = LocalContext.current.container
    val style by app.settings.visualizerStyle.flow.collectAsState()
    val c = AdtTheme.colors
    if (style == "off" || !app.spectrum.hasPermission()) {
        Spacer(Modifier.height(24.dp))
        return
    }
    DisposableEffect(Unit) {
        app.spectrum.acquire()
        onDispose { app.spectrum.release() }
    }
    val bands = app.spectrum.bands.collectAsState()
    val shown by animateFloatAsState(if (playing) 1f else 0.15f, tween(400), label = "vizAmt")
    val accent = c.accent
    val faint = c.line
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).height(36.dp)
    ) {
        val b = bands.value
        val n = b.size
        if (n == 0) return@Canvas
        val h = size.height
        if (style == "wave") {
            val step = size.width / (n - 1)
            val path = androidx.compose.ui.graphics.Path()
            val fill = androidx.compose.ui.graphics.Path()
            fun y(i: Int) = h - (h * 0.08f + h * 0.92f * b[i] * shown)
            path.moveTo(0f, y(0)); fill.moveTo(0f, h); fill.lineTo(0f, y(0))
            for (i in 1 until n) {
                val x0 = (i - 1) * step; val x1 = i * step
                val cx = (x0 + x1) / 2f
                path.cubicTo(cx, y(i - 1), cx, y(i), x1, y(i))
                fill.cubicTo(cx, y(i - 1), cx, y(i), x1, y(i))
            }
            fill.lineTo(size.width, h); fill.close()
            drawPath(fill, accent.copy(alpha = 0.16f))
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
        } else {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val bh = (h * b[i] * shown).coerceAtLeast(2.dp.toPx())
                val x = i * (w + gap)
                drawRect(faint, topLeft = androidx.compose.ui.geometry.Offset(x, h - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(w, 2.dp.toPx()))
                drawRect(accent, topLeft = androidx.compose.ui.geometry.Offset(x, h - bh), size = androidx.compose.ui.geometry.Size(w, bh))
            }
        }
    }
}

/** Senkron sözler: çalan satır kayan bir dolguyla vurgulanır, satıra dokununca oraya atlar. */
@Composable
fun LyricsPane(song: Song, progress: Progress, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
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
                                modifier = if (line.text.isBlank()) Modifier else Modifier.press(
                                    label = "Söz kartı",
                                    onLongClick = { ui.show(SheetSpec.LyricCard(song, line.text)) },
                                ) {},
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                    else -> SyncedLyrics(song, data, progress, textSize)
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun SyncedLyrics(song: Song, lyrics: Lyrics, progress: Progress, textSize: Int) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val animation by app.settings.lyricsAnimation.flow.collectAsState()
    val align by app.settings.lyricsAlign.flow.collectAsState()
    val textAlign = if (align == "center") TextAlign.Center else TextAlign.Start
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
            val distance = if (currentIndex < 0) i + 1 else kotlin.math.abs(i - currentIndex)
            val baseStyle = AdtTheme.type.headline.copy(
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.15f).sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = textAlign,
            )
            val text = line.text.ifBlank { "♪" }
            val next = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + 4000)
            val f = if (isCurrent) ((pos - line.timeMs).toFloat() / (next - line.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f) else 0f

            val targetScale = when {
                !isCurrent -> 1f
                animation == "glow" -> 1.06f
                animation == "focus" -> 1.04f
                else -> 1f
            }
            val targetAlpha = when (animation) {
                "focus" -> if (isCurrent) 1f else (1f - distance * 0.22f).coerceAtLeast(0.15f)
                "glow" -> if (isCurrent) 1f else 0.35f
                else -> 1f
            }
            val scale by animateFloatAsState(targetScale, spring(dampingRatio = 0.6f, stiffness = 300f), label = "lyScale")
            val alpha by animateFloatAsState(targetAlpha, tween(350), label = "lyAlpha")
            val blur = if (animation == "focus" && !isCurrent && Build.VERSION.SDK_INT >= 31) (distance * 1.2f).coerceAtMost(4f).dp else 0.dp

            val modifier = Modifier
                .fillMaxWidth()
                .press(
                    label = "Buraya atla",
                    onLongClick = if (line.text.isBlank()) null else ({ ui.show(SheetSpec.LyricCard(song, line.text)) }),
                ) { app.player.seekTo(line.timeMs) }
                .padding(vertical = 10.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale; this.alpha = alpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (textAlign == TextAlign.Center) 0.5f else 0f, 0.5f)
                }
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)

            val pastColor = if (animation == "fill" || animation == "word") c.faint else c.muted
            when {
                isCurrent && animation == "fill" -> {
                    val brush = Brush.horizontalGradient(
                        0f to c.accent, f to c.accent, (f + 0.02f).coerceAtMost(1f) to c.ink, 1f to c.ink,
                    )
                    androidx.compose.foundation.text.BasicText(text, modifier, style = baseStyle.merge(TextStyle(brush = brush)))
                }
                isCurrent && animation == "word" -> {
                    // Kelime kelime: süre, harf sayısına göre kelimelere paylaştırılır.
                    val words = text.split(" ")
                    val total = words.sumOf { it.length + 1 }.coerceAtLeast(1)
                    val annotated = androidx.compose.ui.text.buildAnnotatedString {
                        var acc = 0
                        words.forEachIndexed { wi, w ->
                            val start = acc.toFloat() / total
                            acc += w.length + 1
                            val end = acc.toFloat() / total
                            val color = when {
                                f >= end -> c.accent
                                f > start -> androidx.compose.ui.graphics.lerp(c.ink, c.accent, ((f - start) / (end - start)).coerceIn(0f, 1f))
                                else -> c.ink
                            }
                            withStyle(androidx.compose.ui.text.SpanStyle(color = color)) { append(w) }
                            if (wi < words.size - 1) append(" ")
                        }
                    }
                    androidx.compose.foundation.text.BasicText(annotated, modifier, style = baseStyle)
                }
                isCurrent && animation == "glow" -> {
                    androidx.compose.foundation.text.BasicText(
                        text, modifier,
                        style = baseStyle.copy(
                            color = c.accent,
                            shadow = androidx.compose.ui.graphics.Shadow(c.accent.copy(alpha = 0.7f), blurRadius = 28f),
                        ),
                    )
                }
                isCurrent -> Txt(text, baseStyle, modifier = modifier, color = c.ink)
                else -> Txt(text, baseStyle, modifier = modifier, color = if (i < currentIndex) pastColor else c.muted)
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
    val dragY = remember { Animatable(0f) }
    val showProgress by app.settings.miniPlayerProgress.flow.collectAsState()

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragY.value.coerceAtLeast(0f)
                alpha = 1f - (dragY.value / 260f).coerceIn(0f, 0.85f)
            }
            .background(c.paper)
            .pointerInput(Unit) {
                // Yukarı kaydır: oynatıcıyı aç. Aşağı kaydır: çalmayı durdur ve kapat.
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                dragY.value > 120f -> {
                                    dragY.animateTo(400f, tween(160))
                                    app.player.stopAndClear()
                                    app.toast("Çalma durduruldu")
                                    dragY.snapTo(0f)
                                }
                                dragY.value < -50f -> {
                                    dragY.snapTo(0f)
                                    ui.openPlayer()
                                }
                                else -> dragY.animateTo(0f, spring(dampingRatio = 0.6f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                    onVerticalDrag = { ch, dy -> ch.consume(); scope.launch { dragY.snapTo(dragY.value + dy) } },
                )
            }
    ) {
        if (showProgress) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(c.accent))
            }
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
                Modifier.size(48.dp).clip(AdtTheme.shape).background(c.ink).press(label = if (state.isPlaying) "Duraklat" else "Çal") { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseIcon(state.isPlaying, c.paper, 22.dp)
            }
            IconBtn(AdtIcons.Next, "Sonraki", { app.player.next() }, enabled = state.hasNext)
        }
    }
}

/** Çal/Duraklat simgesi: dönerek ve büyüyerek değişir. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: androidx.compose.ui.unit.Dp) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.55f, stiffness = 600f), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(100)))
        },
        label = "playPause",
    ) { p ->
        AdtIcon(if (p) AdtIcons.Pause else AdtIcons.Play, if (p) "Duraklat" else "Çal", tint = tint, size = size)
    }
}
