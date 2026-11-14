package com.alideniz.adtmusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AdtTheme.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Basınca hafifçe küçülen, dalga efekti olmayan tıklama. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 900f), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = label,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AdtTheme.colors.ink,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .press(enabled = enabled, label = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AdtIcon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), size = iconSize)
    }
}

enum class BtnStyle { INK, ACCENT, OUTLINE }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.INK,
    height: Dp = 48.dp,
) {
    val c = AdtTheme.colors
    val (bg, fg) = when (style) {
        BtnStyle.INK -> c.ink to c.paper
        BtnStyle.ACCENT -> c.accent to c.onAccent
        BtnStyle.OUTLINE -> Color.Transparent to c.ink
    }
    Row(
        modifier = modifier
            .height(height)
            .background(bg)
            .then(if (style == BtnStyle.OUTLINE) Modifier.border(1.dp, c.ink) else Modifier)
            .press(label = text, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            AdtIcon(icon, null, tint = fg, size = 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Txt(text.uppercase(), AdtTheme.type.label, color = fg, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    Box(
        modifier = modifier
            .height(36.dp)
            .background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.line)
            .press(role = Role.Tab, label = text, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, AdtTheme.type.caption, color = if (selected) c.paper else c.ink, maxLines = 1)
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Txt(title, AdtTheme.type.headline, modifier = Modifier.weight(1f), maxLines = 1)
        if (action != null && onAction != null) {
            Txt(
                action.uppercase(), AdtTheme.type.label,
                modifier = Modifier
                    .press(label = action, onClick = onAction)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
fun Line(modifier: Modifier = Modifier, color: Color = AdtTheme.colors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Albüm kapağı. Yüklenene kadar kapağın yerinde soluk bir nota durur. */
@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentDescription: String? = null,
) {
    val c = AdtTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.surface),
        contentAlignment = Alignment.Center,
    ) {
        AdtIcon(AdtIcons.Note, null, tint = c.line, size = 28.dp)
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Çalmakta olan şarkıyı gösteren üç çubuklu küçük animasyon. */
@Composable
fun PlayingBars(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$delay",
        )
    }
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeightFraction(if (playing) h.value else 0.3f)
                    .background(color)
            )
        }
    }
}

private fun Modifier.fillMaxHeightFraction(f: Float) = this.then(Modifier.height((18 * f).dp))

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    val c = AdtTheme.colors
    val transition = rememberInfiniteTransition(label = "loading")
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0, 150, 300).forEach { d ->
            val a by transition.animateFloat(
                0.15f, 1f,
                infiniteRepeatable(tween(450, delayMillis = d), RepeatMode.Reverse),
                label = "dot$d",
            )
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(10.dp)
                    .graphicsLayer { alpha = a }
                    .background(if (d == 150) c.accent else c.ink)
            )
        }
    }
}

@Composable
fun MessageBlock(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AdtIcons.Note,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AdtTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            AdtIcon(icon, null, tint = c.onAccent, size = 28.dp)
        }
        Spacer(Modifier.height(20.dp))
        Txt(title, AdtTheme.type.title, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Txt(message, AdtTheme.type.body, color = c.muted, align = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Btn(action, onAction, style = BtnStyle.OUTLINE)
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
