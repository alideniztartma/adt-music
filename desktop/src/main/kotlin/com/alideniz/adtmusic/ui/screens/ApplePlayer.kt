package com.alideniz.adtmusic.ui.screens

import com.alideniz.adtmusic.platform.Context
import com.alideniz.adtmusic.platform.ColorUtils
import com.alideniz.adtmusic.platform.LruCache
import com.alideniz.adtmusic.platform.Palette
import com.alideniz.adtmusic.platform.loadPixels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.alideniz.adtmusic.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alideniz.adtmusic.playback.Player
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.playback.PlayerUiState
import com.alideniz.adtmusic.playback.Progress
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtColors
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.ui.theme.LocalAdtColors
import com.alideniz.adtmusic.util.formatMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============================================================ kapak renkleri ve hareketli zemin

private object CoverColors {
    private val cache = LruCache<String, IntArray>(64)

    /** Kapaktan 4 renk: koyu zemin + 3 canlı leke. */
    suspend fun of(context: Context, url: String?): IntArray? {
        if (url.isNullOrBlank()) return null
        cache.get(url)?.let { return it }
        val bmp = loadPixels(url, 120) ?: return null
        val out = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(24).generate()
            val dominant = p.dominantSwatch?.rgb ?: 0xFF333333.toInt()
            fun pick(vararg s: Palette.Swatch?) = s.firstOrNull { it != null }?.rgb ?: dominant
            val base = darken(pick(p.darkMutedSwatch, p.darkVibrantSwatch, p.dominantSwatch), 0.55f)
            intArrayOf(
                base,
                pick(p.vibrantSwatch, p.lightVibrantSwatch, p.dominantSwatch),
                pick(p.mutedSwatch, p.darkVibrantSwatch, p.dominantSwatch),
                pick(p.lightVibrantSwatch, p.lightMutedSwatch, p.vibrantSwatch),
            )
        }
        cache.put(url, out)
        return out
    }

    private fun darken(rgb: Int, amount: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hsl[2] = (hsl[2] * amount).coerceIn(0.06f, 0.32f)
        return ColorUtils.HSLToColor(hsl)
    }
}

/**
 * Kapağın renklerinden oluşan, yavaşça dalgalanan zemin (Apple Music tarzı).
 * [scrim] üstüne çekilen karartma/aydınlatma katmanıdır.
 */
@Composable
fun MeshBackground(url: String?, scrim: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors by produceState<IntArray?>(null, url) {
        value = runCatching { CoverColors.of(context, url) }.getOrNull() ?: value
    }
    val fallback = intArrayOf(0xFF1C1A2E.toInt(), 0xFFFF5A1F.toInt(), 0xFF5B2A86.toInt(), 0xFFB5453A.toInt())
    val cs = colors ?: fallback
    val c0 by animateColorAsState(Color(cs[0]), tween(900), label = "m0")
    val c1 by animateColorAsState(Color(cs[1]), tween(900), label = "m1")
    val c2 by animateColorAsState(Color(cs[2]), tween(900), label = "m2")
    val c3 by animateColorAsState(Color(cs[3]), tween(900), label = "m3")
    val still = AdtTheme.reduceMotion
    val t = if (still) null else rememberInfiniteTransition(label = "mesh").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "meshT",
    )
    Canvas(modifier.fillMaxSize()) {
        val a = t?.value ?: 0.8f
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.75f
        drawRect(c0)
        fun blob(color: Color, cx: Float, cy: Float, rad: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.45f), Color.Transparent),
                    center = Offset(cx, cy), radius = rad,
                ),
                radius = rad, center = Offset(cx, cy),
            )
        }
        blob(c1, w * (0.25f + 0.2f * cos(a)), h * (0.22f + 0.12f * sin(a * 2)), r, 0.85f)
        blob(c2, w * (0.8f + 0.18f * sin(a + 1.3f)), h * (0.55f + 0.15f * cos(a + 0.7f)), r * 0.9f, 0.8f)
        blob(c3, w * (0.35f + 0.25f * sin(a * 0.5f + 2f)), h * (0.88f + 0.08f * cos(a * 1.5f)), r * 0.8f, 0.7f)
        drawRect(scrim)
    }
}

// ============================================================ Apple Music tarzı oynatıcı

private fun appleColors(base: AdtColors) = base.copy(
    paper = Color(0xFF151515),
    surface = Color.White.copy(alpha = 0.10f),
    ink = Color.White,
    muted = Color.White.copy(alpha = 0.66f),
    faint = Color.White.copy(alpha = 0.40f),
    line = Color.White.copy(alpha = 0.18f),
    accent = Color.White,
    onAccent = Color(0xFF111111),
    isDark = true,
)

@Composable
fun ApplePlayerScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val state by app.player.state.collectAsState()
    val progress by app.player.progress.collectAsState()
    val song = state.current ?: return
    val likedIds by app.likedIds.collectAsState()
    val sleep by app.sleepTimer.remaining.collectAsState()
    val coverSize by app.settings.coverSize.flow.collectAsState()
    val placement by app.settings.lyricsPlacement.flow.collectAsState()
    val volumeBar by app.settings.playerVolumeBar.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val liked = song.id in likedIds
    val soundChanged = abs(state.speed - 1f) > 0.001f || abs(state.semitones) > 0.05f
    val dismissOffset = remember { Animatable(0f) }
    val colors = appleColors(AdtTheme.colors)

    AutoOpenLyrics(song, placement)

    CompositionLocalProvider(LocalAdtColors provides colors) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dismissOffset.value }
                .clip(RoundedCornerShape(if (dismissOffset.value > 1f) 28.dp else 0.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dismissOffset.value > 260f) {
                                    ui.playerExpanded = false
                                    dismissOffset.snapTo(0f)
                                } else dismissOffset.animateTo(0f, spring(dampingRatio = 0.8f))
                            }
                        },
                        onDragCancel = { scope.launch { dismissOffset.animateTo(0f) } },
                        onVerticalDrag = { _, dy ->
                            scope.launch { dismissOffset.snapTo((dismissOffset.value + dy).coerceAtLeast(0f)) }
                        },
                    )
                }
        ) {
            MeshBackground(song.thumbnail, Color.Black.copy(alpha = 0.32f))
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp),
            ) {
                // ---- tutamaç
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppleIcon(AdtIcons.Comment, "Yorumlar", 22.dp) { ui.show(SheetSpec.Comments(song)) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .press(label = "Küçült") { ui.playerExpanded = false }
                                .padding(12.dp)
                                .size(width = 38.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.45f))
                        )
                    }
                    AppleIcon(AdtIcons.More, "Seçenekler", 22.dp) { ui.show(SheetSpec.SongOptions(song, queueIndex = null)) }
                }
                Spacer(Modifier.height(10.dp))

                Crossfade(ui.lyricsMode, label = "appleLyrics", modifier = Modifier.weight(1f)) { lyricsMode ->
                    if (lyricsMode) {
                        Column(Modifier.fillMaxSize()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(
                                    song.thumbnail,
                                    Modifier.size(58.dp).shadow(10.dp, RoundedCornerShape(8.dp)),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(song.title, AdtTheme.type.title, maxLines = 1, modifier = Modifier.basicMarquee())
                                    Txt(song.artist, AdtTheme.type.body, color = Color.White.copy(alpha = 0.66f), maxLines = 1)
                                }
                                LikeCircle(liked) { toggleLike(app, scope, song, liked) }
                            }
                            Spacer(Modifier.height(8.dp))
                            LyricsPane(song, progress, Modifier.weight(1f), animationOverride = "apple")
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            if (coverSize == "none") {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomStart) {
                                    AnimatedContent(
                                        song,
                                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                                        label = "appleBigTitle",
                                    ) { s ->
                                        Column {
                                            Txt(
                                                s.title,
                                                AdtTheme.type.hero.copy(
                                                    fontSize = if (s.title.length > 24) 46.sp else 62.sp,
                                                    lineHeight = if (s.title.length > 24) 48.sp else 62.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                ),
                                                maxLines = 5,
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Txt(s.artist, AdtTheme.type.headline, color = Color.White.copy(alpha = 0.66f), maxLines = 2)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            } else {
                                AppleCover(
                                    song, state,
                                    fraction = when (coverSize) { "small" -> 0.62f; "medium" -> 0.82f; else -> 1f },
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    onLike = { if (!liked) toggleLike(app, scope, song, false) },
                                )
                                Spacer(Modifier.height(26.dp))
                            }
                            // ---- başlık + beğen + seçenekler
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (coverSize != "none") {
                                        Txt(
                                            song.title,
                                            AdtTheme.type.headline.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(),
                                        )
                                        Txt(
                                            song.artist,
                                            AdtTheme.type.body.copy(fontSize = 18.sp),
                                            color = Color.White.copy(alpha = 0.66f),
                                            maxLines = 1,
                                            modifier = Modifier.press(label = "Sanatçıya git") {
                                                ui.navigate(Routes.artist(song.artist, null))
                                            },
                                        )
                                    }
                                }
                                LikeCircle(liked) { toggleLike(app, scope, song, liked) }
                            }
                            if (placement == "preview") {
                                Spacer(Modifier.height(10.dp))
                                LyricLinePreview(song, progress) { ui.lyricsMode = true }
                            }
                        }
                    }
                }

                // ---- ilerleme
                Spacer(Modifier.height(18.dp))
                AppleSeek(progress, onSeek = { app.player.seekTo(it) })
                if (soundChanged || state.isBuffering) {
                    val st = kotlin.math.round(state.semitones).toInt()
                    Txt(
                        if (state.isBuffering) "YÜKLENİYOR" else listOfNotNull(
                            if (state.speed != 1f) "%.2fx".format(state.speed) else null,
                            if (st != 0) (if (st > 0) "+$st ton" else "$st ton") else null,
                        ).joinToString(" · "),
                        AdtTheme.type.label, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).press { ui.show(SheetSpec.Speed) },
                        align = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                // ---- kontroller
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleControl(AdtIcons.Previous, "Önceki", 38.dp) { app.player.previous() }
                    AppleControl(null, if (state.isPlaying) "Duraklat" else "Çal", 54.dp, playing = state.isPlaying) { app.player.togglePlay() }
                    AppleControl(
                        AdtIcons.Next, "Sonraki", 38.dp,
                        enabled = state.hasNext || state.repeatMode != Player.REPEAT_MODE_OFF,
                    ) { app.player.next() }
                }

                // ---- ses
                if (volumeBar) {
                    Spacer(Modifier.height(16.dp))
                    AppleVolume()
                }

                // ---- alt çubuk
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleToggle(AdtIcons.Lyrics, "Sözler", ui.lyricsMode) { ui.lyricsMode = !ui.lyricsMode }
                    AppleToggle(AdtIcons.Shuffle, "Karıştır", state.shuffle) { app.player.toggleShuffle() }
                    AppleToggle(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) AdtIcons.RepeatOne else AdtIcons.Repeat,
                        "Tekrarla", state.repeatMode != Player.REPEAT_MODE_OFF,
                    ) { app.player.cycleRepeat() }
                    AppleToggle(AdtIcons.Speed, "Hız ve ton", soundChanged) { ui.show(SheetSpec.Speed) }
                    AppleToggle(AdtIcons.Moon, "Uyku zamanlayıcısı", sleep != null) { ui.show(SheetSpec.SleepTimer) }
                    AppleToggle(AdtIcons.Queue, "Sıra", false) { ui.show(SheetSpec.Queue) }
                }
            }
        }
    }
}

private fun toggleLike(app: com.alideniz.adtmusic.AppContainer, scope: kotlinx.coroutines.CoroutineScope, song: Song, liked: Boolean) {
    scope.launch {
        app.dao.ensureSong(song)
        app.dao.setLiked(song.id, if (liked) null else System.currentTimeMillis())
    }
}

/** Çalarken tam boy, duraklayınca hafifçe küçülen yuvarlak köşeli kapak. */
@Composable
private fun AppleCover(song: Song, state: PlayerUiState, fraction: Float, modifier: Modifier, onLike: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val still = AdtTheme.reduceMotion
    val scale by animateFloatAsState(
        if (state.isPlaying || still) 1f else 0.8f,
        spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "appleCoverScale",
    )
    val shadow by animateDpAsState(if (state.isPlaying) 28.dp else 10.dp, tween(400), label = "appleShadow")
    val offset = remember { Animatable(0f) }
    var heart by remember { mutableIntStateOf(0) }
    val heartAlpha = remember { Animatable(0f) }
    LaunchedEffect(heart) {
        if (heart > 0) { heartAlpha.snapTo(1f); heartAlpha.animateTo(0f, tween(700)) }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * fraction
        val shape = RoundedCornerShape(side * 0.035f + 4.dp)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.value
                    alpha = 1f - (abs(offset.value) / 900f).coerceIn(0f, 0.6f)
                }
                .shadow(shadow, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                when {
                                    v < -160f -> { offset.animateTo(-1200f, tween(160)); app.player.next(); offset.snapTo(0f) }
                                    v > 160f -> { offset.animateTo(1200f, tween(160)); app.player.previous(); offset.snapTo(0f) }
                                    else -> offset.animateTo(0f, spring(dampingRatio = 0.7f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, dx -> change.consume(); scope.launch { offset.snapTo(offset.value + dx) } },
                    )
                }
                .pointerInput(song.id) {
                    detectTapGestures(
                        onTap = { app.player.togglePlay() },
                        onDoubleTap = { onLike(); heart++ },
                    )
                },
        ) {
            Cover(song.thumbnail, Modifier.fillMaxSize(), shape = shape, contentDescription = song.title)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = heartAlpha.value }, contentAlignment = Alignment.Center) {
                AdtIcon(AdtIcons.HeartFilled, null, tint = Color.White, size = 96.dp)
            }
        }
    }
}

@Composable
private fun LikeCircle(liked: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            scale.animateTo(1.3f, spring(dampingRatio = 0.5f, stiffness = 800f))
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .press(label = if (liked) "Beğenmekten vazgeç" else "Beğen", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AdtIcon(if (liked) AdtIcons.HeartFilled else AdtIcons.Heart, null, tint = Color.White, size = 19.dp)
    }
}

@Composable
private fun AppleIcon(icon: ImageVector, desc: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).press(label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AdtIcon(icon, desc, tint = Color.White.copy(alpha = 0.85f), size = size) }
}

@Composable
private fun AppleToggle(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent, tween(200), label = "atBg")
    val fg by animateColorAsState(if (active) Color(0xFF111111) else Color.White.copy(alpha = 0.72f), tween(200), label = "atFg")
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg).press(label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AdtIcon(icon, desc, tint = fg, size = 20.dp) }
}

/** Büyük, çerçevesiz kontrol; basınca küçülüp yaylanarak geri gelir. */
@Composable
private fun AppleControl(
    icon: ImageVector?,
    desc: String,
    size: Dp,
    enabled: Boolean = true,
    playing: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "acScale")
    val halo by animateFloatAsState(if (pressed) 0.16f else 0f, tween(150), label = "acHalo")
    Box(
        Modifier
            .size(size + 34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = halo))
            .press(interactionSource = source, enabled = enabled, label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
        if (icon == null) PlayPauseIcon(playing, tint, size) else AdtIcon(icon, desc, tint = tint, size = size)
    }
}

/** İnce, sürüklerken kalınlaşan ilerleme çubuğu. */
@Composable
private fun AppleSeek(progress: Progress, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) dragFraction else progress.fraction
    val height by animateDpAsState(if (dragging) 12.dp else 6.dp, spring(dampingRatio = 0.7f), label = "seekH")
    val buffered = if (progress.duration > 0) progress.buffered.toFloat() / progress.duration else 0f
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(progress.duration) {
                    detectTapGestures { pos ->
                        if (progress.duration > 0) onSeek(((pos.x / size.width).coerceIn(0f, 1f) * progress.duration).toLong())
                    }
                }
                .pointerInput(progress.duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> dragging = true; dragFraction = (pos.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            if (progress.duration > 0) onSeek((dragFraction * progress.duration).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                        .background(Color.White.copy(alpha = if (dragging) 1f else 0.85f))
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging) (dragFraction * progress.duration).toLong() else progress.position
            Txt(formatMs(shown), AdtTheme.type.caption, color = Color.White.copy(alpha = if (dragging) 0.95f else 0.55f))
            Spacer(Modifier.weight(1f))
            Txt(
                "-" + formatMs((progress.duration - shown).coerceAtLeast(0)),
                AdtTheme.type.caption, color = Color.White.copy(alpha = if (dragging) 0.95f else 0.55f),
            )
        }
    }
}

/** Cihaz sesi (müzik akışı) çubuğu. */
@Composable
private fun AppleVolume() {
    val player = com.alideniz.adtmusic.appContainer.player
    val appVolume by player.volume.collectAsState()
    var level by remember { mutableFloatStateOf(appVolume) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(appVolume) { if (!dragging) level = appVolume }
    val height by animateDpAsState(if (dragging) 12.dp else 6.dp, spring(dampingRatio = 0.7f), label = "volH")
    fun set(f: Float) {
        level = f.coerceIn(0f, 1f)
        player.setVolume(level)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AdtIcon(AdtIcons.VolumeLow, "Sesi kıs", tint = Color.White.copy(alpha = 0.6f), size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) { detectTapGestures { pos -> set(pos.x / size.width) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ -> change.consume(); set(change.position.x / size.width) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(level).fillMaxHeight().background(Color.White.copy(alpha = if (dragging) 1f else 0.85f)))
            }
        }
        Spacer(Modifier.width(10.dp))
        AdtIcon(AdtIcons.Volume, "Sesi aç", tint = Color.White.copy(alpha = 0.6f), size = 18.dp)
    }
}

// ============================================================ ortak: canlı söz satırı ve otomatik açma

/** Kapak altında, çalan söz satırı ve sıradaki satır. Dokununca sözler açılır. */
@Composable
fun LyricLinePreview(song: Song, progress: Progress, onOpen: () -> Unit) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val lyrics by produceState<com.alideniz.adtmusic.data.Lyrics?>(null, song.id) {
        value = null // önceki şarkının sözleri yeni şarkıda kaymasın
        value = runCatching { app.lyrics.lyricsFor(song) }.getOrNull()
    }
    val l = lyrics ?: return
    if (!l.synced || l.lines.isEmpty()) return
    val idx = l.lines.indexOfLast { it.timeMs <= progress.position + 250 }
    val current = l.lines.getOrNull(idx)?.text?.ifBlank { "♪" } ?: "♪"
    val next = l.lines.getOrNull(idx + 1)?.text?.ifBlank { "♪" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AdtTheme.shape)
            .press(label = "Sözleri aç", onClick = onOpen)
            .padding(vertical = 6.dp),
    ) {
        AnimatedContent(
            current,
            transitionSpec = {
                (fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(tween(260)) { it / 2 }) togetherWith
                    (fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(tween(180)) { -it / 2 })
            },
            label = "lyPreview",
        ) { line ->
            Txt(line, AdtTheme.type.title.copy(fontWeight = FontWeight.ExtraBold), color = c.ink, maxLines = 2)
        }
        if (next != null) {
            Txt(next, AdtTheme.type.body, color = c.faint, maxLines = 1)
        }
    }
}

/** "Otomatik aç" seçiliyse: senkron sözü olan şarkıda sözler kendiliğinden açılır (her şarkıda bir kez). */
@Composable
fun AutoOpenLyrics(song: Song, placement: String) {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    LaunchedEffect(song.id, placement) {
        if (placement != "auto") return@LaunchedEffect
        val l = runCatching { app.lyrics.lyricsFor(song) }.getOrNull()
        if (l != null && l.synced && l.lines.isNotEmpty()) ui.lyricsMode = true
    }
}

