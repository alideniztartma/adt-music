package com.alideniz.adtmusic.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatMs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ============================================================ Plak (vinil)

@Composable
fun VinylPlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val base by s.vinylBase.flow.collectAsState()
    val rpm by s.vinylSpeed.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val accent = AdtTheme.colors.accent
    val (paper, top, bottom) = when (base) {
        "dark" -> Triple(Color(0xFF141414), Color(0xFF242424), Color(0xFF0B0B0B))
        "accent" -> Triple(lerp(accent, Color.Black, 0.55f), lerp(accent, Color.Black, 0.35f), lerp(accent, Color.Black, 0.75f))
        else -> Triple(Color(0xFF3B2416), Color(0xFF6B4329), Color(0xFF2A190F))
    }
    val colors = playerColors(AdtTheme.colors, paper, Color(0xFFF6EFE6), lerp(accent, Color.White, 0.15f), true)
    val degPerSec = 360f * (rpm.toFloatOrNull() ?: 33f) / 60f * 0.35f
    val angle = rememberSpin(bits.state.isPlaying, degPerSec)
    val arm by animateFloatAsState(if (bits.state.isPlaying) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "arm")

    StylePlayerFrame(colors, background = {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, paper, bottom))))
        if (base == "wood") {
            Canvas(Modifier.fillMaxSize()) {
                // ahşap damarları
                for (i in 0 until 40) {
                    val y = size.height * i / 40f
                    drawLine(Color.Black.copy(alpha = 0.07f + (i % 3) * 0.02f), Offset(0f, y), Offset(size.width, y + 18f * sin(i * 1.7f)), 2f)
                }
            }
        }
    }) {
        StyleTopBar(bits, "PLAK ÇALAR · $rpm DEVİR")
        Spacer(Modifier.height(8.dp))
        Crossfade(bits.ui.lyricsMode, label = "vinylLyrics", modifier = Modifier.weight(1f)) { lyrics ->
            if (lyrics) {
                LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize())
            } else {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(Modifier.size(side)) {
                        // kaide
                        Box(
                            Modifier.fillMaxSize().padding(4.dp).shadow(18.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.25f))))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        )
                        // plak
                        SongTransition(bits.song, bits.state.index, Modifier.fillMaxSize().padding(side * 0.07f)) { song ->
                            Box(Modifier.fillMaxSize().rotate(angle).press(label = if (bits.state.isPlaying) "Duraklat" else "Çal") { bits.app.player.togglePlay() }) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val r = size.minDimension / 2
                                    drawCircle(Color(0xFF0D0D0D), r)
                                    for (i in 0 until 26) {
                                        val rr = r * (0.42f + 0.56f * i / 26f)
                                        drawCircle(Color.White.copy(alpha = if (i % 4 == 0) 0.07f else 0.03f), rr, style = Stroke(1f))
                                    }
                                    // ışık yansıması
                                    rotate(-angle + 35f) {
                                        drawArc(
                                            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent)),
                                            -20f, 40f, true, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                                        )
                                    }
                                }
                                Cover(song.thumbnail, Modifier.fillMaxSize(0.38f).align(Alignment.Center), shape = CircleShape)
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD9D4CC)).align(Alignment.Center))
                            }
                        }
                        // iğne kolu
                        Canvas(Modifier.fillMaxSize()) {
                            val pivot = Offset(size.width * 0.88f, size.height * 0.10f)
                            val len = size.minDimension * 0.62f
                            val a = (86f + 28f * arm) * (PI / 180f).toFloat()
                            val end = Offset(pivot.x + len * cos(a), pivot.y + len * sin(a))
                            drawCircle(Color(0xFFBDB6AB), 16.dp.toPx(), pivot)
                            drawCircle(Color(0xFF6E675E), 7.dp.toPx(), pivot)
                            drawLine(Color(0xFFCFC8BD), pivot, end, 5.dp.toPx(), StrokeCap.Round)
                            rotate(a * 180f / PI.toFloat() + 90f, end) {
                                drawRoundRect(Color(0xFF2B2B2B), Offset(end.x - 9.dp.toPx(), end.y - 4.dp.toPx()), Size(18.dp.toPx(), 22.dp.toPx()), CornerRadius(3.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyleTitle(
                bits, AdtTheme.type.headline.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold), AdtTheme.type.body,
                colors.ink, colors.muted, Modifier.weight(1f),
            )
            StyleLike(bits, colors.ink, colors.accent)
        }
        Spacer(Modifier.height(10.dp))
        StyleSeek(bits, colors.ink, colors.ink.copy(alpha = 0.2f))
        SoundInfo(bits, colors.ink)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundKnob(AdtIcons.Previous, "Önceki", 58.dp, Color(0xFFCFC8BD), Color(0xFF2B2B2B)) { bits.app.player.previous() }
            RoundKnob(null, if (bits.state.isPlaying) "Duraklat" else "Çal", 78.dp, colors.accent, Color.White, playing = bits.state.isPlaying) { bits.app.player.togglePlay() }
            RoundKnob(AdtIcons.Next, "Sonraki", 58.dp, Color(0xFFCFC8BD), Color(0xFF2B2B2B), enabled = bits.state.hasNext || bits.state.repeatMode != Player.REPEAT_MODE_OFF) { bits.app.player.next() }
        }
        Spacer(Modifier.height(10.dp))
        UpNextLine(bits, colors.ink)
        StyleToolbar(bits, colors.ink, colors.accent, Modifier.padding(bottom = 8.dp))
    }
}

/** Metal görünümlü yuvarlak düğme (plak çalar). */
@Composable
private fun RoundKnob(
    icon: ImageVector?, desc: String, size: Dp, face: Color, tint: Color,
    enabled: Boolean = true, playing: Boolean = false, onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "knob")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = depth; scaleY = depth; alpha = if (enabled) 1f else 0.4f }
            .shadow(if (pressed) 2.dp else 10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(lerp(face, Color.White, 0.35f), face, lerp(face, Color.Black, 0.25f))))
            .press(interactionSource = source, enabled = enabled, label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) PlayPauseIcon(playing, tint, size * 0.42f) else AdtIcon(icon, desc, tint = tint, size = size * 0.4f)
    }
}

// ============================================================ Kaset

@Composable
fun CassettePlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val shellPref by s.cassetteColor.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val accent = AdtTheme.colors.accent
    val shell = when (shellPref) {
        "clear" -> Color(0xFFBFC7CC).copy(alpha = 0.55f)
        "orange" -> Color(0xFFFF7A1A)
        "blue" -> Color(0xFF2F6BFF)
        "pink" -> Color(0xFFFF4D8D)
        else -> Color(0xFF1E1E1E)
    }
    val paper = Color(0xFF1A1714)
    val colors = playerColors(AdtTheme.colors, paper, Color(0xFFF3E9DA), lerp(accent, Color(0xFFFFC14D), 0.3f), true)
    val spin = rememberSpin(bits.state.isPlaying, 210f)
    val f = bits.progress.fraction

    StylePlayerFrame(colors, background = {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A241F), paper, Color(0xFF0F0D0B)))))
        Canvas(Modifier.fillMaxSize()) {
            // halı deseni
            val step = 26.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width) { drawCircle(Color.White.copy(alpha = 0.025f), 2.dp.toPx(), Offset(x, y)); x += step }
                y += step; row++
            }
        }
    }) {
        StyleTopBar(bits, "KASETÇALAR · TARAF A")
        Spacer(Modifier.height(12.dp))
        Crossfade(bits.ui.lyricsMode, label = "tapeLyrics", modifier = Modifier.weight(1f)) { lyrics ->
            if (lyrics) LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SongTransition(bits.song, bits.state.index, Modifier.fillMaxWidth().aspectRatio(1.58f)) { song ->
                    Cassette(song.title, song.artist, song.thumbnail, shell, spin, f, colors.accent)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // sayaç
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Txt(
                    "%03d".format((bits.progress.position / 1000) % 1000),
                    AdtTheme.type.title.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                    color = Color(0xFFFFB347),
                )
            }
            Spacer(Modifier.width(12.dp))
            StyleTitle(bits, AdtTheme.type.title, AdtTheme.type.caption, colors.ink, colors.muted, Modifier.weight(1f))
            StyleLike(bits, colors.ink, colors.accent)
        }
        Spacer(Modifier.height(8.dp))
        StyleSeek(bits, colors.accent, colors.ink.copy(alpha = 0.15f))
        SoundInfo(bits, colors.ink)
        Spacer(Modifier.height(10.dp))
        // teyp tuşları
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TapeKey(AdtIcons.Previous, "Geri sar", Modifier.weight(1f)) { bits.app.player.previous() }
            TapeKey(if (bits.state.isPlaying) AdtIcons.Pause else AdtIcons.Play, if (bits.state.isPlaying) "Duraklat" else "Çal", Modifier.weight(1.4f), down = bits.state.isPlaying, accent = colors.accent) { bits.app.player.togglePlay() }
            TapeKey(AdtIcons.Next, "İleri sar", Modifier.weight(1f)) { bits.app.player.next() }
            TapeKey(AdtIcons.Shuffle, "Karıştır", Modifier.weight(1f), down = bits.state.shuffle, accent = colors.accent) { bits.app.player.toggleShuffle() }
        }
        Spacer(Modifier.height(8.dp))
        UpNextLine(bits, colors.ink)
        StyleToolbar(bits, colors.ink, colors.accent, Modifier.padding(bottom = 8.dp))
    }
}

/** Dönen makaralı kaset; sol makara biterken incelir, sağdaki kalınlaşır. */
@Composable
private fun Cassette(title: String, artist: String, thumb: String?, shell: Color, spin: Float, fraction: Float, accent: Color) {
    Box(
        Modifier.fillMaxSize().shadow(16.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(shell)
            .border(2.dp, Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            // etiket
            Column(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF4EBDC)).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("A", AdtTheme.type.headline, color = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(title, AdtTheme.type.title.copy(fontFamily = FontFamily.Cursive, fontSize = 20.sp), color = Color(0xFF1B1B1B), maxLines = 1)
                        Txt(artist, AdtTheme.type.caption, color = Color(0xFF555555), maxLines = 1)
                    }
                    Cover(thumb, Modifier.size(34.dp), shape = RoundedCornerShape(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
                Spacer(Modifier.height(6.dp))
                // makara penceresi
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val h = size.height
                    val w = size.width
                    drawRoundRect(Color(0xFF2A2A2A), Offset(w * 0.12f, 0f), Size(w * 0.76f, h), CornerRadius(h / 2))
                    val maxR = h * 0.46f
                    val minR = h * 0.2f
                    val leftR = minR + (maxR - minR) * (1f - fraction)
                    val rightR = minR + (maxR - minR) * fraction
                    val lc = Offset(w * 0.3f, h / 2)
                    val rc = Offset(w * 0.7f, h / 2)
                    drawCircle(Color(0xFF4A3426), leftR, lc)
                    drawCircle(Color(0xFF4A3426), rightR, rc)
                    listOf(lc, rc).forEach { c ->
                        drawCircle(Color(0xFFEDE6DA), h * 0.17f, c)
                        rotate(spin, c) {
                            for (k in 0 until 6) {
                                val a = k / 6f * 2 * PI.toFloat()
                                drawLine(Color(0xFF6B6B6B), c, Offset(c.x + h * 0.15f * cos(a), c.y + h * 0.15f * sin(a)), 2.dp.toPx())
                            }
                        }
                        drawCircle(Color(0xFF2A2A2A), h * 0.05f, c)
                    }
                    drawLine(Color(0xFF4A3426), Offset(lc.x, lc.y + leftR), Offset(rc.x, rc.y + rightR), 2.dp.toPx())
                }
            }
            Spacer(Modifier.height(8.dp))
            // alt kısım: vida delikleri
            Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) }
            }
        }
    }
}

@Composable
private fun TapeKey(icon: ImageVector, desc: String, modifier: Modifier, down: Boolean = false, accent: Color = Color.White, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sunk = pressed || down
    val drop by animateFloatAsState(if (sunk) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "key")
    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer { translationY = drop * 5.dp.toPx() }
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE9E4DA), Color(0xFFBDB6AB))))
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.25f * (1f - drop)), Offset(0f, size.height - 6.dp.toPx()), Size(size.width, 6.dp.toPx()))
                if (down) drawRect(accent, Offset(0f, 0f), Size(size.width, 4.dp.toPx()))
            }
            .press(interactionSource = source, label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AdtIcon(icon, desc, tint = Color(0xFF222222), size = 22.dp) }
}

// ============================================================ Cam (Spotify tarzı)

@Composable
fun GlassPlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val blurPref by s.glassBlur.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val accent = AdtTheme.colors.accent
    val colors = playerColors(AdtTheme.colors, Color(0xFF121212), Color.White, Color(0xFF1ED760).let { lerp(it, accent, 0.0f) }, true)
    val drift = rememberLoopTime(30_000)

    StylePlayerFrame(colors, background = {
        coil.compose.AsyncImage(
            model = bits.song.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { scaleX = 1.3f + 0.08f * sin(drift * 2 * PI.toFloat()); scaleY = scaleX; rotationZ = 6f * sin(drift * 2 * PI.toFloat()) }
                .blur((blurPref.coerceIn(0, 100) * 0.9f).dp),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.35f).copy(alpha = 0.75f), Color(0xE6121212), Color(0xFF121212)))))
    }) {
        StyleTopBar(bits, "ŞU AN ÇALIYOR", Color.White)
        Spacer(Modifier.height(14.dp))
        Crossfade(bits.ui.lyricsMode, label = "glassLyrics", modifier = Modifier.weight(1f)) { lyrics ->
            if (lyrics) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(lerp(accent, Color.Black, 0.25f)).padding(18.dp)) {
                    LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize())
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    SongTransition(bits.song, bits.state.index, Modifier.size(side)) { song ->
                        Cover(song.thumbnail, Modifier.fillMaxSize().shadow(28.dp, RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyleTitle(
                bits, AdtTheme.type.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), AdtTheme.type.body,
                Color.White, Color.White.copy(alpha = 0.7f), Modifier.weight(1f),
            )
            StyleLike(bits, Color.White, Color(0xFF1ED760))
        }
        Spacer(Modifier.height(10.dp))
        StyleSeek(bits, Color.White, Color.White.copy(alpha = 0.25f))
        SoundInfo(bits, Color.White)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StyleIcon(AdtIcons.Shuffle, "Karıştır", if (bits.state.shuffle) Color(0xFF1ED760) else Color.White) { bits.app.player.toggleShuffle() }
            StyleIcon(AdtIcons.Previous, "Önceki", Color.White, size = 32.dp, box = 56.dp) { bits.app.player.previous() }
            Box(
                Modifier.size(70.dp).clip(CircleShape).background(Color.White)
                    .press(label = if (bits.state.isPlaying) "Duraklat" else "Çal") { bits.app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { PlayPauseIcon(bits.state.isPlaying, Color.Black, 32.dp) }
            StyleIcon(AdtIcons.Next, "Sonraki", Color.White, size = 32.dp, box = 56.dp) { bits.app.player.next() }
            StyleIcon(
                if (bits.state.repeatMode == Player.REPEAT_MODE_ONE) AdtIcons.RepeatOne else AdtIcons.Repeat, "Tekrarla",
                if (bits.state.repeatMode != Player.REPEAT_MODE_OFF) Color(0xFF1ED760) else Color.White,
            ) { bits.app.player.cycleRepeat() }
        }
        // buzlu cam kart: sözler ya da sıradaki
        if (!bits.ui.lyricsMode) {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                LyricLinePreview(bits.song, bits.progress) { bits.ui.lyricsMode = true }
                UpNextLine(bits, Color.White)
            }
        }
        StyleToolbar(bits, Color.White, Color(0xFF1ED760), Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

// ============================================================ Neon

@Composable
fun NeonPlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val neonPref by s.neonColor.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val neon = when (neonPref) {
        "pink" -> Color(0xFFFF2BD6)
        "cyan" -> Color(0xFF00E5FF)
        "lime" -> Color(0xFFB6FF00)
        "purple" -> Color(0xFF9D4DFF)
        else -> lerp(AdtTheme.colors.accent, Color.White, 0.1f)
    }
    val second = if (neonPref == "cyan") Color(0xFFFF2BD6) else Color(0xFF00E5FF)
    val colors = playerColors(AdtTheme.colors, Color(0xFF07040F), Color.White, neon, true)
    val t = rememberLoopTime(3_000)
    val pulse = if (bits.state.isPlaying) (sin(t * 2 * PI.toFloat() * 2) + 1f) / 2f else 0.3f
    val spin = rememberSpin(bits.state.isPlaying, 40f)
    val glow = Shadow(neon, Offset.Zero, 24f)

    StylePlayerFrame(colors, background = {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07040F), Color(0xFF1A0730), Color(0xFF07040F))))
            // gün batımı güneşi
            val sunC = Offset(size.width / 2, size.height * 0.62f)
            drawCircle(Brush.verticalGradient(listOf(neon.copy(alpha = 0.55f), second.copy(alpha = 0.15f)), startY = sunC.y - size.width * 0.35f, endY = sunC.y), size.width * 0.35f, sunC)
            // ızgara zemin
            val horizon = size.height * 0.62f
            drawRect(Color(0xFF07040F), Offset(0f, horizon), Size(size.width, size.height - horizon))
            val lines = 14
            for (i in 0 until lines) {
                val p = ((i + t) / lines)
                val y = horizon + (size.height - horizon) * p * p
                drawLine(neon.copy(alpha = 0.15f + 0.5f * p), Offset(0f, y), Offset(size.width, y), 1.5f)
            }
            for (i in -10..10) {
                val x0 = size.width / 2 + i * size.width * 0.06f
                val x1 = size.width / 2 + i * size.width * 0.32f
                drawLine(neon.copy(alpha = 0.35f), Offset(x0, horizon), Offset(x1, size.height), 1.5f)
            }
        }
    }) {
        StyleTopBar(bits, "◢ NEON ◣", neon)
        Spacer(Modifier.height(8.dp))
        Crossfade(bits.ui.lyricsMode, label = "neonLyrics", modifier = Modifier.weight(1f)) { lyrics ->
            if (lyrics) LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize())
            else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight) * 0.86f
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        drawCircle(neon.copy(alpha = 0.10f + 0.15f * pulse), r)
                        rotate(spin) {
                            drawArc(
                                Brush.sweepGradient(listOf(neon, second, neon)), 0f, 300f, false,
                                topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = Size(r * 2 - 12.dp.toPx(), r * 2 - 12.dp.toPx()),
                                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        drawCircle(neon.copy(alpha = 0.25f + 0.35f * pulse), r - 18.dp.toPx(), style = Stroke(10.dp.toPx() + 8.dp.toPx() * pulse))
                    }
                    SongTransition(bits.song, bits.state.index, Modifier.fillMaxSize(0.78f)) { song ->
                        Cover(song.thumbnail, Modifier.fillMaxSize(), shape = CircleShape)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Txt(
            bits.song.title.uppercase(java.util.Locale("tr")),
            AdtTheme.type.display.copy(fontSize = 30.sp, lineHeight = 32.sp, shadow = glow, letterSpacing = 1.sp),
            color = Color.White, maxLines = 2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Txt(bits.song.artist, AdtTheme.type.bodyStrong.copy(shadow = Shadow(second, Offset.Zero, 18f)), color = second, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StyleSeek(bits, neon, Color.White.copy(alpha = 0.12f), glow = true)
        SoundInfo(bits, Color.White)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            NeonButton(AdtIcons.Previous, "Önceki", 54.dp, second) { bits.app.player.previous() }
            NeonButton(null, if (bits.state.isPlaying) "Duraklat" else "Çal", 76.dp, neon, playing = bits.state.isPlaying, pulse = pulse) { bits.app.player.togglePlay() }
            NeonButton(AdtIcons.Next, "Sonraki", 54.dp, second) { bits.app.player.next() }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { UpNextLine(bits, Color.White) }
            StyleLike(bits, Color.White, neon)
        }
        StyleToolbar(bits, Color.White, neon, Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun NeonButton(icon: ImageVector?, desc: String, size: Dp, color: Color, playing: Boolean = false, pulse: Float = 0.5f, onClick: () -> Unit) {
    Box(
        Modifier.size(size).drawBehind {
            drawCircle(color.copy(alpha = 0.18f + 0.2f * pulse), size.toPx() / 2 + 8.dp.toPx() * pulse)
            drawCircle(color, size.toPx() / 2 - 2.dp.toPx(), style = Stroke(2.5.dp.toPx()))
        }.clip(CircleShape).press(label = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) PlayPauseIcon(playing, color, size * 0.42f) else AdtIcon(icon, desc, tint = color, size = size * 0.42f)
    }
}

// ============================================================ iPod

@Composable
fun IpodPlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val bodyPref by s.ipodColor.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val (body, wheel, wheelText) = when (bodyPref) {
        "black" -> Triple(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFFBDBDBD))
        "silver" -> Triple(Color(0xFFC9CCD1), Color(0xFFEDEEF0), Color(0xFF7A7A7A))
        "red" -> Triple(Color(0xFFB3192B), Color(0xFFF2F2F2), Color(0xFF8A8A8A))
        "blue" -> Triple(Color(0xFF2E6BD8), Color(0xFFF2F2F2), Color(0xFF8A8A8A))
        else -> Triple(Color(0xFFF4F4F4), Color(0xFFFFFFFF), Color(0xFF9A9A9A))
    }
    val darkBody = bodyPref == "black" || bodyPref == "red" || bodyPref == "blue"
    val screenBg = Color(0xFFE8EEF4)
    val lcdInk = Color(0xFF1B2733)
    val colors = playerColors(AdtTheme.colors, body, lcdInk, Color(0xFF2E7CF6), false)

    StylePlayerFrame(colors, horizontalPadding = 28.dp, background = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(body, Color.White, 0.25f), body, lerp(body, Color.Black, 0.12f)))))
    }) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StyleIcon(AdtIcons.ChevronDown, "Küçült", if (darkBody) Color.White else lcdInk) { bits.ui.playerExpanded = false }
            Spacer(Modifier.weight(1f))
            StyleIcon(AdtIcons.More, "Seçenekler", if (darkBody) Color.White else lcdInk) { bits.ui.show(com.alideniz.adtmusic.ui.SheetSpec.SongOptions(bits.song, queueIndex = null)) }
        }
        // ekran
        Column(
            Modifier.fillMaxWidth().weight(1f).shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color.White, screenBg)))
                .border(3.dp, Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
        ) {
            // başlık çubuğu
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFF7F9FB), Color(0xFFC9D3DD)))).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayPauseIcon(bits.state.isPlaying, lcdInk, 12.dp)
                Txt("Şu An Çalıyor", AdtTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = lcdInk, modifier = Modifier.weight(1f), align = TextAlign.Center)
                // pil
                Box(Modifier.size(width = 22.dp, height = 10.dp).border(1.dp, lcdInk).padding(1.5.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.8f).background(Color(0xFF3FB34F)))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF9AA8B5)))
            Crossfade(bits.ui.lyricsMode, label = "ipodLyrics", modifier = Modifier.weight(1f)) { lyrics ->
                if (lyrics) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) { LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize()) }
                } else {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Txt("${bits.state.index + 1} / ${bits.state.queue.size}", AdtTheme.type.caption, color = lcdInk.copy(alpha = 0.7f))
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SongTransition(bits.song, bits.state.index, Modifier.weight(0.46f).aspectRatio(1f)) { song ->
                                Cover(song.thumbnail, Modifier.fillMaxSize().shadow(8.dp, RectangleShape), shape = RectangleShape)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(0.54f)) {
                                StyleTitle(bits, AdtTheme.type.title, AdtTheme.type.caption, lcdInk, lcdInk.copy(alpha = 0.7f))
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (bits.liked) AdtIcon(AdtIcons.HeartFilled, null, tint = Color(0xFFE5383B), size = 14.dp)
                                    if (bits.state.shuffle) AdtIcon(AdtIcons.Shuffle, null, tint = lcdInk, size = 14.dp)
                                    if (bits.state.repeatMode != Player.REPEAT_MODE_OFF) AdtIcon(AdtIcons.Repeat, null, tint = lcdInk, size = 14.dp)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // iPod ilerleme çubuğu
                        Box(
                            Modifier.fillMaxWidth().height(10.dp).border(1.dp, lcdInk.copy(alpha = 0.6f))
                                .pointerInput(bits.progress.duration) {
                                    detectTapGestures { p ->
                                        if (bits.progress.duration > 0) bits.app.player.seekTo((p.x / size.width * bits.progress.duration).toLong())
                                    }
                                }
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(bits.progress.fraction).background(Brush.verticalGradient(listOf(Color(0xFF8EC5FF), Color(0xFF2E7CF6)))))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Txt(formatMs(bits.progress.position), AdtTheme.type.caption, color = lcdInk)
                            Spacer(Modifier.weight(1f))
                            Txt("-" + formatMs((bits.progress.duration - bits.progress.position).coerceAtLeast(0)), AdtTheme.type.caption, color = lcdInk)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ClickWheel(bits, wheel, wheelText, Modifier.fillMaxWidth(0.82f).aspectRatio(1f).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        StyleToolbar(bits, if (darkBody) Color.White else lcdInk, Color(0xFF2E7CF6), Modifier.padding(bottom = 8.dp))
    }
}

/**
 * Tıklama tekeri: MENU (sözler), ⏮, ⏭, ⏯ ve orta düğme (beğen).
 * Parmağı tekerin üstünde döndürmek şarkıda ileri-geri sarar.
 */
@Composable
private fun ClickWheel(bits: PlayerBits, face: Color, text: Color, modifier: Modifier) {
    var spun by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(face, lerp(face, Color.Black, 0.06f))))
            .pointerInput(bits.progress.duration) {
                detectDragGestures(
                    onDragStart = { p -> lastAngle = atan2(p.y - size.height / 2f, p.x - size.width / 2f); spun = 0f },
                    onDragEnd = {
                        // bir tam tur ≈ 30 saniye
                        val delta = (spun / (2 * PI.toFloat()) * 30_000).toLong()
                        if (bits.progress.duration > 0 && kotlin.math.abs(delta) > 500) {
                            bits.app.player.seekTo((bits.progress.position + delta).coerceIn(0, bits.progress.duration))
                        }
                        spun = 0f
                    },
                ) { change, _ ->
                    val p = change.position
                    val a = atan2(p.y - size.height / 2f, p.x - size.width / 2f)
                    var d = a - lastAngle
                    if (d > PI) d -= (2 * PI).toFloat()
                    if (d < -PI) d += (2 * PI).toFloat()
                    spun += d
                    lastAngle = a
                }
            }
    ) {
        val label = AdtTheme.type.label.copy(fontSize = 13.sp)
        Txt("MENU", label, color = text, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).press(label = "Sözler") { bits.ui.lyricsMode = !bits.ui.lyricsMode }.padding(8.dp))
        Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp).press(label = "Önceki") { bits.app.player.previous() }.padding(10.dp)) {
            AdtIcon(AdtIcons.Previous, "Önceki", tint = text, size = 22.dp)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).press(label = "Sonraki") { bits.app.player.next() }.padding(10.dp)) {
            AdtIcon(AdtIcons.Next, "Sonraki", tint = text, size = 22.dp)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).press(label = "Çal / duraklat") { bits.app.player.togglePlay() }.padding(10.dp)) {
            Row { AdtIcon(AdtIcons.Play, null, tint = text, size = 16.dp); AdtIcon(AdtIcons.Pause, null, tint = text, size = 16.dp) }
        }
        Box(
            Modifier.align(Alignment.Center).fillMaxSize(0.38f).shadow(3.dp, CircleShape).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(face, Color.Black, 0.04f), lerp(face, Color.Black, 0.12f))))
                .press(label = if (bits.liked) "Beğenmekten vazgeç" else "Beğen") { bits.toggleLike() },
            contentAlignment = Alignment.Center,
        ) {
            if (spun != 0f) {
                val sec = (spun / (2 * PI.toFloat()) * 30).toInt()
                Txt(if (sec >= 0) "+${sec}s" else "${sec}s", AdtTheme.type.caption, color = text)
            } else if (bits.liked) AdtIcon(AdtIcons.HeartFilled, null, tint = Color(0xFFE5383B), size = 18.dp)
        }
    }
}

// ============================================================ Brütalist

@Composable
fun BrutalPlayerScreen() {
    val bits = rememberPlayerBits() ?: return
    val s = bits.app.settings
    val pref by s.brutalColor.flow.collectAsState()
    val placement by s.lyricsPlacement.flow.collectAsState()
    AutoOpenLyrics(bits.song, placement)
    val bg = when (pref) {
        "pink" -> Color(0xFFFF8FD0)
        "lime" -> Color(0xFFC6F432)
        "blue" -> Color(0xFF7DB4FF)
        "accent" -> lerp(AdtTheme.colors.accent, Color.White, 0.25f)
        else -> Color(0xFFFFE14D)
    }
    val ink = Color(0xFF0A0A0A)
    val colors = playerColors(AdtTheme.colors, bg, ink, ink, false)
    val tr = java.util.Locale("tr")

    StylePlayerFrame(colors, horizontalPadding = 18.dp, background = {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(ink.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(ink.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrutalBox(Modifier.size(44.dp), onClick = { bits.ui.playerExpanded = false }, label = "Küçült") { AdtIcon(AdtIcons.ChevronDown, null, tint = ink, size = 22.dp) }
            Txt(
                "ÇALIYOR №${bits.state.index + 1}", AdtTheme.type.label.copy(fontSize = 14.sp), color = ink,
                modifier = Modifier.weight(1f), align = TextAlign.Center,
            )
            BrutalBox(Modifier.size(44.dp), onClick = { bits.ui.show(com.alideniz.adtmusic.ui.SheetSpec.SongOptions(bits.song, queueIndex = null)) }, label = "Seçenekler") {
                AdtIcon(AdtIcons.More, null, tint = ink, size = 22.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Crossfade(bits.ui.lyricsMode, label = "brutalLyrics", modifier = Modifier.weight(1f)) { lyrics ->
            if (lyrics) {
                BrutalBox(Modifier.fillMaxSize(), fill = Color.White) { Box(Modifier.fillMaxSize().padding(16.dp)) { LyricsPane(bits.song, bits.progress, Modifier.fillMaxSize()) } }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight) - 8.dp
                    Box(Modifier.size(side)) {
                        Box(Modifier.fillMaxSize().offset(8.dp, 8.dp).background(ink))
                        SongTransition(bits.song, bits.state.index, Modifier.fillMaxSize()) { song ->
                            Cover(song.thumbnail, Modifier.fillMaxSize().border(4.dp, ink), shape = RectangleShape)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).offset((-12).dp, 12.dp).rotate(8f)
                                .background(ink).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Txt(if (bits.state.isPlaying) "▶ ÇALIYOR" else "❚❚ DURDU", AdtTheme.type.label, color = bg) }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Txt(
            bits.song.title.uppercase(tr),
            AdtTheme.type.hero.copy(fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
            color = ink, maxLines = 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(ink).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Txt(bits.song.artist.uppercase(tr), AdtTheme.type.bodyStrong, color = bg, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            StyleLike(bits, ink, Color(0xFFE5383B))
        }
        Spacer(Modifier.height(8.dp))
        StyleSeek(bits, ink, ink.copy(alpha = 0.2f), styleOverride = "thick")
        SoundInfo(bits, ink)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrutalBox(Modifier.weight(1f).fillMaxHeight(), onClick = { bits.app.player.previous() }, label = "Önceki") { AdtIcon(AdtIcons.Previous, null, tint = ink, size = 26.dp) }
            BrutalBox(Modifier.weight(1.6f).fillMaxHeight(), fill = ink, onClick = { bits.app.player.togglePlay() }, label = "Çal / duraklat") { PlayPauseIcon(bits.state.isPlaying, bg, 30.dp) }
            BrutalBox(Modifier.weight(1f).fillMaxHeight(), onClick = { bits.app.player.next() }, label = "Sonraki") { AdtIcon(AdtIcons.Next, null, tint = ink, size = 26.dp) }
        }
        Spacer(Modifier.height(8.dp))
        UpNextLine(bits, ink)
        StyleToolbar(bits, ink, ink, Modifier.padding(bottom = 8.dp))
    }
}

/** Kalın çerçeveli, sert gölgeli kutu; basınca gölgenin üstüne iner. */
@Composable
private fun BrutalBox(
    modifier: Modifier,
    fill: Color = Color.White,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val ink = Color(0xFF0A0A0A)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val off by animateFloatAsState(if (pressed) 0f else 1f, tween(90), label = "brutal")
    Box(modifier) {
        Box(Modifier.fillMaxSize().offset(5.dp, 5.dp).background(ink))
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = (1f - off) * 5.dp.toPx(); translationY = (1f - off) * 5.dp.toPx() }
                .background(fill).border(3.dp, ink)
                .then(if (onClick != null) Modifier.press(interactionSource = source, label = label, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
