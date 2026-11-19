package com.alideniz.adtmusic.ui.theme

import com.alideniz.adtmusic.platform.Context
import com.alideniz.adtmusic.platform.ColorUtils
import com.alideniz.adtmusic.platform.LruCache
import com.alideniz.adtmusic.platform.Palette
import com.alideniz.adtmusic.platform.loadPixels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.alideniz.adtmusic.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Albüm kapağından uygulamanın vurgu rengini çıkarır. */
object CoverPalette {
    private val cache = LruCache<String, Int>(128)

    suspend fun accentFor(context: Context, url: String?): Color? {
        if (url.isNullOrBlank()) return null
        cache.get(url)?.let { return if (it == 0) null else Color(it) }
        val bitmap = loadPixels(url, 160) ?: return null
        val color = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(24).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
            swatch?.let { normalize(it.rgb) }
        }
        cache.put(url, color?.toArgb() ?: 0)
        return color
    }

    /** Çok soluk veya çok koyu renkleri, arayüzde okunur bir vurguya çevirir. */
    private fun normalize(rgb: Int): Color? {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        if (hsl[1] < 0.18f) return null // siyah-beyaz kapak: varsayılan turuncu kalsın
        hsl[1] = hsl[1].coerceIn(0.5f, 0.92f)
        hsl[2] = hsl[2].coerceIn(0.45f, 0.6f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

@Composable
fun rememberCoverAccent(url: String?, fallback: Color = AdtOrange): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(url, fallback) {
        color = runCatching { CoverPalette.accentFor(context, url) }.getOrNull() ?: fallback
    }
    return color
}
