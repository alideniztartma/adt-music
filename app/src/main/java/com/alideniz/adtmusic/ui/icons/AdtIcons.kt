package com.alideniz.adtmusic.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.ui.theme.AdtTheme

/** ADT'nin kendi ince çizgili simge seti (Material simgeleri kullanılmaz). */
object AdtIcons {
    private fun stroke(name: String, vararg d: String, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Square,
                    strokeLineJoin = StrokeJoin.Miter,
                )
            }
        }.build()

    private fun fill(name: String, vararg d: String, vw: Float = 24f, vh: Float = 24f) =
        ImageVector.Builder(name, 24.dp, 24.dp, vw, vh).apply {
            d.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    /** Hem çizgi hem dolgu içeren simgeler için. */
    private fun mixed(name: String, strokes: List<String>, fills: List<String>, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fills.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    private const val HEART =
        "M12 20s-7-4.4-9.2-8.6C1.2 8.2 3 4.5 6.6 4.5c2.1 0 3.4 1.2 5.4 3.3 2-2.1 3.3-3.3 5.4-3.3 3.6 0 5.4 3.7 3.8 6.9C19 15.6 12 20 12 20z"

    val Back = stroke("back", "M15 5l-7 7 7 7")
    val ChevronDown = stroke("down", "M6 9l6 6 6-6")
    val ChevronRight = stroke("right", "M9 5l7 7-7 7")
    val Search = stroke("search", "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "M20 20l-4-4")
    val Home = stroke("home", "M3 10.5L12 3l9 7.5V21h-6v-6H9v6H3z")
    val Library = stroke("library", "M4 4v16", "M9 4v16", "M14 4l6 16")
    val Play = fill("play", "M7 4.5v15l13-7.5z")
    val Pause = fill("pause", "M6 5h4v14H6z", "M14 5h4v14h-4z")
    val Next = fill("next", "M5 5l10 7-10 7z", "M17.5 5H20v14h-2.5z")
    val Previous = fill("prev", "M19 5L9 12l10 7z", "M4 5h2.5v14H4z")
    val Shuffle = stroke("shuffle", "M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")
    val Repeat = stroke("repeat", "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")
    val RepeatOne = stroke("repeat1", "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3", "M11 10h1v4")
    val Heart = stroke("heart", HEART)
    val HeartFilled = fill("heartFilled", HEART)
    val More = fill(
        "more",
        "M5 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M12 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
        "M19 10.2a1.8 1.8 0 1 1 0 3.6a1.8 1.8 0 1 1 0-3.6z",
    )
    val Download = stroke("download", "M12 3v12", "M7 10l5 5 5-5", "M5 21h14")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7")
    /** Sözler: metin satırları ve köşede bir nota. */
    val Lyrics = mixed(
        "lyrics",
        strokes = listOf("M3 5h15", "M3 10h11", "M3 15h8", "M3 20h6", "M19.5 17.5V9l2.5-1"),
        fills = listOf("M17.4 15.6a2.1 2.1 0 1 1 0 4.2a2.1 2.1 0 1 1 0-4.2z"),
    )
    val Queue = stroke("queue", "M4 6h16", "M4 12h16", "M4 18h10")
    val Equalizer = stroke("eq", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6")
    val Moon = stroke("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
    val Share = stroke("share", "M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7", "M16 6l-4-4-4 4", "M12 2v13")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Close = stroke("close", "M6 6l12 12", "M18 6L6 18")
    /** Ayarlar: iki kaydırıcı düğmesi (modern "ince ayar" simgesi). */
    val Settings = mixed(
        "settings",
        strokes = listOf("M3 7h9", "M19 7h2", "M3 17h3", "M13 17h8"),
        fills = listOf(
            "M15.5 3.8a3.2 3.2 0 1 1 0 6.4a3.2 3.2 0 1 1 0-6.4z",
            "M9.5 13.8a3.2 3.2 0 1 1 0 6.4a3.2 3.2 0 1 1 0-6.4z",
        ),
    )
    val Artist = stroke("artist", "M12 4a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M4 21a8 8 0 0 1 16 0")
    val Album = stroke("album", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 10a2 2 0 1 1 0 4a2 2 0 1 1 0-4z")
    val Radio = stroke(
        "radio", "M4.9 19.1a10 10 0 0 1 0-14.2", "M19.1 4.9a10 10 0 0 1 0 14.2",
        "M7.8 16.2a6 6 0 0 1 0-8.4", "M16.2 7.8a6 6 0 0 1 0 8.4", "M12 11a1 1 0 1 1 0 2a1 1 0 1 1 0-2z",
    )
    val Trash = stroke("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M5 7l1 13h12l1-13", "M9 7V4h6v3")
    val Edit = stroke("edit", "M4 20h4L19 9l-4-4L4 16z", "M14 6l4 4")
    val History = stroke("history", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 7v5l3 2")
    val Stats = stroke("stats", "M5 20V10", "M12 20V4", "M19 20v-7")
    val Offline = stroke("offline", "M2 2l20 20", "M8.5 16.5a5 5 0 0 1 7 0", "M5 12.9a10 10 0 0 1 5.2-2.7", "M16.8 10.4A10 10 0 0 1 19 12.9", "M12 20h.01")
    val PlaylistAdd = stroke("playlistAdd", "M3 6h12", "M3 12h12", "M3 18h8", "M18 13v8", "M14 17h8")
    val Wrapped = mixed(
        "wrapped",
        strokes = listOf("M4 20V12", "M10 20V6", "M16 20v-9"),
        fills = listOf("M19.5 2l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1z"),
    )
    val FollowAdd = stroke("follow", "M10 4a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M3 21a7 7 0 0 1 12.5-4.3", "M19 14v6", "M16 17h6")
    val Following = stroke("following", "M10 4a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M3 21a7 7 0 0 1 12.5-4.3", "M15.5 18l2 2 4-4.5")
    val Palette = mixed(
        "palette",
        strokes = listOf("M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.6-1-2.6 0-1 .8-1.7 1.9-1.7H17a4 4 0 0 0 4-4C21 6.5 17 3 12 3z"),
        fills = listOf(
            "M7.5 10a1.3 1.3 0 1 1 0 2.6a1.3 1.3 0 1 1 0-2.6z",
            "M10 6.3a1.3 1.3 0 1 1 0 2.6a1.3 1.3 0 1 1 0-2.6z",
            "M14.5 6.3a1.3 1.3 0 1 1 0 2.6a1.3 1.3 0 1 1 0-2.6z",
        ),
    )
    /** Music Moments: parıltı. */
    val Moment = mixed(
        "moment",
        strokes = emptyList(),
        fills = listOf(
            "M10 3l1.9 5.6L17.5 10.5l-5.6 1.9L10 18l-1.9-5.6L2.5 10.5l5.6-1.9z",
            "M18.5 14l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9z",
        ),
    )
    val Badge = stroke("badge", "M12 3a6 6 0 1 1 0 12a6 6 0 1 1 0-12z", "M8.5 14L7 21l5-3 5 3-1.5-7")
    val Comment = stroke("comment", "M4 4.5h16v11.5H9.5L4 20.5z", "M8 9h8", "M8 12.5h5")
    val Info = stroke("info", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 11v6", "M12 7.5v.5")
    val Quote = mixed("quote", strokes = emptyList(), fills = listOf("M4 10a4 4 0 0 1 4-4v2a2 2 0 0 0-2 2h2v6H4z", "M13 10a4 4 0 0 1 4-4v2a2 2 0 0 0-2 2h2v6h-4z"))
    val Import = stroke("import", "M12 3v11", "M7 9l5 5 5-5", "M4 15v5h16v-5")
    val Mix = stroke("mix", "M3 7h4l9 10h5", "M3 17h4l2.2-2.5", "M14.2 9.6L16 7h5", "M18 4l3 3-3 3", "M18 14l3 3-3 3")
    val Update = stroke("update", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5", "M12 8v5l3 2")
    val DragHandle = stroke("drag", "M8 7h8", "M8 12h8", "M8 17h8")
    val Upload = stroke("upload", "M12 15V3", "M7 8l5-5 5 5", "M5 21h14")
    val Globe = stroke("globe", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M3 12h18", "M12 3c2.5 2.7 3.6 5.7 3.6 9s-1.1 6.3-3.6 9c-2.5-2.7-3.6-5.7-3.6-9S9.5 5.7 12 3z")
    val Link = stroke("link", "M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1", "M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1")
    val Speed = stroke("speed", "M12 14l4-4", "M3.3 18a10 10 0 1 1 17.4 0")
    val Refresh = stroke("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val SkipBack10 = stroke("back10", "M4 12a8 8 0 1 0 2.3-5.7", "M4 4v5h5")
    val SkipFwd10 = stroke("fwd10", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5")

    /** Logodaki nota (ADT Music'in işareti). */
    val Note = fill(
        "note",
        "M72.07 101.06 A32 24 -20 1 1 11.93 122.94 A32 24 -20 1 1 72.07 101.06 Z",
        "M62 10 H73 V114 H62 Z",
        "M73 10 C73 40 112 50 104 92 C100 68 88 58 73 58 Z",
        vw = 120f, vh = 140f,
    )
}

@Composable
fun AdtIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = AdtTheme.colors.ink,
    size: Dp? = 24.dp,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = if (size != null) modifier.size(size) else modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
