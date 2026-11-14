package com.alideniz.adtmusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.db.PlaylistSummary
import com.alideniz.adtmusic.playback.DlState
import com.alideniz.adtmusic.playback.SleepTimer
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatMs
import com.alideniz.adtmusic.util.shareSong
import kotlinx.coroutines.launch

@Composable
fun SheetHost(ui: AppUi) {
    val c = AdtTheme.colors
    val spec = ui.sheet
    var last by remember { mutableStateOf<SheetSpec?>(null) }
    if (spec != null) last = spec
    val visible = spec != null

    BackHandler(enabled = visible) { ui.dismiss() }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { ui.dismiss() }
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.88f
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .background(c.paper)
                    .border(1.dp, c.ink)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(top = 10.dp, bottom = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).background(c.ink))
                }
                Spacer(Modifier.height(10.dp))
                when (val s = last) {
                    is SheetSpec.SongOptions -> SongOptionsContent(s, ui)
                    is SheetSpec.AddToPlaylist -> AddToPlaylistContent(s, ui)
                    SheetSpec.SleepTimer -> SleepTimerContent(ui)
                    SheetSpec.Speed -> SpeedContent(ui)
                    SheetSpec.Queue -> QueueContent(ui)
                    is SheetSpec.Input -> InputContent(s, ui)
                    is SheetSpec.Confirm -> ConfirmContent(s, ui)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = AdtTheme.colors.ink) {
    Row(
        Modifier
            .fillMaxWidth()
            .press(label = label, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdtIcon(icon, null, tint = tint, size = 22.dp)
        Spacer(Modifier.width(18.dp))
        Txt(label, AdtTheme.type.bodyStrong, color = tint)
    }
}

@Composable
private fun SheetTitle(text: String) {
    Txt(text, AdtTheme.type.headline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun SongOptionsContent(spec: SheetSpec.SongOptions, ui: AppUi) {
    val context = LocalContext.current
    val app = context.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val song = spec.song
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val liked = song.id in likedIds
    val dl = downloads[song.id]

    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(song.thumbnail, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(song.title, AdtTheme.type.title, maxLines = 2)
            Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
        }
    }
    Line(Modifier.padding(vertical = 6.dp))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetItem(AdtIcons.Play, "Sıradaki olarak çal", { app.player.playNext(song); ui.dismiss() })
        SheetItem(AdtIcons.Queue, "Sıraya ekle", { app.player.addToQueue(listOf(song)); ui.dismiss() })
        SheetItem(AdtIcons.Radio, "Radyo başlat", { app.player.playRadio(song); ui.dismiss() })
        SheetItem(
            if (liked) AdtIcons.HeartFilled else AdtIcons.Heart,
            if (liked) "Beğenilenlerden çıkar" else "Beğen",
            {
                scope.launch {
                    app.dao.ensureSong(song)
                    app.dao.setLiked(song.id, if (liked) null else System.currentTimeMillis())
                }
                ui.dismiss()
            },
            tint = if (liked) c.accent else c.ink,
        )
        SheetItem(AdtIcons.PlaylistAdd, "Çalma listesine ekle", { ui.show(SheetSpec.AddToPlaylist(listOf(song))) })
        when (dl?.state) {
            DlState.DONE -> SheetItem(AdtIcons.Trash, "İndirmeyi sil", { app.downloads.remove(song.id); ui.dismiss() })
            DlState.DOWNLOADING, DlState.QUEUED -> SheetItem(AdtIcons.Close, "İndirmeyi iptal et", { app.downloads.remove(song.id); ui.dismiss() })
            else -> SheetItem(AdtIcons.Download, "İndir", {
                app.downloads.download(song)
                app.toast("İndirme başladı")
                ui.dismiss()
            })
        }
        if (song.artist.isNotBlank()) {
            SheetItem(AdtIcons.Artist, "Sanatçıya git", { ui.navigate(Routes.artist(song.artist, null)) })
        }
        if (spec.playlistId != null) {
            SheetItem(AdtIcons.Trash, "Bu listeden çıkar", {
                scope.launch { app.dao.removeFromPlaylist(spec.playlistId, song.id) }
                ui.dismiss()
            })
        }
        if (spec.queueIndex != null) {
            SheetItem(AdtIcons.Close, "Sıradan çıkar", { app.player.removeAt(spec.queueIndex); ui.dismiss() })
        }
        SheetItem(AdtIcons.Share, "Paylaş", { shareSong(context, song); ui.dismiss() })
    }
}

@Composable
private fun AddToPlaylistContent(spec: SheetSpec.AddToPlaylist, ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val playlists by remember { app.dao.playlists() }.collectAsState(initial = emptyList<PlaylistSummary>())

    SheetTitle("Listeye ekle")
    SheetItem(AdtIcons.Plus, "Yeni çalma listesi", {
        ui.show(
            SheetSpec.Input("Yeni çalma listesi", "", "Oluştur") { name ->
                scope.launch {
                    app.dao.createPlaylistWith(name.ifBlank { "Yeni liste" }, spec.songs, null)
                    app.toast("\"$name\" oluşturuldu")
                }
            }
        )
    }, tint = c.accent)
    Line(Modifier.padding(vertical = 4.dp))
    LazyColumn(Modifier.heightIn(max = 380.dp)) {
        itemsIndexed(playlists, key = { _, p -> p.playlist.id }) { _, p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .press(label = p.playlist.name) {
                        scope.launch {
                            spec.songs.forEach { app.dao.addToPlaylist(p.playlist.id, it) }
                            app.toast("${p.playlist.name} listesine eklendi")
                        }
                        ui.dismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(p.cover, Modifier.size(44.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(p.playlist.name, AdtTheme.type.bodyStrong, maxLines = 1)
                    Txt("${p.songCount} şarkı", AdtTheme.type.caption, color = c.muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerContent(ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val remaining by app.sleepTimer.remaining.collectAsState()
    SheetTitle("Uyku zamanlayıcısı")
    Txt(
        when (val r = remaining) {
            null -> "Müzik seçtiğin sürenin sonunda yavaşça kısılıp durur."
            SleepTimer.END_OF_SONG -> "Çalan şarkı bitince duracak."
            else -> "Kalan süre: ${formatMs(r ?: 0L)}"
        },
        AdtTheme.type.body, color = c.muted,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(Modifier.height(14.dp))
    FlowRow(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
            Chip("$m dk", false, { app.sleepTimer.start(m); app.toast("$m dakika sonra duracak"); ui.dismiss() })
        }
        Chip("Şarkı bitince", remaining == SleepTimer.END_OF_SONG, { app.sleepTimer.endOfSong(); ui.dismiss() })
    }
    if (remaining != null) {
        Spacer(Modifier.height(12.dp))
        SheetItem(AdtIcons.Close, "Zamanlayıcıyı kapat", { app.sleepTimer.cancel(); ui.dismiss() }, tint = c.accent)
    }
    Spacer(Modifier.height(8.dp))
}

private data class SoundPreset(val name: String, val speed: Float, val semitones: Float)

private val SOUND_PRESETS = listOf(
    SoundPreset("Normal", 1f, 0f),
    SoundPreset("Slowed", 0.85f, -2.8f),
    SoundPreset("Daycore", 0.8f, -4f),
    SoundPreset("Nightcore", 1.25f, 3.9f),
    SoundPreset("Hızlı (ton aynı)", 1.25f, 0f),
    SoundPreset("Yavaş (ton aynı)", 0.75f, 0f),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedContent(ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val state by app.player.state.collectAsState()
    val speed = state.speed
    val semis = state.semitones
    val semisRounded = kotlin.math.round(semis * 10f) / 10f

    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Hız ve ton", AdtTheme.type.headline, modifier = Modifier.weight(1f))
        Txt(
            "SIFIRLA", AdtTheme.type.label, color = c.accent,
            modifier = Modifier.press(label = "Sıfırla") { app.player.resetSpeedAndPitch() }.padding(8.dp),
        )
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // ---- ön ayarlar
        FlowRow(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SOUND_PRESETS.forEach { p ->
                val active = kotlin.math.abs(speed - p.speed) < 0.01f && kotlin.math.abs(semis - p.semitones) < 0.15f
                Chip(p.name, active, { app.player.setSpeedAndPitch(p.speed, p.semitones) })
            }
        }

        // ---- hız
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("HIZ", AdtTheme.type.label, color = c.muted)
                Txt("%.2fx".format(speed), AdtTheme.type.display)
            }
            StepButton("−", "Yavaşlat") { app.player.setSpeed(((speed - 0.05f) * 20).let { kotlin.math.round(it) } / 20f) }
            Spacer(Modifier.width(8.dp))
            StepButton("+", "Hızlandır") { app.player.setSpeed(((speed + 0.05f) * 20).let { kotlin.math.round(it) } / 20f) }
        }
        AdtSlider(
            value = speed,
            onChange = { v -> app.player.setSpeed(kotlin.math.round(v * 20f) / 20f) },
            range = 0.5f..2f,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp)) {
            Txt("0.5x", AdtTheme.type.caption, color = c.faint)
            Spacer(Modifier.weight(1f))
            Txt("1x", AdtTheme.type.caption, color = c.faint)
            Spacer(Modifier.weight(1f))
            Txt("2x", AdtTheme.type.caption, color = c.faint)
        }

        // ---- ton
        Spacer(Modifier.height(22.dp))
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("TON", AdtTheme.type.label, color = c.muted)
                Txt(
                    when {
                        kotlin.math.abs(semisRounded) < 0.05f -> "Orijinal"
                        semisRounded > 0 -> "+${formatSemis(semisRounded)} yarım ton"
                        else -> "−${formatSemis(-semisRounded)} yarım ton"
                    },
                    AdtTheme.type.display,
                )
            }
            StepButton("−", "Tonu düşür") { app.player.setSemitones(kotlin.math.round(semis) - 1f) }
            Spacer(Modifier.width(8.dp))
            StepButton("+", "Tonu yükselt") { app.player.setSemitones(kotlin.math.round(semis) + 1f) }
        }
        AdtSlider(
            value = semis,
            onChange = { v -> app.player.setSemitones(kotlin.math.round(v)) },
            range = -12f..12f,
            centered = true,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp)) {
            Txt("−12", AdtTheme.type.caption, color = c.faint)
            Spacer(Modifier.weight(1f))
            Txt("0", AdtTheme.type.caption, color = c.faint)
            Spacer(Modifier.weight(1f))
            Txt("+12", AdtTheme.type.caption, color = c.faint)
        }
        Spacer(Modifier.height(12.dp))
        Txt(
            "Ton, şarkının hızını değiştirmeden sesi inceltir ya da kalınlaştırır. Karaoke için kendi sesine uygun tonu bulabilirsin. Ayarların sonraki şarkılarda da geçerli kalır.",
            AdtTheme.type.caption, color = c.muted,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatSemis(v: Float): String =
    if (kotlin.math.abs(v - kotlin.math.round(v)) < 0.05f) kotlin.math.round(v).toInt().toString()
    else "%.1f".format(v)

@Composable
private fun StepButton(text: String, label: String, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .border(1.dp, c.ink)
            .press(label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, AdtTheme.type.headline)
    }
}

@Composable
private fun QueueContent(ui: AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val state by app.player.state.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((state.index - 1).coerceAtLeast(0)) }

    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt("Sıra", AdtTheme.type.headline)
            Txt("${state.queue.size} şarkı · ${state.queue.size - state.index - 1} sıradaki", AdtTheme.type.caption, color = c.muted)
        }
        IconBtn(AdtIcons.Shuffle, "Karıştır", { app.player.toggleShuffle() }, tint = if (state.shuffle) c.accent else c.ink)
        Txt(
            "TEMİZLE", AdtTheme.type.label,
            modifier = Modifier.press(label = "Sırayı temizle") { app.player.clearUpcoming() }.padding(8.dp),
        )
    }
    Line(Modifier.padding(top = 6.dp))
    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp)) {
        itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
            val current = i == state.index
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (current) c.surface else Color.Transparent)
                    .press(label = song.title) { app.player.skipTo(i) }
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(song.thumbnail, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(song.title, AdtTheme.type.bodyStrong, color = if (current) c.accent else c.ink, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadBadge(downloads[song.id])
                        Txt(song.artist, AdtTheme.type.caption, color = c.muted, maxLines = 1)
                    }
                }
                if (!current) {
                    IconBtn(AdtIcons.ChevronDown, "Aşağı taşı", { app.player.move(i, i + 1) }, size = 36.dp, iconSize = 18.dp, tint = c.muted, enabled = i < state.queue.size - 1)
                    IconBtn(AdtIcons.Close, "Sıradan çıkar", { app.player.removeAt(i) }, size = 36.dp, iconSize = 18.dp, tint = c.muted)
                } else {
                    PlayingBars(c.accent, state.isPlaying, Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun InputContent(spec: SheetSpec.Input, ui: AppUi) {
    var text by remember(spec) { mutableStateOf(spec.initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(spec) { runCatching { focus.requestFocus() } }
    SheetTitle(spec.title)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        TextInput(
            value = text,
            onValueChange = { text = it },
            placeholder = "Ad yaz",
            focusRequester = focus,
            onSubmit = { spec.onConfirm(text.trim()); ui.dismiss() },
        )
        Spacer(Modifier.height(14.dp))
        Btn(spec.confirm, { spec.onConfirm(text.trim()); ui.dismiss() }, Modifier.fillMaxWidth(), style = BtnStyle.INK)
    }
}

@Composable
private fun ConfirmContent(spec: SheetSpec.Confirm, ui: AppUi) {
    val c = AdtTheme.colors
    SheetTitle(spec.title)
    Txt(spec.message, AdtTheme.type.body, color = c.muted, modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(18.dp))
    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Vazgeç", { ui.dismiss() }, Modifier.weight(1f), style = BtnStyle.OUTLINE)
        Btn(spec.confirm, { spec.onConfirm(); ui.dismiss() }, Modifier.weight(1f), style = BtnStyle.ACCENT)
    }
}
