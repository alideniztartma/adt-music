package com.alideniz.adtmusic.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Albüm kapağından uygulamanın vurgu rengini çıkarır. */
object CoverPalette {
    private val cache = LruCache<String, Int>(128)

    suspend fun accentFor(context: Context, url: String?): Color? {
        if (url.isNullOrBlank()) return null
        cache.get(url)?.let { return if (it == 0) null else Color(it) }
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(160)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
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
    LaunchedEffect(url) {
        color = runCatching { CoverPalette.accentFor(context, url) }.getOrNull() ?: fallback
    }
    return color
}
