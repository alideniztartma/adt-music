package com.alideniz.adtmusic.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.data.Comment
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.container
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

private class RepliesState {
    val items = mutableStateListOf<Comment>()
    var next by mutableStateOf<Page?>(null)
    var loading by mutableStateOf(false)
    var open by mutableStateOf(false)
}

private val TIMESTAMP = Regex("(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\d:])")

private fun countText(n: Int): String = when {
    n >= 1_000_000 -> "%.1f Mn".format(n / 1e6)
    n >= 1_000 -> "%.1f B".format(n / 1e3).replace(".0 ", " ")
    else -> n.toString()
}

/** YouTube yorumları: sabitlenen, sanatçının beğendiği ve yanıtlar; zaman damgasına dokununca o ana atlar. */
@Composable
fun CommentsContent(spec: SheetSpec.Comments, ui: AppUi) {
    val context = LocalContext.current
    val app = context.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val song = spec.song

    val comments = remember(song.id) { mutableStateListOf<Comment>() }
    var next by remember(song.id) { mutableStateOf<Page?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }
    var loadingMore by remember(song.id) { mutableStateOf(false) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var disabled by remember(song.id) { mutableStateOf(false) }
    var total by remember(song.id) { mutableStateOf(-1) }
    var reload by remember { mutableStateOf(0) }
    val replies = remember(song.id) { mutableStateMapOf<String, RepliesState>() }
    val listState = rememberLazyListState()

    LaunchedEffect(song.id, reload) {
        loading = true; error = null
        runCatching { app.youtube.comments(song.id) }
            .onSuccess { p ->
                comments.clear(); comments.addAll(p.items)
                next = p.next; disabled = p.disabled; total = p.total
            }
            .onFailure { error = it.message ?: "Bilinmeyen hata" }
        loading = false
    }

    fun loadMore() {
        val page = next ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { app.youtube.moreComments(song.id, page) }
                .onSuccess { p ->
                    val known = comments.map { it.id }.toHashSet()
                    comments.addAll(p.items.filter { it.id !in known })
                    next = p.next
                }
                .onFailure { app.toast("Daha fazla yorum yüklenemedi") }
            loadingMore = false
        }
    }

    fun toggleReplies(comment: Comment) {
        val st = replies.getOrPut(comment.id) { RepliesState() }
        if (st.open) { st.open = false; return }
        st.open = true
        if (st.items.isNotEmpty() || st.loading) return
        val page = comment.repliesPage ?: return
        st.loading = true
        scope.launch {
            runCatching { app.youtube.moreComments(song.id, page) }
                .onSuccess { st.items.addAll(it.items); st.next = it.next }
                .onFailure { app.toast("Yanıtlar yüklenemedi") }
            st.loading = false
        }
    }

    fun moreReplies(st: RepliesState) {
        val page = st.next ?: return
        if (st.loading) return
        st.loading = true
        scope.launch {
            runCatching { app.youtube.moreComments(song.id, page) }
                .onSuccess { st.items.addAll(it.items); st.next = it.next }
            st.loading = false
        }
    }

    // Listenin sonuna yaklaşınca otomatik yükle
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, next) { if (nearEnd && next != null && !loading) loadMore() }

    val onTimestamp: (Long) -> Unit = { ms ->
        val current = app.player.state.value.current
        if (current?.id == song.id) app.player.seekTo(ms) else app.player.playAt(song, ms)
        app.toast("⏱ ${formatClock(ms)}")
    }

    // ---- başlık
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt("Yorumlar", AdtTheme.type.headline)
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Txt(countText(total), AdtTheme.type.title, color = c.accent)
                }
            }
            Txt(song.title + " · " + song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
        }
        Cover(song.thumbnail, Modifier.size(44.dp))
    }
    Line(Modifier.padding(top = 6.dp))

    when {
        loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Txt("Yorumlar yükleniyor…", AdtTheme.type.body, color = c.muted)
        }
        error != null -> CommentsMessage("Yorumlar yüklenemedi", "İnternet bağlantını kontrol et.", "TEKRAR DENE") { reload++ }
        disabled -> CommentsMessage("Yorumlar kapalı", "Bu şarkının yükleyicisi yorumları kapatmış.", null) {}
        comments.isEmpty() -> CommentsMessage("Henüz yorum yok", "Bu şarkıya kimse yorum yazmamış.", null) {}
        else -> LazyColumn(state = listState, modifier = Modifier.heightIn(max = 600.dp)) {
            items(comments, key = { it.id }) { comment ->
                val st = replies[comment.id]
                CommentRow(comment, context, onTimestamp)
                if (comment.replyCount > 0 && comment.repliesPage != null) {
                    Row(
                        Modifier
                            .padding(start = 72.dp, bottom = 6.dp)
                            .press(label = "Yanıtlar") { toggleReplies(comment) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(18.dp).height(2.dp).background(c.accent))
                        Spacer(Modifier.width(8.dp))
                        Txt(
                            if (st?.open == true) "YANITLARI GİZLE" else "${comment.replyCount} YANIT",
                            AdtTheme.type.label, color = c.accent,
                        )
                    }
                }
                if (st?.open == true) {
                    Column(Modifier.padding(start = 40.dp)) {
                        st.items.forEach { r -> CommentRow(r, context, onTimestamp, small = true) }
                        when {
                            st.loading -> Txt("Yükleniyor…", AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(start = 32.dp, bottom = 10.dp))
                            st.next != null -> Txt(
                                "DAHA FAZLA YANIT", AdtTheme.type.label, color = c.ink,
                                modifier = Modifier.padding(start = 32.dp, bottom = 10.dp).press { moreReplies(st) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                Line(Modifier.padding(horizontal = 24.dp))
            }
            if (next != null || loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Txt("Daha fazla yorum yükleniyor…", AdtTheme.type.caption, color = c.muted)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, context: Context, onTimestamp: (Long) -> Unit, small: Boolean = false) {
    val c = AdtTheme.colors
    var expanded by remember(comment.id) { mutableStateOf(false) }
    val avatar = if (small) 26.dp else 36.dp

    Row(
        Modifier
            .fillMaxWidth()
            .press(
                role = null,
                label = "Yorumu aç",
                onLongClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("yorum", comment.text))
                    context.container.toast("Yorum kopyalandı")
                },
            ) { expanded = !expanded }
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Cover(comment.avatar, Modifier.size(avatar), shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (comment.pinned) {
                Txt("📌 SABİTLENDİ", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameStyle = AdtTheme.type.bodyStrong
                if (comment.byUploader) {
                    Box(Modifier.background(c.ink).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Txt(comment.author, nameStyle, color = c.paper, maxLines = 1)
                    }
                } else {
                    Txt(comment.author, nameStyle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                }
                if (comment.verified) {
                    Spacer(Modifier.width(4.dp))
                    Txt("✓", AdtTheme.type.caption, color = c.muted)
                }
                comment.date?.let {
                    Spacer(Modifier.width(8.dp))
                    Txt(it, AdtTheme.type.caption, color = c.muted, maxLines = 1)
                }
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = linkify(comment.text, c.accent, onTimestamp),
                style = AdtTheme.type.body.copy(color = c.ink),
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded && comment.text.length > 280) {
                Txt("DEVAMI", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdtIcon(AdtIcons.Heart, null, tint = c.muted, size = 14.dp)
                    if (comment.likes > 0) {
                        Spacer(Modifier.width(4.dp))
                        Txt(countText(comment.likes), AdtTheme.type.caption, color = c.muted)
                    }
                }
                if (comment.hearted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdtIcon(AdtIcons.HeartFilled, null, tint = c.accent, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Txt("Sanatçı beğendi", AdtTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = c.accent)
                    }
                }
            }
        }
    }
}

/** Yorumdaki "1:24" gibi zaman damgalarını dokunulabilir bağlantıya çevirir. */
private fun linkify(text: String, color: androidx.compose.ui.graphics.Color, onTimestamp: (Long) -> Unit): AnnotatedString {
    val matches = TIMESTAMP.findAll(text).toList()
    if (matches.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var last = 0
        matches.forEach { m ->
            append(text.substring(last, m.range.first))
            val h = m.groupValues[1].toLongOrNull() ?: 0
            val min = m.groupValues[2].toLong()
            val sec = m.groupValues[3].toLong()
            val ms = ((h * 60 + min) * 60 + sec) * 1000
            if (sec < 60) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "t$ms",
                        styles = TextLinkStyles(SpanStyle(color = color, fontWeight = FontWeight.Bold)),
                        linkInteractionListener = { onTimestamp(ms) },
                    )
                ) { append(m.value) }
            } else append(m.value)
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun CommentsMessage(title: String, message: String, action: String?, onAction: () -> Unit) {
    val c = AdtTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp)) {
        Txt(title, AdtTheme.type.headline)
        Spacer(Modifier.height(6.dp))
        Txt(message, AdtTheme.type.body, color = c.muted)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Txt(action, AdtTheme.type.label, color = c.accent, modifier = Modifier.press(onClick = onAction).padding(vertical = 8.dp))
        }
    }
}

