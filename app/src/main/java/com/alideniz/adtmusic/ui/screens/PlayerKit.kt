package com.alideniz.adtmusic.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.alideniz.adtmusic.AppContainer
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.playback.PlayerUiState
import com.alideniz.adtmusic.playback.Progress
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtColors
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.ui.theme.LocalAdtColors
import com.alideniz.adtmusic.util.formatMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============================================================ yeni oynatıcıların ortak parçaları

/** Bir oynatıcının ihtiyaç duyduğu her şey tek yerde. */
class PlayerBits(
    val app: AppContainer,
    val ui: AppUi,
    val scope: CoroutineScope,
    val state: PlayerUiState,
    val progress: Progress,
    val song: Song,
    val liked: Boolean,
    val sleepOn: Boolean,
) {
    val soundChanged: Boolean get() = abs(state.speed - 1f) > 0.001f || abs(state.semitones) > 0.05f
    val upNext: Song? get() = state.queue.getOrNull(state.index + 1)

    fun toggleLike() {
        val wasLiked = liked
        scope.launch {
            app.dao.ensureSong(song)
            app.dao.setLiked(song.id, if (wasLiked) null else System.currentTimeMillis())
        }
    }
}

@Composable
fun rememberPlayerBits(): PlayerBits? {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val state by app.player.state.collectAsState()
    val progress by app.player.progress.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val sleep by app.sleepTimer.remaining.collectAsState()
    val song = state.current ?: return null
    return PlayerBits(app, ui, scope, state, progress, song, song.id in likedIds, sleep != null)
}

/** Ayarlardaki animasyon hızına göre süre (ms). */
@Composable
fun animMs(base: Int): Int {
    val app = LocalContext.current.container
    val speed by app.settings.animationSpeed.flow.collectAsState()
    if (AdtTheme.reduceMotion) return (base * 0.4f).toInt()
    return (base / speed.coerceIn(0.25f, 3f)).toInt()
}

/** Oynatıcı renklerini tek satırda kurar. */
fun playerColors(base: AdtColors, paper: Color, ink: Color, accent: Color, dark: Boolean) = base.copy(
    paper = paper,
    surface = ink.copy(alpha = 0.08f),
    ink = ink,
    muted = ink.copy(alpha = 0.66f),
    faint = ink.copy(alpha = 0.40f),
    line = ink.copy(alpha = 0.18f),
    accent = accent,
    onAccent = if (dark) Color(0xFF111111) else Color.White,
    isDark = dark,
)

/**
 * Bütün yeni oynatıcıların iskeleti: aşağı çekince kapanır, kendi renklerini sağlar,
 * arka planı ve üst kenar boşluklarını ayarlar.
 */
@Composable
fun StylePlayerFrame(
    colors: AdtColors,
    background: @Composable BoxScope.() -> Unit,
    horizontalPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val dismiss = remember { Animatable(0f) }
    CompositionLocalProvider(LocalAdtColors provides colors) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismiss.value
                    val s = 1f - (dismiss.value / 6000f).coerceIn(0f, 0.08f)
                    scaleX = s; scaleY = s
                }
                .background(colors.paper)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dismiss.value > 260f) {
                                    ui.playerExpanded = false
                                    dismiss.snapTo(0f)
                                } else dismiss.animateTo(0f, spring(dampingRatio = 0.8f))
                            }
                        },
                        onDragCancel = { scope.launch { dismiss.animateTo(0f) } },
                        onVerticalDrag = { _, dy -> scope.launch { dismiss.snapTo((dismiss.value + dy).coerceAtLeast(0f)) } },
                    )
                }
        ) {
            background()
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding),
                content = content,
            )
        }
    }
}

/** Üst çubuk: kapat · ortada etiket · seçenekler. */
@Composable
fun StyleTopBar(bits: PlayerBits, label: String, fg: Color = AdtTheme.colors.ink) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        StyleIcon(AdtIcons.ChevronDown, "Küçült", fg) { bits.ui.playerExpanded = false }
        Txt(
            label, AdtTheme.type.label, color = fg.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f), align = TextAlign.Center, maxLines = 1,
        )
        StyleIcon(AdtIcons.More, "Seçenekler", fg) { bits.ui.show(SheetSpec.SongOptions(bits.song, queueIndex = null)) }
    }
}

@Composable
fun StyleIcon(icon: ImageVector, desc: String, tint: Color, size: Dp = 22.dp, box: Dp = 42.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(box).clip(CircleShape).press(label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AdtIcon(icon, desc, tint = tint, size = size) }
}

/** Şarkı adı ve sanatçı; ayara göre kayan yazı. */
@Composable
fun StyleTitle(
    bits: PlayerBits,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    fg: Color,
    sub: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    upper: Boolean = false,
) {
    val marquee by bits.app.settings.titleMarquee.flow.collectAsState()
    val tr = java.util.Locale("tr")
    Column(modifier) {
        Txt(
            if (upper) bits.song.title.uppercase(tr) else bits.song.title, titleStyle, color = fg, maxLines = 1, align = align,
            modifier = Modifier.fillMaxWidth().then(if (marquee) Modifier.basicMarquee() else Modifier),
        )
        Txt(
            if (upper) bits.song.artist.uppercase(tr) else bits.song.artist, artistStyle, color = sub, maxLines = 1, align = align,
            modifier = Modifier.fillMaxWidth().press(label = "Sanatçıya git") {
                bits.ui.navigate(com.alideniz.adtmusic.ui.Routes.artist(bits.song.artist, null))
            },
        )
    }
}

/** "Sıradaki" satırı. */
@Composable
fun UpNextLine(bits: PlayerBits, fg: Color, modifier: Modifier = Modifier) {
    val show by bits.app.settings.showUpNext.flow.collectAsState()
    val next = bits.upNext
    if (!show || next == null) return
    Row(
        modifier.fillMaxWidth().press(label = "Sıra") { bits.ui.show(SheetSpec.Queue) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("SIRADAKİ", AdtTheme.type.label, color = fg.copy(alpha = 0.55f))
        Spacer(Modifier.width(10.dp))
        Txt("${next.title} · ${next.artist}", AdtTheme.type.caption, color = fg.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.weight(1f))
    }
}

/** Hız/ton değiştiyse ya da yükleniyorsa küçük bilgi satırı. */
@Composable
fun SoundInfo(bits: PlayerBits, fg: Color) {
    if (!bits.soundChanged && !bits.state.isBuffering) return
    val st = kotlin.math.round(bits.state.semitones).toInt()
    Txt(
        if (bits.state.isBuffering) "YÜKLENİYOR" else listOfNotNull(
            if (bits.state.speed != 1f) "%.2fx".format(bits.state.speed) else null,
            if (st != 0) (if (st > 0) "+$st ton" else "$st ton") else null,
        ).joinToString(" · "),
        AdtTheme.type.label, color = fg.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).press { bits.ui.show(SheetSpec.Speed) },
        align = TextAlign.Center,
    )
}

// ------------------------------------------------------------ ilerleme çubuğu

/**
 * Ayardaki biçimde ilerleme çubuğu: çizgi, kalın, dalga, noktalar ya da hap.
 * Dokunarak ya da sürükleyerek konum değiştirilir.
 */
@Composable
fun StyleSeek(
    bits: PlayerBits,
    fg: Color,
    track: Color,
    modifier: Modifier = Modifier,
    styleOverride: String? = null,
    showTimes: Boolean = true,
    glow: Boolean = false,
) {
    val pref by bits.app.settings.seekStyle.flow.collectAsState()
    val style = styleOverride ?: pref
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val progress = bits.progress
    val fraction = if (dragging) dragFraction else progress.fraction
    val playing = bits.state.isPlaying
    val phase = if (style == "wave" && playing && !AdtTheme.reduceMotion) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "waveP",
        ).value
    } else 0f
    val amp by animateFloatAsState(if (playing) 1f else 0.15f, tween(400), label = "waveAmp")
    val h = when (style) { "thick" -> 30.dp; "pill" -> 34.dp; "wave" -> 30.dp; else -> 26.dp }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(h)
                .pointerInput(progress.duration) {
                    detectTapGestures { pos ->
                        if (progress.duration > 0) bits.app.player.seekTo((pos.x / size.width * progress.duration).toLong())
                    }
                }
                .pointerInput(progress.duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            if (progress.duration > 0) bits.app.player.seekTo((dragFraction * progress.duration).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
        ) {
            val w = size.width
            val cy = size.height / 2
            val x = w * fraction
            val glowColor = fg.copy(alpha = 0.35f)
            when (style) {
                "thick" -> {
                    val th = if (dragging) 14.dp.toPx() else 10.dp.toPx()
                    drawRoundRect(track, Offset(0f, cy - th / 2), Size(w, th), CornerRadius(th / 2))
                    drawRoundRect(fg, Offset(0f, cy - th / 2), Size(x.coerceAtLeast(th), th), CornerRadius(th / 2))
                }
                "pill" -> {
                    val th = 22.dp.toPx()
                    drawRoundRect(track, Offset(0f, cy - th / 2), Size(w, th), CornerRadius(th / 2))
                    drawRoundRect(fg, Offset(0f, cy - th / 2), Size(x.coerceAtLeast(th), th), CornerRadius(th / 2))
                    drawCircle(track.copy(alpha = 1f), 6.dp.toPx(), Offset((x - th / 2).coerceAtLeast(th / 2), cy))
                }
                "dots" -> {
                    val n = 42
                    val step = w / (n - 1)
                    for (i in 0 until n) {
                        val px = i * step
                        val on = px <= x
                        val r = if (on) 3.2.dp.toPx() else 2.2.dp.toPx()
                        drawCircle(if (on) fg else track, r, Offset(px, cy))
                    }
                }
                "wave" -> {
                    val path = Path()
                    val a = 5.dp.toPx() * amp
                    val k = (2 * PI / 28.dp.toPx()).toFloat()
                    var px = 0f
                    path.moveTo(0f, cy)
                    while (px <= x) {
                        path.lineTo(px, cy + a * sin(px * k - phase))
                        px += 2f
                    }
                    drawLine(track, Offset(x, cy), Offset(w, cy), 3.dp.toPx(), StrokeCap.Round)
                    if (glow) drawPath(path, glowColor, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, fg, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(fg, if (dragging) 9.dp.toPx() else 6.dp.toPx(), Offset(x, cy + a * sin(x * k - phase)))
                }
                else -> {
                    val th = if (dragging) 5.dp.toPx() else 3.dp.toPx()
                    drawLine(track, Offset(0f, cy), Offset(w, cy), th, StrokeCap.Round)
                    if (glow) drawLine(glowColor, Offset(0f, cy), Offset(x, cy), th * 4, StrokeCap.Round)
                    drawLine(fg, Offset(0f, cy), Offset(x, cy), th, StrokeCap.Round)
                    drawCircle(fg, if (dragging) 9.dp.toPx() else 6.dp.toPx(), Offset(x, cy))
                }
            }
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth()) {
                Txt(formatMs(if (dragging) (dragFraction * progress.duration).toLong() else progress.position), AdtTheme.type.caption, color = fg.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Txt("-" + formatMs((progress.duration - progress.position).coerceAtLeast(0)), AdtTheme.type.caption, color = fg.copy(alpha = 0.7f))
            }
        }
    }
}

// ------------------------------------------------------------ alt araç çubuğu

/** Sözler · karıştır · tekrarla · hız · uyku · sıra. */
@Composable
fun StyleToolbar(bits: PlayerBits, fg: Color, active: Color, modifier: Modifier = Modifier) {
    val st = bits.state
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolDot(AdtIcons.Lyrics, "Sözler", bits.ui.lyricsMode, fg, active) { bits.ui.lyricsMode = !bits.ui.lyricsMode }
        ToolDot(AdtIcons.Shuffle, "Karıştır", st.shuffle, fg, active) { bits.app.player.toggleShuffle() }
        ToolDot(
            if (st.repeatMode == Player.REPEAT_MODE_ONE) AdtIcons.RepeatOne else AdtIcons.Repeat,
            "Tekrarla", st.repeatMode != Player.REPEAT_MODE_OFF, fg, active,
        ) { bits.app.player.cycleRepeat() }
        ToolDot(AdtIcons.Speed, "Hız ve ton", bits.soundChanged, fg, active) { bits.ui.show(SheetSpec.Speed) }
        ToolDot(AdtIcons.Moon, "Uyku zamanlayıcısı", bits.sleepOn, fg, active) { bits.ui.show(SheetSpec.SleepTimer) }
        ToolDot(AdtIcons.Comment, "Yorumlar", false, fg, active) { bits.ui.show(SheetSpec.Comments(bits.song)) }
        ToolDot(AdtIcons.Queue, "Sıra", false, fg, active) { bits.ui.show(SheetSpec.Queue) }
    }
}

@Composable
private fun ToolDot(icon: ImageVector, desc: String, on: Boolean, fg: Color, active: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StyleIcon(icon, desc, if (on) active else fg.copy(alpha = 0.75f), size = 20.dp, box = 40.dp, onClick = onClick)
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) active else Color.Transparent))
    }
}

// ------------------------------------------------------------ beğen

/** Beğen düğmesi; ayara göre zıplar ya da kalpler saçar. */
@Composable
fun StyleLike(bits: PlayerBits, fg: Color, accent: Color, iconSize: Dp = 26.dp, box: Dp = 46.dp) {
    val pref by bits.app.settings.likeAnimation.flow.collectAsState()
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var first by remember(bits.song.id) { mutableStateOf(true) }
    LaunchedEffect(bits.liked, bits.song.id) {
        if (first) { first = false; return@LaunchedEffect }
        if (!bits.liked || pref == "none") return@LaunchedEffect
        launch {
            scale.animateTo(1.4f, spring(dampingRatio = 0.45f, stiffness = 900f))
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
        if (pref == "burst") {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            burst.snapTo(0f)
        }
    }
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        if (burst.value > 0f) {
            Canvas(Modifier.size(box * 2)) {
                val p = burst.value
                val r = size.minDimension / 2 * (0.35f + 0.6f * p)
                for (i in 0 until 10) {
                    val ang = i / 10f * 2 * PI.toFloat()
                    val c = if (i % 2 == 0) accent else fg
                    drawCircle(
                        c.copy(alpha = 1f - p), (4.dp.toPx() * (1f - p * 0.6f)),
                        Offset(center.x + r * cos(ang), center.y + r * sin(ang)),
                    )
                }
                drawCircle(accent.copy(alpha = (1f - p) * 0.5f), r * 0.8f, center, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(box)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(CircleShape)
                .press(label = if (bits.liked) "Beğenmekten vazgeç" else "Beğen") { bits.toggleLike() },
            contentAlignment = Alignment.Center,
        ) {
            AdtIcon(if (bits.liked) AdtIcons.HeartFilled else AdtIcons.Heart, null, tint = if (bits.liked) accent else fg, size = iconSize)
        }
    }
}

// ------------------------------------------------------------ şarkı değişince kapak geçişi

/**
 * Şarkı değişince içeriği ayardaki geçişle değiştirir:
 * kaydır, soldur, yakınlaş, çevir (3B) ya da döndür.
 */
@Composable
fun SongTransition(
    song: Song,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Song) -> Unit,
) {
    val app = LocalContext.current.container
    val kind by app.settings.coverTransition.flow.collectAsState()
    val dur = animMs(520)
    var current by remember { mutableStateOf(song) }
    var previous by remember { mutableStateOf<Song?>(null) }
    var lastIndex by remember { mutableStateOf(index) }
    var dir by remember { mutableFloatStateOf(1f) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(song.id) {
        if (song.id == current.id) { current = song; return@LaunchedEffect }
        dir = if (index >= lastIndex) 1f else -1f
        lastIndex = index
        previous = current
        current = song
        p.snapTo(0f)
        p.animateTo(1f, tween(dur, easing = FastOutSlowInEasing))
        previous = null
    }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val t = p.value
        previous?.let { prev ->
            Box(Modifier.fillMaxSize().graphicsLayer { outgoing(kind, t, w, dir) }) { content(prev) }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { incoming(kind, t, w, dir, previous != null) }) { content(current) }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.outgoing(kind: String, t: Float, w: Float, dir: Float) {
    cameraDistance = 14f * density
    when (kind) {
        "fade" -> alpha = 1f - t
        "zoom" -> { alpha = 1f - t; scaleX = 1f + 0.25f * t; scaleY = scaleX }
        "flip" -> { rotationY = 180f * t * dir; alpha = if (t < 0.5f) 1f else 0f }
        "spin" -> { rotationZ = 160f * t * dir; scaleX = 1f - t; scaleY = scaleX; alpha = 1f - t }
        else -> { translationX = -w * t * dir; alpha = 1f - t * 0.6f }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.incoming(kind: String, t: Float, w: Float, dir: Float, changing: Boolean) {
    if (!changing) return
    cameraDistance = 14f * density
    when (kind) {
        "fade" -> alpha = t
        "zoom" -> { alpha = t; scaleX = 0.75f + 0.25f * t; scaleY = scaleX }
        "flip" -> { rotationY = -180f * (1f - t) * dir; alpha = if (t >= 0.5f) 1f else 0f }
        "spin" -> { rotationZ = -160f * (1f - t) * dir; scaleX = t; scaleY = t; alpha = t }
        else -> translationX = w * (1f - t) * dir
    }
}

// ------------------------------------------------------------ zaman sayacı (dönen parçalar için)

/**
 * Çalarken ilerleyen, duraklayınca duran açı (derece). Plak ve kaset makaraları
 * duraklatınca olduğu yerde kalsın diye sonsuz animasyon yerine kare kare sayılır.
 */
@Composable
fun rememberSpin(playing: Boolean, degreesPerSecond: Float): Float {
    var angle by remember { mutableFloatStateOf(0f) }
    val reduce = AdtTheme.reduceMotion
    LaunchedEffect(playing, degreesPerSecond, reduce) {
        if (!playing || reduce) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            angle = (angle + (now - last) / 1000f * degreesPerSecond) % 360f
            last = now
        }
    }
    return angle
}

/** Arka plan hareketinin hızı (ayar). 0 = durgun. */
@Composable
fun backgroundMotionFactor(): Float {
    val app = LocalContext.current.container
    val m by app.settings.backgroundMotion.flow.collectAsState()
    if (AdtTheme.reduceMotion) return 0f
    return when (m) { "off" -> 0f; "slow" -> 0.5f; "fast" -> 2f; else -> 1f }
}

/** 0..1 arasında sürekli dönen zaman (arka plan animasyonları için). */
@Composable
fun rememberLoopTime(periodMs: Int): Float {
    val f = backgroundMotionFactor()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(f, periodMs) {
        if (f == 0f) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            t = (t + (now - last) * f / periodMs) % 1f
            last = now
        }
    }
    return t
}

// ------------------------------------------------------------ parçacık efektleri

/**
 * Oynatıcının üstünde süzülen efektler: kar, yıldız, baloncuk, konfeti, kalp.
 * Dokunmaları engellemez.
 */
@Composable
fun ParticleLayer(modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val effect by app.settings.particleEffect.flow.collectAsState()
    val amount by app.settings.particleAmount.flow.collectAsState()
    if (effect == "off" || AdtTheme.reduceMotion) return
    ParticleCanvas(effect, amount, modifier)
}

/** Ayarlardaki küçük önizleme için. */
@Composable
fun ParticlePreview(effect: String) = ParticleCanvas(effect, 30, Modifier)

@Composable
fun ParticleCanvas(effect: String, amount: Int, modifier: Modifier) {
    val accent = AdtTheme.colors.accent
    val count = (amount.coerceIn(10, 100) * 0.8f).toInt()
    val seeds = remember(count) {
        val r = java.util.Random(42)
        List(count) { FloatArray(6) { r.nextFloat() } }
    }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) now = withFrameMillis { it } }
    val palette = listOf(accent, Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFF118AB2), Color(0xFFEF476F), Color.White)
    Canvas(modifier.fillMaxSize()) {
        val t = now / 1000f
        val w = size.width
        val h = size.height
        seeds.forEachIndexed { i, s ->
            val speed = 0.04f + s[1] * 0.08f
            when (effect) {
                "snow" -> {
                    val y = ((s[0] + t * speed) % 1f) * h
                    val x = (s[2] * w + sin(t * (0.6f + s[3]) + s[4] * 6f) * 22.dp.toPx())
                    drawCircle(Color.White.copy(alpha = 0.35f + s[5] * 0.5f), (1.5f + s[3] * 3.5f).dp.toPx(), Offset(x, y))
                }
                "stars" -> {
                    val tw = (sin(t * (1.2f + s[3] * 2.5f) + s[4] * 10f) + 1f) / 2f
                    val x = s[2] * w
                    val y = s[0] * h
                    val r = (1f + s[5] * 2.2f).dp.toPx()
                    val col = Color.White.copy(alpha = 0.15f + tw * 0.8f)
                    drawCircle(col, r, Offset(x, y))
                    if (s[5] > 0.75f) {
                        drawLine(col, Offset(x - r * 3, y), Offset(x + r * 3, y), 1f)
                        drawLine(col, Offset(x, y - r * 3), Offset(x, y + r * 3), 1f)
                    }
                }
                "bubbles" -> {
                    val y = h - ((s[0] + t * speed) % 1f) * (h + 60f)
                    val x = s[2] * w + sin(t * 1.3f + s[4] * 7f) * 14.dp.toPx()
                    val r = (4f + s[3] * 12f).dp.toPx()
                    drawCircle(accent.copy(alpha = 0.10f + s[5] * 0.12f), r, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = 0.35f), r, Offset(x, y), style = Stroke(1.2.dp.toPx()))
                }
                "confetti" -> {
                    val y = ((s[0] + t * (speed * 1.6f)) % 1f) * h
                    val x = s[2] * w + sin(t * 2f + s[4] * 9f) * 18.dp.toPx()
                    val col = palette[i % palette.size]
                    rotate(t * 200f * (s[3] - 0.5f) + s[5] * 360f, Offset(x, y)) {
                        drawRect(col.copy(alpha = 0.85f), Offset(x - 4.dp.toPx(), y - 2.dp.toPx()), Size(8.dp.toPx(), 4.dp.toPx()))
                    }
                }
                "hearts" -> {
                    val y = h - ((s[0] + t * speed) % 1f) * (h + 80f)
                    val x = s[2] * w + sin(t * 1.5f + s[4] * 8f) * 16.dp.toPx()
                    val r = (5f + s[3] * 8f).dp.toPx()
                    drawHeart(Offset(x, y), r, accent.copy(alpha = 0.25f + s[5] * 0.45f))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.4f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.4f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
}
