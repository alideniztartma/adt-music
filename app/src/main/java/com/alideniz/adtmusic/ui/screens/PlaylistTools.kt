package com.alideniz.adtmusic.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Btn
import com.alideniz.adtmusic.ui.components.BtnStyle
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.TextInput
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================ Listeyi düzenle (ad + açıklama)

@Composable
fun EditPlaylistContent(spec: SheetSpec.EditPlaylist, ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    var name by remember(spec) { mutableStateOf(spec.name) }
    var description by remember(spec) { mutableStateOf(spec.description) }

    fun save() {
        app.appScope.launch {
            if (name.isNotBlank()) app.dao.renamePlaylist(spec.id, name.trim())
            app.dao.setPlaylistDescription(spec.id, description.trim())
        }
        ui.dismiss()
    }

    Txt("Listeyi düzenle", AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Txt("AD", AdtTheme.type.label, color = c.muted)
        Spacer(Modifier.height(6.dp))
        TextInput(value = name, onValueChange = { name = it.take(80) }, placeholder = "Liste adı")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("AÇIKLAMA", AdtTheme.type.label, color = c.muted, modifier = Modifier.weight(1f))
            Txt("${description.length}/300", AdtTheme.type.caption, color = c.faint)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .border(1.5.dp, c.ink, AdtTheme.shape)
                .padding(14.dp),
        ) {
            if (description.isEmpty()) {
                Txt("Bu liste ne anlatıyor? Ruh hâli, an, kime ait…", AdtTheme.type.body, color = c.faint)
            }
            BasicTextField(
                value = description,
                onValueChange = { description = it.take(300) },
                textStyle = AdtTheme.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Btn("Kaydet", { save() }, Modifier.fillMaxWidth(), style = BtnStyle.INK)
    }
}

// ============================================================ Kapak oluşturucu

private enum class CoverStyle(val label: String) {
    TYPE("Tipografi"), MOSAIC("Mozaik"), STRIPE("Şerit"), CIRCLE("Daire"), GRID("Izgara")
}

private val COVER_COLORS = listOf(
    Color(0xFFFF5A1F), Color(0xFF111111), Color(0xFFF4F2EE), Color(0xFFE5383B),
    Color(0xFFFF4D8D), Color(0xFF8A5CFF), Color(0xFF2F6BFF), Color(0xFF00A6A6),
    Color(0xFF18B36B), Color(0xFFFFB400),
)

@Composable
fun PlaylistCoverContent(spec: SheetSpec.PlaylistCover, ui: AppUi) {
    val context = LocalContext.current
    val app = context.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(CoverStyle.TYPE) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val covers = remember(spec) { spec.covers.filterNotNull().distinct() }

    Txt("Kapak oluştur", AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Box(
        Modifier
            .padding(horizontal = 48.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
    ) {
        GeneratedCover(style, COVER_COLORS[colorIndex], spec.name, spec.songCount, covers)
    }
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverStyle.entries.forEach { s -> Chip(s.label, s == style, { style = s }) }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        COVER_COLORS.forEachIndexed { i, col ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(col)
                    .border(if (i == colorIndex) 3.dp else 1.dp, if (i == colorIndex) c.accent else c.line, CircleShape)
                    .press(label = "Renk") { colorIndex = i },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(
            if (saving) "Kaydediliyor…" else "Kapak yap",
            {
                if (saving) return@Btn
                saving = true
                scope.launch {
                    runCatching {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        val path = withContext(Dispatchers.IO) { saveCover(context.filesDir, spec.id, bmp) }
                        app.dao.setPlaylistCover(spec.id, path)
                        app.toast("Kapak kaydedildi")
                        ui.dismiss()
                    }.onFailure { app.toast("Kapak kaydedilemedi") }
                    saving = false
                }
            },
            Modifier.weight(1f),
            style = BtnStyle.ACCENT,
        )
        if (spec.hasCustom) {
            Btn("Sıfırla", {
                scope.launch {
                    app.dao.setPlaylistCover(spec.id, null)
                    withContext(Dispatchers.IO) { clearCovers(context.filesDir, spec.id) }
                    app.toast("Şarkı kapaklarına dönüldü")
                    ui.dismiss()
                }
            }, style = BtnStyle.OUTLINE)
        }
    }
    Spacer(Modifier.height(8.dp))
}

private fun saveCover(filesDir: File, id: Long, bitmap: Bitmap): String {
    val dir = File(filesDir, "covers").apply { mkdirs() }
    clearCovers(filesDir, id)
    val f = File(dir, "pl_${id}_${System.currentTimeMillis()}.jpg")
    val scaled = if (bitmap.width > 900) Bitmap.createScaledBitmap(bitmap, 900, 900 * bitmap.height / bitmap.width, true) else bitmap
    f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return f.absolutePath
}

private fun clearCovers(filesDir: File, id: Long) {
    File(filesDir, "covers").listFiles()?.filter { it.name.startsWith("pl_${id}_") }?.forEach { it.delete() }
}

@Composable
private fun GeneratedCover(style: CoverStyle, bg: Color, name: String, count: Int, covers: List<String>) {
    val fg = if (bg.luminance() > 0.45f) Color(0xFF111111) else Color(0xFFF4F2EE)
    val orange = if (bg == Color(0xFFFF5A1F)) Color(0xFF111111) else Color(0xFFFF5A1F)
    val title = name.ifBlank { "Liste" }
    val big = when {
        title.length > 26 -> 26.sp
        title.length > 16 -> 34.sp
        title.length > 9 -> 44.sp
        else -> 56.sp
    }
    val heading = AdtTheme.type.hero.copy(fontSize = big, lineHeight = big * 0.95f, fontWeight = FontWeight.ExtraBold)

    Box(Modifier.fillMaxSize().background(bg)) {
        when (style) {
            CoverStyle.TYPE -> Column(Modifier.fillMaxSize().padding(18.dp)) {
                Txt("ADT · $count ŞARKI", AdtTheme.type.label, color = fg.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Txt(title.uppercase(), heading, color = fg, maxLines = 4)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(48.dp).height(6.dp).background(orange))
            }

            CoverStyle.MOSAIC -> Column(Modifier.fillMaxSize()) {
                val four = (0 until 4).map { covers.getOrNull(it) ?: covers.firstOrNull() }
                Row(Modifier.weight(1f)) {
                    Cover(four[0], Modifier.weight(1f).fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                    Cover(four[1], Modifier.weight(1f).fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                }
                Row(Modifier.weight(1f)) {
                    Cover(four[2], Modifier.weight(1f).fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                    Cover(four[3], Modifier.weight(1f).fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                }
                Box(Modifier.fillMaxWidth().background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Txt(title, AdtTheme.type.headline.copy(fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1)
                }
            }

            CoverStyle.STRIPE -> Box(Modifier.fillMaxSize()) {
                Cover(covers.firstOrNull(), Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().background(bg).padding(14.dp)
                ) {
                    Txt(title, heading.copy(fontSize = big * 0.7f, lineHeight = big * 0.7f), color = fg, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Txt("$count şarkı · ADT Music", AdtTheme.type.caption, color = fg.copy(alpha = 0.75f))
                }
            }

            CoverStyle.CIRCLE -> Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-40).dp)
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(orange)
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Txt(title, heading, color = fg, maxLines = 4)
                    Spacer(Modifier.height(6.dp))
                    Txt("$count ŞARKI", AdtTheme.type.label, color = fg.copy(alpha = 0.7f))
                }
            }

            CoverStyle.GRID -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                val nine = (0 until 9).map { covers.getOrNull(it % covers.size.coerceAtLeast(1)) }
                for (r in 0 until 3) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 3) {
                            val url = nine[r * 3 + col]
                            if (r == 2 && col == 2) {
                                Box(Modifier.weight(1f).fillMaxSize().background(orange), contentAlignment = Alignment.Center) {
                                    Txt("$count", AdtTheme.type.headline.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF111111))
                                }
                            } else {
                                Cover(url, Modifier.weight(1f).fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
                            }
                        }
                    }
                    if (r < 2) Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Txt(title, AdtTheme.type.title.copy(fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1)
            }
        }
    }
}
