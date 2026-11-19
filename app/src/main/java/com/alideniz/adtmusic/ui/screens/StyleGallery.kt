package com.alideniz.adtmusic.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Oynatıcı tarzları: anahtar, ad, kısa açıklama. */
val PLAYER_STYLES = listOf(
    Triple("minimal", "Minimal", "Kâğıt, mürekkep, keskin köşe"),
    Triple("apple", "Apple Music", "Dalgalı renkler, yuvarlak kapak"),
    Triple("vinyl", "Plak", "Dönen plak, iğne kolu"),
    Triple("cassette", "Kaset", "Dönen makaralar, teyp tuşları"),
    Triple("glass", "Cam", "Bulanık kapak, Spotify havası"),
    Triple("neon", "Neon", "Synthwave ızgara, parlayan halka"),
    Triple("ipod", "iPod", "Tıklama tekeri, LCD ekran"),
    Triple("brutal", "Brütalist", "Kalın çerçeve, sert gölge"),
)

/** Yana kaydırılan, canlı önizlemeli oynatıcı galerisi. */
@Composable
fun PlayerStyleGallery(selected: String, onSelect: (String) -> Unit) {
    val c = AdtTheme.colors
    val list = rememberLazyListState()
    val still = AdtTheme.reduceMotion
    LaunchedEffect(Unit) {
        val i = PLAYER_STYLES.indexOfFirst { it.first == selected }
        if (i > 1) list.scrollToItem(i - 1)
    }
    val t = rememberInfiniteTransition(label = "gallery").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "galleryT",
    )
    LazyRow(
        state = list,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(PLAYER_STYLES.size) { i ->
            val (key, label, desc) = PLAYER_STYLES[i]
            val on = key == selected
            Column(
                Modifier
                    .width(138.dp)
                    .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line)
                    .press(label = label) { onSelect(key) }
                    .padding(8.dp),
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.6f)) { drawPreview(key, if (still) 0.35f else t.value, c.paper, c.ink, c.accent) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(label, AdtTheme.type.bodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
                    if (on) AdtIcon(AdtIcons.Check, null, tint = c.accent, size = 16.dp)
                }
                Txt(desc, AdtTheme.type.caption, color = c.muted, maxLines = 2)
            }
        }
    }
}

/** Her oynatıcının şematik, hareketli küçük çizimi. */
private fun DrawScope.drawPreview(key: String, t: Float, paper: Color, ink: Color, accent: Color) {
    val w = size.width
    val h = size.height
    val pad = w * 0.1f
    val spin = t * 360f
    fun bars(fg: Color, y: Float) {
        drawRect(fg, Offset(pad, y), Size(w * 0.6f, h * 0.035f))
        drawRect(fg.copy(alpha = 0.5f), Offset(pad, y + h * 0.055f), Size(w * 0.4f, h * 0.025f))
    }
    fun seek(fg: Color, y: Float) {
        drawLine(fg.copy(alpha = 0.3f), Offset(pad, y), Offset(w - pad, y), 3f)
        drawLine(fg, Offset(pad, y), Offset(pad + (w - 2 * pad) * t, y), 3f)
    }
    fun controls(fg: Color, y: Float, big: Color = fg) {
        drawCircle(fg, w * 0.05f, Offset(w * 0.25f, y))
        drawCircle(big, w * 0.09f, Offset(w * 0.5f, y))
        drawCircle(fg, w * 0.05f, Offset(w * 0.75f, y))
    }
    when (key) {
        "apple" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5B2A86), Color(0xFFB5453A), Color(0xFF1C1A2E))))
            drawCircle(Color(0xFFFF7A59).copy(alpha = 0.5f), w * 0.5f, Offset(w * (0.3f + 0.2f * cos(t * 2 * PI.toFloat())), h * 0.25f))
            drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(pad, h * 0.08f), Size(w - 2 * pad, w - 2 * pad), CornerRadius(12f))
            bars(Color.White, h * 0.62f); seek(Color.White, h * 0.78f); controls(Color.White, h * 0.88f)
        }
        "vinyl" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6B4329), Color(0xFF3B2416))))
            val c = Offset(w / 2, h * 0.3f)
            val r = w * 0.38f
            rotate(spin, c) {
                drawCircle(Color(0xFF0D0D0D), r, c)
                for (i in 1..5) drawCircle(Color.White.copy(alpha = 0.06f), r * (0.45f + i * 0.1f), c, style = Stroke(1f))
                drawCircle(accent, r * 0.35f, c)
                drawCircle(Color.White, r * 0.06f, Offset(c.x + r * 0.2f, c.y))
            }
            drawLine(Color(0xFFCFC8BD), Offset(w * 0.9f, h * 0.06f), Offset(w * 0.68f, h * 0.38f), 4f)
            bars(Color(0xFFF6EFE6), h * 0.62f); seek(Color(0xFFF6EFE6), h * 0.78f); controls(Color(0xFFCFC8BD), h * 0.88f, accent)
        }
        "cassette" -> {
            drawRect(Color(0xFF1A1714))
            drawRoundRect(Color(0xFF1E1E1E), Offset(pad * 0.6f, h * 0.12f), Size(w - pad * 1.2f, h * 0.3f), CornerRadius(8f))
            drawRoundRect(Color(0xFFF4EBDC), Offset(pad, h * 0.15f), Size(w - pad * 2, h * 0.22f), CornerRadius(4f))
            drawRect(accent, Offset(pad, h * 0.2f), Size(w - pad * 2, h * 0.012f))
            listOf(0.35f, 0.65f).forEach { fx ->
                val c = Offset(w * fx, h * 0.3f)
                drawCircle(Color(0xFF4A3426), w * 0.08f, c)
                rotate(spin * 2, c) { for (k in 0 until 3) { val a = k * 2.094f; drawLine(Color.White, c, Offset(c.x + w * 0.05f * cos(a), c.y + w * 0.05f * sin(a)), 2f) } }
            }
            bars(Color(0xFFF3E9DA), h * 0.55f); seek(accent, h * 0.72f)
            for (k in 0 until 4) drawRect(Color(0xFFE9E4DA), Offset(pad + k * (w - 2 * pad) / 4f + 2f, h * 0.8f), Size((w - 2 * pad) / 4f - 4f, h * 0.1f))
        }
        "glass" -> {
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.9f), Color(0xFF121212), Color(0xFF121212))))
            drawRoundRect(accent, Offset(pad, h * 0.08f), Size(w - 2 * pad, w - 2 * pad), CornerRadius(6f))
            bars(Color.White, h * 0.6f); seek(Color.White, h * 0.74f)
            controls(Color.White, h * 0.84f)
            drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(pad, h * 0.91f), Size(w - 2 * pad, h * 0.07f), CornerRadius(8f))
        }
        "neon" -> {
            drawRect(Color(0xFF07040F))
            val horizon = h * 0.55f
            drawCircle(Color(0xFFFF2BD6).copy(alpha = 0.35f), w * 0.3f, Offset(w / 2, horizon))
            drawRect(Color(0xFF07040F), Offset(0f, horizon), Size(w, h - horizon))
            for (i in 0 until 6) { val p = ((i + t) / 6f); val y = horizon + (h - horizon) * p * p; drawLine(Color(0xFFFF2BD6).copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), 1.5f) }
            val c = Offset(w / 2, h * 0.28f)
            rotate(spin, c) { drawArc(Color(0xFF00E5FF), 0f, 280f, false, Offset(c.x - w * 0.3f, c.y - w * 0.3f), Size(w * 0.6f, w * 0.6f), style = Stroke(4f)) }
            drawCircle(accent, w * 0.22f, c)
            drawCircle(Color(0xFFFF2BD6), w * 0.09f, Offset(w * 0.5f, h * 0.86f), style = Stroke(3f))
        }
        "ipod" -> {
            drawRect(Color(0xFFF4F4F4))
            drawRoundRect(Color(0xFFE8EEF4), Offset(pad, h * 0.06f), Size(w - 2 * pad, h * 0.4f), CornerRadius(6f))
            drawRoundRect(Color.Black, Offset(pad, h * 0.06f), Size(w - 2 * pad, h * 0.4f), CornerRadius(6f), style = Stroke(3f))
            drawRect(accent, Offset(pad * 1.6f, h * 0.15f), Size(w * 0.3f, w * 0.3f))
            drawRect(Color(0xFF2E7CF6), Offset(pad * 1.6f, h * 0.38f), Size((w - pad * 3.2f) * t, h * 0.02f))
            val c = Offset(w / 2, h * 0.72f)
            drawCircle(Color.White, w * 0.34f, c)
            drawCircle(Color(0xFFDDDDDD), w * 0.34f, c, style = Stroke(2f))
            drawCircle(Color(0xFFEAEAEA), w * 0.12f, c)
        }
        "brutal" -> {
            drawRect(Color(0xFFFFE14D))
            drawRect(Color.Black, Offset(pad + 6f, h * 0.08f + 6f), Size(w - 2 * pad, w - 2 * pad))
            drawRect(accent, Offset(pad, h * 0.08f), Size(w - 2 * pad, w - 2 * pad))
            drawRect(Color.Black, Offset(pad, h * 0.08f), Size(w - 2 * pad, w - 2 * pad), style = Stroke(4f))
            drawRect(Color.Black, Offset(pad, h * 0.6f), Size(w * 0.75f, h * 0.06f))
            drawRect(Color.Black, Offset(pad, h * 0.69f), Size(w * 0.4f, h * 0.04f))
            drawRect(Color.Black, Offset(pad + 4f, h * 0.82f + 4f), Size(w - 2 * pad, h * 0.1f))
            drawRect(Color.White, Offset(pad, h * 0.82f), Size(w - 2 * pad, h * 0.1f))
            drawRect(Color.Black, Offset(pad, h * 0.82f), Size(w - 2 * pad, h * 0.1f), style = Stroke(3f))
        }
        else -> {
            drawRect(paper)
            drawRect(ink, Offset(pad, h * 0.08f), Size(w - 2 * pad, w - 2 * pad))
            bars(ink, h * 0.62f); seek(ink, h * 0.78f)
            drawRect(accent, Offset(pad, h * 0.84f), Size(w * 0.16f, w * 0.16f))
            drawRect(ink, Offset(pad + w * 0.22f, h * 0.84f), Size(w * 0.12f, w * 0.16f), style = Stroke(2f))
            drawRect(ink, Offset(pad + w * 0.4f, h * 0.84f), Size(w * 0.12f, w * 0.16f), style = Stroke(2f))
        }
    }
}
