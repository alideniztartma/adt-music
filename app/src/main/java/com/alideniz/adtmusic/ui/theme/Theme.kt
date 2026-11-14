package com.alideniz.adtmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alideniz.adtmusic.R

/**
 * ADT tasarım dili — Minimal.
 * Kırık beyaz kâğıt, mürekkep siyahı, tek bir vurgu rengi; keskin köşeler,
 * büyük ve sıkışık başlıklar. Vurgu rengi çalan şarkının kapağından gelir.
 */

val AdtOrange = Color(0xFFFF5A1F)

@Immutable
data class AdtColors(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

fun lightAdtColors(accent: Color = AdtOrange) = AdtColors(
    paper = Color(0xFFF4F2EE),
    surface = Color(0xFFEAE7E1),
    ink = Color(0xFF111111),
    muted = Color(0xFF555555),
    faint = Color(0xFF8A877F),
    line = Color(0xFFD6D2CA),
    accent = accent,
    onAccent = if (accent.luminance() > 0.42f) Color(0xFF111111) else Color(0xFFF4F2EE),
    isDark = false,
)

fun darkAdtColors(accent: Color = AdtOrange, pureBlack: Boolean = false) = AdtColors(
    paper = if (pureBlack) Color.Black else Color(0xFF0F0F0F),
    surface = if (pureBlack) Color(0xFF121212) else Color(0xFF1B1B1B),
    ink = Color(0xFFF4F2EE),
    muted = Color(0xFFA6A39C),
    faint = Color(0xFF6E6C67),
    line = Color(0xFF2C2C2C),
    accent = accent,
    onAccent = if (accent.luminance() > 0.42f) Color(0xFF111111) else Color(0xFFF4F2EE),
    isDark = true,
)

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

@Immutable
data class AdtType(
    val hero: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
)

fun adtType(scale: Float = 1f): AdtType = AdtType(
    hero = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp * scale, lineHeight = 48.sp * scale, letterSpacing = (-0.035).em),
    display = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp * scale, lineHeight = 38.sp * scale, letterSpacing = (-0.03).em),
    headline = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp * scale, lineHeight = 28.sp * scale, letterSpacing = (-0.02).em),
    title = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 17.sp * scale, lineHeight = 21.sp * scale, letterSpacing = (-0.01).em),
    body = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 15.sp * scale, lineHeight = 20.sp * scale),
    bodyStrong = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 15.sp * scale, lineHeight = 20.sp * scale),
    caption = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 13.sp * scale, lineHeight = 17.sp * scale),
    label = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 12.sp * scale, lineHeight = 15.sp * scale, letterSpacing = 0.1.em),
)

val LocalAdtColors = staticCompositionLocalOf { lightAdtColors() }
val LocalAdtType = staticCompositionLocalOf { adtType() }
val LocalAdtShape = staticCompositionLocalOf<Shape> { RectangleShape }
val LocalAdtCorner = staticCompositionLocalOf { 0.dp }
/** Animasyonlar azaltıldıysa true. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object AdtTheme {
    val colors: AdtColors @Composable get() = LocalAdtColors.current
    val type: AdtType @Composable get() = LocalAdtType.current
    /** Kapak, düğme ve kutuların köşe şekli (Keskin / Yumuşak / Yuvarlak). */
    val shape: Shape @Composable get() = LocalAdtShape.current
    val corner: Dp @Composable get() = LocalAdtCorner.current
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

@Composable
fun AdtTheme(
    themeMode: String,
    accent: Color,
    pureBlack: Boolean = false,
    corner: Dp = 0.dp,
    fontScale: Float = 1f,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // Not: vurgu rengi bilerek animasyonsuz değişir; tüm arayüzün her karede
    // yeniden çizilmesini önler (akıcılık için).
    val colors = remember(dark, accent, pureBlack) {
        if (dark) darkAdtColors(accent, pureBlack) else lightAdtColors(accent)
    }
    val type = remember(fontScale) { adtType(fontScale) }
    val shape = remember(corner) { if (corner <= 0.dp) RectangleShape else RoundedCornerShape(corner) }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.3f))
    }
    CompositionLocalProvider(
        LocalAdtColors provides colors,
        LocalAdtType provides type,
        LocalAdtShape provides shape,
        LocalAdtCorner provides corner,
        LocalReduceMotion provides reduceMotion,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

/** Vurgu rengini kâğıt rengiyle karıştırarak hafif bir zemin tonu üretir. */
fun AdtColors.tint(amount: Float): Color = accent.copy(alpha = amount).compositeOver(paper)
