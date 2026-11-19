package com.alideniz.adtmusic.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Görselin küçültülmüş piksel dökümü (ARGB). */
class PixelBitmap(val width: Int, val height: Int, val pixels: IntArray)

/** Kapak görselini Coil önbelleğinden yükler ve piksellerini döndürür. */
suspend fun loadPixels(url: String, size: Int = 160): PixelBitmap? {
    val ctx = PlatformContext.INSTANCE
    val req = ImageRequest.Builder(ctx).data(url).size(size).build()
    val res = SingletonImageLoader.get(ctx).execute(req) as? SuccessResult ?: return null
    val bmp = res.image.toBitmap()
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return null
    // En fazla ~64x64 örnek yeter
    val step = max(1, max(w, h) / 64)
    val sw = (w + step - 1) / step
    val sh = (h + step - 1) / step
    val out = IntArray(sw * sh)
    var i = 0
    for (y in 0 until sh) for (x in 0 until sw) {
        out[i++] = bmp.getColor(min(x * step, w - 1), min(y * step, h - 1))
    }
    return PixelBitmap(sw, sh, out)
}

/** android.util.LruCache karşılığı (yalnızca kullanılan kısım). */
class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
    }

    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V): V? = map.put(key, value)
}

/** androidx.core.graphics.ColorUtils karşılığı (HSL dönüşümleri). */
object ColorUtils {
    fun colorToHSL(color: Int, out: FloatArray) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val delta = mx - mn
        val l = (mx + mn) / 2f
        val h: Float
        val s: Float
        if (mx == mn) {
            h = 0f; s = 0f
        } else {
            h = when (mx) {
                r -> ((g - b) / delta) % 6f
                g -> ((b - r) / delta) + 2f
                else -> ((r - g) / delta) + 4f
            }
            s = delta / (1f - abs(2f * l - 1f))
        }
        out[0] = ((h * 60f) % 360f).let { if (it < 0) it + 360f else it }
        out[1] = s.coerceIn(0f, 1f)
        out[2] = l.coerceIn(0f, 1f)
    }

    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val c = (1f - abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))
        val hi = (h.toInt() / 60) % 6
        val (r, g, b) = when (hi) {
            0 -> Triple(c + m, x + m, m)
            1 -> Triple(x + m, c + m, m)
            2 -> Triple(m, c + m, x + m)
            3 -> Triple(m, x + m, c + m)
            4 -> Triple(x + m, m, c + m)
            else -> Triple(c + m, m, x + m)
        }
        fun ch(v: Float) = (v * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

/**
 * androidx.palette'in sade bir karşılığı: renkleri gruplar ve aynı hedeflerle
 * (canlı, açık canlı, koyu canlı, soluk…) en uygun rengi seçer.
 */
class Palette private constructor(swatches: List<Swatch>) {

    class Swatch(val rgb: Int, val population: Int) {
        val hsl: FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(rgb, it) }
    }

    val dominantSwatch: Swatch? = swatches.maxByOrNull { it.population }
    private val used = mutableSetOf<Swatch>()
    private val maxPop = (dominantSwatch?.population ?: 1).toFloat()

    val vibrantSwatch: Swatch? = pick(swatches, 0.5f, 0.3f, 0.7f, 1f, 0.35f, 1f)
    val lightVibrantSwatch: Swatch? = pick(swatches, 0.74f, 0.55f, 1f, 1f, 0.35f, 1f)
    val darkVibrantSwatch: Swatch? = pick(swatches, 0.26f, 0f, 0.45f, 1f, 0.35f, 1f)
    val mutedSwatch: Swatch? = pick(swatches, 0.5f, 0.3f, 0.7f, 0.3f, 0f, 0.4f)
    val lightMutedSwatch: Swatch? = pick(swatches, 0.74f, 0.55f, 1f, 0.3f, 0f, 0.4f)
    val darkMutedSwatch: Swatch? = pick(swatches, 0.26f, 0f, 0.45f, 0.3f, 0f, 0.4f)

    private fun pick(
        list: List<Swatch>, tl: Float, minL: Float, maxL: Float, ts: Float, minS: Float, maxS: Float,
    ): Swatch? {
        val best = list.filter { s ->
            s !in used && s.hsl[1] in minS..maxS && s.hsl[2] in minL..maxL
        }.maxByOrNull { s ->
            (1f - abs(s.hsl[1] - ts)) * 0.24f + (1f - abs(s.hsl[2] - tl)) * 0.52f + (s.population / maxPop) * 0.24f
        }
        if (best != null) used += best
        return best
    }

    class Builder(private val bitmap: PixelBitmap) {
        private var maxColors = 16
        fun maximumColorCount(n: Int) = apply { maxColors = n }

        fun generate(): Palette {
            val counts = HashMap<Int, IntArray>() // anahtar: 5 bit/kanal; değer: [adet, r, g, b]
            for (p in bitmap.pixels) {
                if ((p ushr 24) < 128) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                val a = counts.getOrPut(key) { IntArray(4) }
                a[0]++; a[1] += r; a[2] += g; a[3] += b
            }
            val swatches = counts.values
                .sortedByDescending { it[0] }
                .take(maxColors.coerceAtLeast(1) * 4)
                .map { a ->
                    val n = a[0]
                    val rgb = (0xFF shl 24) or ((a[1] / n) shl 16) or ((a[2] / n) shl 8) or (a[3] / n)
                    Swatch(rgb, n)
                }
                .filterNot { s -> s.hsl[2] < 0.05f || s.hsl[2] > 0.95f } // saf siyah/beyazı at
                .take(maxColors.coerceAtLeast(1))
            return Palette(swatches)
        }
    }

    companion object {
        fun from(bitmap: PixelBitmap) = Builder(bitmap)
    }
}
