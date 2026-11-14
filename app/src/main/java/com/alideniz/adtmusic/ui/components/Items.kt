package com.alideniz.adtmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.data.ArtistRef
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.CollectionRef
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.playback.DlInfo
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatCount
import com.alideniz.adtmusic.util.formatDuration

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    download: DlInfo? = null,
    liked: Boolean = false,
    showCover: Boolean = true,
) {
    val c = AdtTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .press(onLongClick = onMore, label = song.title, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) PlayingBars(c.accent, isPlaying)
                else Txt("%02d".format(index), AdtTheme.type.caption, color = c.faint)
            }
        }
        if (showCover) {
            Box {
                Cover(song.thumbnail, Modifier.size(52.dp))
                if (isCurrent && index == null) {
                    Box(Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        PlayingBars(c.accent, isPlaying)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(song.title, AdtTheme.type.bodyStrong, color = if (isCurrent) c.accent else c.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liked) {
                    AdtIcon(AdtIcons.HeartFilled, "Beğenildi", tint = c.accent, size = 12.dp)
                    Spacer(Modifier.width(5.dp))
                }
                DownloadBadge(download)
                Txt(
                    listOfNotNull(song.artist.ifBlank { null }, formatDuration(song.durationSec).ifBlank { null }).joinToString(" · "),
                    AdtTheme.type.caption, color = c.muted, maxLines = 1,
                )
            }
        }
        IconBtn(AdtIcons.More, "Seçenekler", onMore, tint = c.muted, size = 40.dp, iconSize = 20.dp)
    }
}

@Composable
fun DownloadBadge(info: DlInfo?) {
    val c = AdtTheme.colors
    when (info?.state) {
        DlState.DONE -> {
            Box(Modifier.size(14.dp).background(c.ink), contentAlignment = Alignment.Center) {
                AdtIcon(AdtIcons.Download, "İndirildi", tint = c.paper, size = 10.dp)
            }
            Spacer(Modifier.width(6.dp))
        }
        DlState.DOWNLOADING, DlState.QUEUED -> {
            Box(Modifier.size(14.dp).border(1.dp, c.ink)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((info?.progress ?: 0f).coerceIn(0.05f, 1f)).background(c.accent))
            }
            Spacer(Modifier.width(6.dp))
        }
        else -> Unit
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 148.dp) {
    val c = AdtTheme.colors
    Column(modifier.width(width).press(onLongClick = onLongClick, label = song.title, onClick = onClick)) {
        Cover(song.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(10.dp))
        Txt(song.title, AdtTheme.type.bodyStrong, maxLines = 1)
        Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
    }
}

@Composable
fun CollectionCard(ref: CollectionRef, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 148.dp) {
    val c = AdtTheme.colors
    Column(modifier.width(width).press(label = ref.title, onClick = onClick)) {
        Box {
            Cover(ref.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
            Txt(
                if (ref.kind == CollectionKind.ALBUM) "ALBÜM" else "LİSTE",
                AdtTheme.type.label, color = c.paper,
                modifier = Modifier.background(c.ink).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Txt(ref.title, AdtTheme.type.bodyStrong, maxLines = 2)
        if (ref.subtitle.isNotBlank()) Txt(ref.subtitle, AdtTheme.type.caption, color = c.muted, maxLines = 1)
    }
}

@Composable
fun CollectionRow(ref: CollectionRef, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .press(label = ref.title, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(ref.thumbnail, Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(ref.title, AdtTheme.type.bodyStrong, maxLines = 2)
            val parts = listOfNotNull(
                if (ref.kind == CollectionKind.ALBUM) "Albüm" else "Çalma listesi",
                ref.subtitle.ifBlank { null },
                if (ref.count > 0) "${ref.count} şarkı" else null,
            )
            Txt(parts.joinToString(" · "), AdtTheme.type.caption, color = c.muted, maxLines = 1)
        }
        AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 18.dp)
    }
}

@Composable
fun ArtistRow(ref: ArtistRef, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .press(label = ref.name, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(ref.thumbnail, Modifier.size(64.dp), shape = CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(ref.name, AdtTheme.type.bodyStrong, maxLines = 1)
            Txt(
                if (ref.subscribers > 0) "Sanatçı · ${formatCount(ref.subscribers)} abone" else "Sanatçı",
                AdtTheme.type.caption, color = c.muted, maxLines = 1,
            )
        }
        AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 18.dp)
    }
}

/** İnce, sürüklenebilir ilerleme çubuğu. */
@Composable
fun SeekBar(
    fraction: Float,
    bufferedFraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AdtTheme.colors.ink,
    trackColor: Color = AdtTheme.colors.line,
    onDragChange: (Float?) -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val seek by rememberUpdatedState(onSeek)
    val dragChange by rememberUpdatedState(onDragChange)
    val shown = if (dragging) dragFraction else fraction
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragFraction = (o.x / size.width).coerceIn(0f, 1f)
                        dragChange(dragFraction)
                    },
                    onDragEnd = {
                        seek(dragFraction)
                        dragging = false
                        dragChange(null)
                    },
                    onDragCancel = { dragging = false; dragChange(null) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragChange(dragFraction)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        val trackH = if (dragging) 6.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(trackH).background(trackColor))
        Box(Modifier.fillMaxWidth(bufferedFraction.coerceIn(0f, 1f)).height(trackH).background(trackColor.copy(alpha = 1f).compositeDarker()))
        Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(trackH).background(color))
        val thumb = if (dragging) 18.dp else 12.dp
        val density = LocalDensity.current
        val x = with(density) { (w.toPx() * shown.coerceIn(0f, 1f)).toDp() } - thumb / 2
        Box(Modifier.offset(x = x).size(thumb).background(color))
    }
}

private fun Color.compositeDarker(): Color = copy(red = red * 0.85f, green = green * 0.85f, blue = blue * 0.85f)

/** Ekolayzer ve ayarlar için yatay kaydırıcı. */
@Composable
fun AdtSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    centered: Boolean = false,
    enabled: Boolean = true,
) {
    val c = AdtTheme.colors
    val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    fun toValue(fr: Float) = range.start + fr.coerceIn(0f, 1f) * (range.endInclusive - range.start)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> change(toValue(o.x / size.width)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { ch, _ -> change(toValue(ch.position.x / size.width)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        val color = if (enabled) c.ink else c.faint
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line))
        if (centered) {
            val start = minOf(f, 0.5f)
            val len = kotlin.math.abs(f - 0.5f)
            Box(Modifier.offset(x = w * start).width(w * len).height(4.dp).background(if (enabled) c.accent else c.faint))
            Box(Modifier.offset(x = w * 0.5f - 0.5.dp).width(1.dp).height(12.dp).background(c.faint))
        } else {
            Box(Modifier.fillMaxWidth(f).height(4.dp).background(if (enabled) c.accent else c.faint))
        }
        Box(Modifier.offset(x = w * f - 8.dp).size(16.dp).background(color))
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .background(if (checked) c.ink else Color.Transparent)
            .border(1.5.dp, c.ink)
            .press(role = androidx.compose.ui.semantics.Role.Switch, onClick = { onChange(!checked) })
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).background(if (checked) c.accent else c.ink))
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AdtTheme.type.title,
    onSubmit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AdtTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.5.dp, c.ink)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, style, color = c.faint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun Avatar(text: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = AdtTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
        Txt(text.take(2).uppercase(), AdtTheme.type.label, color = c.paper)
    }
}
