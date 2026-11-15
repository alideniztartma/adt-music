package com.alideniz.adtmusic.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import com.alideniz.adtmusic.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.BuildConfig
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.playback.AudioEffects
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.AdtSlider
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.Toggle
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.util.formatBytes
import com.alideniz.adtmusic.util.formatMs
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val fx = app.effects
    val enabled by app.settings.eqEnabled.flow.collectAsState()
    val preset by app.settings.eqPreset.flow.collectAsState()
    val bands by fx.bands.collectAsState()
    val levels by fx.levels.collectAsState()
    val bass by app.settings.bassBoost.flow.collectAsState()
    val virt by app.settings.virtualizer.flow.collectAsState()
    val available by fx.available.collectAsState()
    val boostEq by app.settings.volumeBoost.flow.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Ekolayzer", "Sesi kulağına göre ayarla", onBack = { ui.back() }) {
            Toggle(enabled, { fx.setEnabled(it) }, Modifier.padding(end = 12.dp))
        }
        if (!available) {
            MessageBlock(
                "Ekolayzer hazır değil",
                "Bir şarkı çalmaya başladığında cihazının ses efektleri etkinleşir.",
                icon = AdtIcons.Equalizer,
            )
        }
        Txt("ÖN AYARLAR", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioEffects.PRESETS.keys.forEach { name ->
                Chip(name, name == preset, {
                    if (!enabled) fx.setEnabled(true)
                    fx.applyPreset(name)
                })
            }
            if (preset == "Özel") Chip("Özel", true, {})
        }
        Spacer(Modifier.height(26.dp))
        Txt("BANTLAR", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))
        bands.forEachIndexed { i, band ->
            val level = levels.getOrNull(i) ?: 0
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    if (band.centerHz >= 1000) "${band.centerHz / 1000} kHz" else "${band.centerHz} Hz",
                    AdtTheme.type.caption, color = c.muted, modifier = Modifier.width(64.dp),
                )
                AdtSlider(
                    value = level.toFloat(),
                    onChange = { fx.setBand(i, it.toInt()) },
                    range = band.minMb.toFloat()..band.maxMb.toFloat(),
                    centered = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Txt(
                    "%+.0f dB".format(level / 100f), AdtTheme.type.caption,
                    modifier = Modifier.width(56.dp).padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Txt("EFEKTLER", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp))
        EffectSlider("Bas güçlendirme", bass, enabled) { fx.setBass(it) }
        EffectSlider("Sanal surround", virt, enabled) { fx.setVirtualizer(it) }
        EffectSlider("Ses yükseltme", boostEq, true) { fx.setVolumeBoost(it) }
        Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
    }
}

@Composable
private fun EffectSlider(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val c = AdtTheme.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Txt(label, AdtTheme.type.bodyStrong, modifier = Modifier.weight(1f))
            Txt("%${value / 10}", AdtTheme.type.caption, color = c.muted)
        }
        AdtSlider(value.toFloat(), { onChange(it.toInt()) }, range = 0f..1000f, enabled = enabled)
    }
}

@Composable
private fun DataUsageCard(mobile: Long, wifi: Long, onReset: () -> Unit) {
    val c = AdtTheme.colors
    val total = (mobile + wifi).coerceAtLeast(1)
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().border(1.dp, c.ink).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("BU AYKİ VERİ KULLANIMI", AdtTheme.type.label, color = c.accent, modifier = Modifier.weight(1f))
            Txt("SIFIRLA", AdtTheme.type.label, color = c.faint, modifier = Modifier.press(label = "Sıfırla", onClick = onReset).padding(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Txt("MOBİL", AdtTheme.type.label, color = c.muted)
                Txt(com.alideniz.adtmusic.playback.DataUsage.format(mobile), AdtTheme.type.headline)
            }
            Column(Modifier.weight(1f)) {
                Txt("WI-FI", AdtTheme.type.label, color = c.muted)
                Txt(com.alideniz.adtmusic.playback.DataUsage.format(wifi), AdtTheme.type.headline)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(6.dp).background(c.line)) {
            val f = mobile.toFloat() / total
            if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(c.accent))
            if (f < 1f) Box(Modifier.weight(1f - f).fillMaxHeight().background(c.ink))
        }
        Spacer(Modifier.height(6.dp))
        Txt("Akış ve indirmeler dahil. Ay başında kendiliğinden sıfırlanır.", AdtTheme.type.caption, color = c.muted)
    }
}

/** Oynatıcı tarzı seçimi: küçük önizlemelerle. */
@Composable
private fun PlayerStylePicker(selected: String, onSelect: (String) -> Unit) {
    val c = AdtTheme.colors
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("minimal" to "Minimal", "apple" to "Apple Music").forEach { (key, label) ->
            val on = key == selected
            Column(
                Modifier
                    .weight(1f)
                    .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line)
                    .press(label = label) { onSelect(key) }
                    .padding(10.dp),
            ) {
                // küçük şematik önizleme
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.62f)
                        .background(
                            if (key == "apple") androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF5B2A86), Color(0xFFB5453A), Color(0xFF1C1A2E))
                            ) else androidx.compose.ui.graphics.SolidColor(c.paper)
                        )
                        .border(1.dp, c.line)
                        .padding(10.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        val fg = if (key == "apple") Color.White else c.ink
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(if (key == "apple") androidx.compose.foundation.shape.RoundedCornerShape(8.dp) else androidx.compose.ui.graphics.RectangleShape)
                                .background(if (key == "apple") Color.White.copy(alpha = 0.85f) else c.ink)
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).background(fg))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth(0.45f).height(4.dp).background(fg.copy(alpha = 0.5f)))
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.fillMaxWidth().height(3.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                .background(fg.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            if (key == "apple") {
                                repeat(3) { i -> Box(Modifier.size(if (i == 1) 14.dp else 10.dp).clip(CircleShape).background(fg)) }
                            } else {
                                Box(Modifier.size(16.dp).background(c.accent))
                                Box(Modifier.size(width = 10.dp, height = 16.dp).border(1.dp, fg))
                                Box(Modifier.size(width = 10.dp, height = 16.dp).border(1.dp, fg))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(label, AdtTheme.type.bodyStrong, modifier = Modifier.weight(1f))
                    if (on) AdtIcon(AdtIcons.Check, null, tint = c.accent, size = 16.dp)
                }
            }
        }
    }
}

private val ACCENTS = listOf(
    0xFFFF5A1F.toInt(), 0xFFE5383B.toInt(), 0xFFFF4D8D.toInt(), 0xFF8A5CFF.toInt(),
    0xFF2F6BFF.toInt(), 0xFF00A6A6.toInt(), 0xFF18B36B.toInt(), 0xFFFFB400.toInt(),
)

private val COUNTRIES = listOf(
    "TR" to "Türkiye", "US" to "ABD", "GB" to "İngiltere", "DE" to "Almanya", "FR" to "Fransa",
    "NL" to "Hollanda", "AZ" to "Azerbaycan", "KR" to "Kore", "JP" to "Japonya", "BR" to "Brezilya",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val s = app.settings
    val scope = rememberCoroutineScope()

    val theme by s.theme.flow.collectAsState()
    val pureBlack by s.pureBlack.flow.collectAsState()
    val dynamic by s.dynamicColor.flow.collectAsState()
    val accentInt by s.accentColor.flow.collectAsState()
    val corner by s.cornerStyle.flow.collectAsState()
    val fontScale by s.fontScale.flow.collectAsState()
    val playerBg by s.playerBackground.flow.collectAsState()
    val miniProgress by s.miniPlayerProgress.flow.collectAsState()
    val startTab by s.startTab.flow.collectAsState()
    val showRecent by s.homeShowRecent.flow.collectAsState()
    val showFollowed by s.homeShowFollowed.flow.collectAsState()
    val showTrending by s.homeShowTrending.flow.collectAsState()
    val showAlbums by s.homeShowAlbums.flow.collectAsState()
    val country by s.contentCountry.flow.collectAsState()
    val lyricsAnim by s.lyricsAnimation.flow.collectAsState()
    val lyricsAlign by s.lyricsAlign.flow.collectAsState()
    val lyricsSize by s.lyricsTextSize.flow.collectAsState()
    val keepScreen by s.keepScreenOnLyrics.flow.collectAsState()
    val highRefresh by s.highRefreshRate.flow.collectAsState()
    val frameRate by s.frameRate.flow.collectAsState()
    val crossfade by s.crossfadeSec.flow.collectAsState()
    val normalize by s.normalizeVolume.flow.collectAsState()
    val headset by s.headsetGestures.flow.collectAsState()
    val autoUpdate by s.autoUpdateCheck.flow.collectAsState()
    val showFps by s.showFps.flow.collectAsState()
    val visualizer by s.visualizerStyle.flow.collectAsState()
    val coverAnim by s.coverAnimation.flow.collectAsState()
    val releaseNotify by s.releaseNotifications.flow.collectAsState()
    val weeklyNotify by s.weeklyNotification.flow.collectAsState()
    val mobileQuality by s.mobileQuality.flow.collectAsState()
    val usage by app.dataUsage.usage.collectAsState()
    val btAuto by s.btAutoPlay.flow.collectAsState()
    val iconFollows by s.iconFollowsAccent.flow.collectAsState()
    val playerStyle by s.playerStyle.flow.collectAsState()
    val coverSize by s.coverSize.flow.collectAsState()
    val lyricsPlacement by s.lyricsPlacement.flow.collectAsState()
    val volumeBar by s.playerVolumeBar.flow.collectAsState()
    val lyricsWeight by s.lyricsWeight.flow.collectAsState()
    val lyricsSpacing by s.lyricsSpacing.flow.collectAsState()
    val lyricsUpper by s.lyricsUppercase.flow.collectAsState()
    val lyricsHighlight by s.lyricsHighlight.flow.collectAsState()
    val lyricsBlur by s.lyricsBlur.flow.collectAsState()
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { s.btAutoPlay.set(true); app.toast("Bluetooth bağlanınca müzik kendiliğinden başlayacak") }
        else app.toast("Bluetooth izni olmadan bağlantı algılanamaz")
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingVisualizer by remember { mutableStateOf<String?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val want = pendingVisualizer
        pendingVisualizer = null
        if (granted && want != null) s.visualizerStyle.set(want)
        else if (!granted) app.toast("Görselleştirici için ses izni gerekli (yalnızca çalan müziği okur)")
    }
    val reduceMotion by s.reduceMotion.flow.collectAsState()
    val haptics by s.haptics.flow.collectAsState()
    val quality by s.audioQuality.flow.collectAsState()
    val dataSaver by s.mobileDataSaver.flow.collectAsState()
    val preload by s.preloadNext.flow.collectAsState()
    val autoplay by s.autoplay.flow.collectAsState()
    val skipSilence by s.skipSilence.flow.collectAsState()
    val pauseOnDisconnect by s.pauseOnDisconnect.flow.collectAsState()
    val resume by s.resumeOnStart.flow.collectAsState()
    val boost by s.volumeBoost.flow.collectAsState()
    val cacheMb by s.cacheSizeMb.flow.collectAsState()
    val wifiOnly by s.downloadWifiOnly.flow.collectAsState()
    val autoDl by s.autoDownloadLiked.flow.collectAsState()
    val saveSearch by s.saveSearchHistory.flow.collectAsState()
    val suggestions by s.searchSuggestions.flow.collectAsState()
    val pauseHistory by s.pauseHistory.flow.collectAsState()
    val sleep by app.sleepTimer.remaining.collectAsState()
    val player by app.player.state.collectAsState()
    var cacheUsed by remember { mutableLongStateOf(app.media.playerCacheBytes()) }
    var version by remember { mutableIntStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = Backup.export(app)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
            }.onSuccess { app.toast("Yedek kaydedildi") }.onFailure { app.toast("Yedek kaydedilemedi") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: error("Dosya okunamadı")
                Backup.import(app, text)
            }.onSuccess { app.toast("$it öğe geri yüklendi") }.onFailure { app.toast("Bu dosya bir ADT Music yedeği değil") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Ayarlar", "Uygulamayı kendine göre ayarla", onBack = { ui.back() })

        // ------------------------------------------------------------ görünüm
        Group("Görünüm")
        Choice("Tema", listOf("system" to "Sistem", "light" to "Açık", "dark" to "Koyu"), theme) { s.theme.set(it) }
        SwitchRow("Saf siyah (AMOLED)", "Koyu temada tam siyah zemin; OLED ekranda pil tasarrufu.", pureBlack) { s.pureBlack.set(it) }
        SwitchRow("Renkleri kapaktan al", "Vurgu rengi çalan şarkının kapağına göre değişir.", dynamic) { s.dynamicColor.set(it) }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Txt(if (dynamic) "Kapak rengi yoksa kullanılacak renk" else "Vurgu rengi", AdtTheme.type.bodyStrong)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEach { col ->
                    val selected = col == accentInt
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .border(if (selected) 3.dp else 0.dp, c.ink, CircleShape)
                            .press(label = "Renk") { s.accentColor.set(col) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) AdtIcon(AdtIcons.Check, null, tint = Color.White, size = 18.dp)
                    }
                }
            }
        }
        SwitchRow(
            "Uygulama simgesi vurgu rengini izlesin",
            "Simgedeki daire seçtiğin renge bürünür. Değişiklik uygulamadan çıkınca görünür; başlatıcı simgeyi birkaç saniye içinde yeniler.",
            iconFollows,
        ) { s.iconFollowsAccent.set(it) }
        Line()
        Choice("Köşeler", listOf("sharp" to "Keskin", "soft" to "Yumuşak", "round" to "Yuvarlak"), corner) { s.cornerStyle.set(it) }
        Choice("Yazı boyutu", listOf(0.9f to "Küçük", 1f to "Normal", 1.1f to "Büyük", 1.2f to "Çok büyük"), fontScale) { s.fontScale.set(it) }
        Choice("Oynatıcı arka planı", listOf("tint" to "Renkli", "mesh" to "Hareketli renk", "blur" to "Bulanık kapak", "plain" to "Düz"), playerBg) { s.playerBackground.set(it) }
        SwitchRow("Mini oynatıcıda ilerleme çizgisi", null, miniProgress) { s.miniPlayerProgress.set(it) }
        Choice("Açılış sayfası", listOf(Routes.HOME to "Ana sayfa", Routes.SEARCH to "Ara", Routes.LIBRARY to "Kütüphane"), startTab) { s.startTab.set(it) }

        // ------------------------------------------------------------ ana sayfa
        Group("Ana sayfa")
        SwitchRow("Tekrar dinle", null, showRecent) { s.homeShowRecent.set(it) }
        SwitchRow("Takip ettiklerinden", null, showFollowed) { s.homeShowFollowed.set(it) }
        SwitchRow("Trendler", null, showTrending) { s.homeShowTrending.set(it) }
        SwitchRow("Albümler", null, showAlbums) { s.homeShowAlbums.set(it) }
        Choice("İçerik ülkesi (trendler)", COUNTRIES, country) {
            app.setCountry(it)
            app.toast("Ana sayfayı yenileyince geçerli olur")
        }

        // ------------------------------------------------------------ oynatıcı düzeni
        Group("Oynatıcı")
        PlayerStylePicker(playerStyle) { s.playerStyle.set(it) }
        Choice(
            "Kapak",
            listOf("large" to "Büyük", "medium" to "Orta", "small" to "Küçük", "none" to "Kapaksız"),
            coverSize,
        ) { s.coverSize.set(it) }
        Txt(
            "Kapaksız modda şarkı adı dev yazıyla ekranı kaplar.",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Choice(
            "Sözler nerede görünsün",
            listOf("button" to "Düğmeyle", "preview" to "Kapağın altında", "auto" to "Otomatik aç"),
            lyricsPlacement,
        ) { s.lyricsPlacement.set(it) }
        SwitchRow("Ses çubuğu", "Oynatıcıda cihaz sesini ayarlayan çubuk (Apple Music tarzında).", volumeBar) { s.playerVolumeBar.set(it) }

        // ------------------------------------------------------------ sözler
        Group("Sözler")
        Choice(
            "Animasyon",
            listOf("apple" to "Apple Music", "word" to "Kelime kelime", "fill" to "Dolgu", "glow" to "Parlama", "focus" to "Odak"),
            lyricsAnim,
        ) { s.lyricsAnimation.set(it) }
        LyricsPreview(lyricsAnim, lyricsAlign)
        Choice("Hizalama", listOf("start" to "Sola", "center" to "Ortala"), lyricsAlign) { s.lyricsAlign.set(it) }
        Choice("Yazı boyutu", listOf(22 to "Küçük", 26 to "Orta", 32 to "Büyük", 38 to "Dev"), lyricsSize) { s.lyricsTextSize.set(it) }
        Choice("Kalınlık", listOf("medium" to "İnce", "bold" to "Kalın", "extrabold" to "Çok kalın"), lyricsWeight) { s.lyricsWeight.set(it) }
        Choice("Satır aralığı", listOf("tight" to "Sıkı", "normal" to "Normal", "wide" to "Geniş"), lyricsSpacing) { s.lyricsSpacing.set(it) }
        Choice("Çalan satırın rengi", listOf("accent" to "Vurgu rengi", "ink" to "Yazı rengi"), lyricsHighlight) { s.lyricsHighlight.set(it) }
        SwitchRow("BÜYÜK HARF", "Sözleri büyük harfle gösterir.", lyricsUpper) { s.lyricsUppercase.set(it) }
        SwitchRow("Uzak satırları bulanıklaştır", "Çalan satıra odaklanır (Android 12 ve üstü).", lyricsBlur) { s.lyricsBlur.set(it) }
        SwitchRow("Sözler açıkken ekran kapanmasın", null, keepScreen) { s.keepScreenOnLyrics.set(it) }

        // ------------------------------------------------------------ oynatıcı görselleri
        Group("Oynatıcı görselleri")
        Choice(
            "Kapak animasyonu",
            listOf("off" to "Kapalı", "breathe" to "Nefes", "beat" to "Ritme göre"),
            coverAnim,
        ) { s.coverAnimation.set(it) }
        Choice(
            "Ses görselleştirici",
            listOf("off" to "Kapalı", "bars" to "Çubuklar", "wave" to "Dalga"),
            visualizer,
        ) { style ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (style == "off" || granted) s.visualizerStyle.set(style)
            else { pendingVisualizer = style; micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
        }
        Txt(
            "Görselleştirici ve ritim animasyonu, Android'in ses analiz aracını kullanır; bu yüzden ses izni ister. Mikrofon dinlenmez, yalnızca çalan müzik okunur.",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
        )

        // ------------------------------------------------------------ akıcılık
        Group("Akıcılık")
        Choice(
            "Kare hızı",
            listOf("high" to "Yüksek (en yüksek, ör. 144 Hz)", "low" to "Düşük (60 Hz, pil dostu)"),
            frameRate,
        ) { s.frameRate.set(it) }
        Txt(
            "Yüksekte tüm arayüz ve animasyonlar ekranın en yüksek hızında çizilir.",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
        )
        SwitchRow("Hız göstergesi", "Köşede ekran yenileme hızını (Hz) ve kare sayısını (FPS) gösterir.", showFps) { s.showFps.set(it) }
        SwitchRow("Animasyonları azalt", "Geçişler sade ve kısa olur.", reduceMotion) { s.reduceMotion.set(it) }
        SwitchRow("Dokunma titreşimi", "Düğmelere basınca hafif titreşim.", haptics) { s.haptics.set(it) }

        // ------------------------------------------------------------ çalma
        Group("Çalma")
        Choice("Wi-Fi'da ses kalitesi", listOf("high" to "Yüksek", "normal" to "Normal", "low" to "Tasarruf"), quality) { s.audioQuality.set(it) }
        Choice(
            "Mobil veride ses kalitesi",
            listOf("same" to "Wi-Fi ile aynı", "high" to "Yüksek", "normal" to "Normal", "low" to "Tasarruf"),
            if (mobileQuality == "same" && dataSaver) "low" else mobileQuality,
        ) { s.mobileQuality.set(it); s.mobileDataSaver.set(false) }
        DataUsageCard(usage.mobileBytes, usage.wifiBytes) { app.dataUsage.reset(); app.toast("Sayaç sıfırlandı") }
        SwitchRow(
            "Bluetooth bağlanınca çal",
            "Kulaklık, hoparlör ya da araba bağlanınca kaldığın yerden başlar.",
            btAuto,
        ) { on ->
            if (!on) s.btAutoPlay.set(false)
            else if (android.os.Build.VERSION.SDK_INT >= 31 &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) btPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            else s.btAutoPlay.set(true)
        }
        SwitchRow("Sıradaki şarkıyı önceden yükle", "Geçişte beklemezsin; biraz daha veri kullanır.", preload) { s.preloadNext.set(it) }
        SwitchRow("Otomatik oynatma", "Sıra bitince benzer şarkılarla radyo gibi devam eder.", autoplay) { s.autoplay.set(it) }
        Choice(
            "Şarkılar arası geçiş",
            listOf(0 to "Kapalı", 2 to "2 sn", 4 to "4 sn", 6 to "6 sn", 8 to "8 sn", 12 to "12 sn"),
            crossfade,
        ) { s.crossfadeSec.set(it) }
        SwitchRow("Ses dengeleme", "Şarkılar arasındaki ses seviyesi farkını otomatik eşitler.", normalize) { s.normalizeVolume.set(it) }
        SwitchRow(
            "Kulaklık hareketleri",
            "Kulaklık tuşu: 1 dokunuş çal/duraklat · 2 dokunuş sonraki · 3 dokunuş önceki · basılı tut beğen",
            headset,
        ) { s.headsetGestures.set(it) }
        SwitchRow("Sessiz kısımları atla", "Şarkı başı ve sonundaki sessizliği atlar.", skipSilence) { s.skipSilence.set(it) }
        SwitchRow("Kulaklık çıkınca durdur", "Uygulama yeniden açılınca geçerli olur.", pauseOnDisconnect) { s.pauseOnDisconnect.set(it) }
        SwitchRow("Kaldığın yerden devam", "Uygulama açılınca son sıranı hazırlar.", resume) { s.resumeOnStart.set(it) }
        NavRow(
            "Hız ve ton",
            "${"%.2f".format(player.speed)}x · ${kotlin.math.round(player.semitones).toInt().let { if (it > 0) "+$it" else "$it" }} yarım ton",
        ) { ui.show(SheetSpec.Speed) }
        NavRow("Ekolayzer", "Ön ayarlar, bantlar, bas") { ui.navigate(Routes.EQUALIZER) }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row {
                Txt("Ses yükseltme", AdtTheme.type.bodyStrong, modifier = Modifier.weight(1f))
                Txt(if (boost == 0) "Kapalı" else "+${"%.1f".format(boost / 100f)} dB", AdtTheme.type.caption, color = c.muted)
            }
            AdtSlider(boost.toFloat(), { app.effects.setVolumeBoost(((it / 50).toInt()) * 50) }, range = 0f..1000f)
            Txt("Sesi kısık şarkılar için. Yüksek değerler bozulmaya yol açabilir.", AdtTheme.type.caption, color = c.muted)
        }
        Line()
        NavRow(
            "Uyku zamanlayıcısı",
            when (val r = sleep) {
                null -> "Kapalı"
                -1L -> "Şarkı bitince"
                else -> "Kalan ${formatMs(r ?: 0L)}"
            },
        ) { ui.show(SheetSpec.SleepTimer) }

        // ------------------------------------------------------------ depolama
        Group("Depolama ve indirme")
        Choice(
            "Akıllı önbellek",
            listOf(512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB"),
            cacheMb,
        ) {
            s.cacheSizeMb.set(it)
            app.toast("Yeni boyut uygulama yeniden açılınca geçerli olur")
        }
        Txt(
            "Dinlediğin şarkılar önbelleğe alınır; yer dolunca en eski dinlenen silinir. Şu an: ${formatBytes(cacheUsed)}",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
        )
        NavRow("Önbelleği temizle", "İndirmelerin silinmez") {
            ui.show(SheetSpec.Confirm("Önbellek temizlensin mi?", "Önbelleğe alınan şarkılar silinir. İndirdiklerin kalır.", "Temizle") {
                scope.launch {
                    withContext(Dispatchers.IO) { app.media.clearPlayerCache() }
                    cacheUsed = app.media.playerCacheBytes()
                    app.toast("Önbellek temizlendi")
                }
            })
        }
        SwitchRow("Yalnızca Wi-Fi ile indir", "Mobil veriyi korur.", wifiOnly) { app.downloads.setWifiOnly(it) }
        SwitchRow("Beğenilenleri otomatik indir", "Kalbe dokunduğun şarkı internetsiz de çalar.", autoDl) { s.autoDownloadLiked.set(it) }
        NavRow("İndirilenler", formatBytes(app.media.downloadCacheBytes())) { ui.navigate(Routes.DOWNLOADS) }

        // ------------------------------------------------------------ gizlilik
        Group("Gizlilik")
        SwitchRow("Arama geçmişini kaydet", null, saveSearch) { s.saveSearchHistory.set(it) }
        SwitchRow("Arama önerileri", "Yazarken öneri göster.", suggestions) { s.searchSuggestions.set(it) }
        SwitchRow(
            "Gizli mod",
            "Açıkken dinlediklerin ve aramaların geçmişe, Wrapped'e, istatistiklere ve önerilere yazılmaz. Kütüphane'nin üstünden de açıp kapatabilirsin.",
            pauseHistory,
        ) { s.pauseHistory.set(it) }
        NavRow("Arama geçmişini temizle", "") { scope.launch { app.dao.clearSearches(); app.toast("Arama geçmişi silindi") } }
        NavRow("Dinleme geçmişini temizle", "Wrapped ve istatistikler de sıfırlanır") {
            ui.show(SheetSpec.Confirm("Geçmiş silinsin mi?", "Dinleme geçmişin, Wrapped ve istatistiklerin sıfırlanır.", "Sil") {
                scope.launch { app.dao.clearHistory() }
            })
        }
        Txt(
            "ADT Music hiçbir takipçi, analiz ya da telemetri içermez. Hesap gerektirmez. Tüm verilerin yalnızca bu cihazda saklanır.",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // ------------------------------------------------------------ yedekleme
        Group("Yedekleme")
        NavRow("Kütüphaneyi yedekle", "Beğeniler, listeler ve sanatçılar tek bir dosyaya") {
            exportLauncher.launch("adt-music-yedek.json")
        }
        NavRow("Yedekten geri yükle", "Mevcut kütüphanene eklenir") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        // ------------------------------------------------------------ bildirimler
        Group("Bildirimler")
        SwitchRow("Yeni çıkanlar", "Takip ettiğin sanatçılar yeni şarkı paylaşınca haber verir.", releaseNotify) { s.releaseNotifications.set(it) }
        SwitchRow("Haftalık özet", "Pazar akşamları haftanın dinleme özetini gönderir.", weeklyNotify) { s.weeklyNotification.set(it) }
        NavRow("Rozetler ve seri", "") { ui.navigate(Routes.BADGES) }

        // ------------------------------------------------------------ güncelleme
        Group("Güncelleme")
        SwitchRow("Güncellemeleri otomatik denetle", "Yeni sürüm çıkınca açılışta haber verir.", autoUpdate) { s.autoUpdateCheck.set(it) }
        NavRow("Şimdi denetle", "Kurulu derleme: ${app.updater.currentBuild}") {
            scope.launch {
                val info = runCatching { app.updater.check(force = true) }.getOrNull()
                if (info != null) ui.show(SheetSpec.Update) else app.toast("Uygulaman güncel")
            }
        }

        // ------------------------------------------------------------ hakkında
        Group("Hakkında")
        NavRow("Sürüm", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") { version++ }
        NavRow("Bildirim ayarları", "Çalma kontrolleri için bildirim izni") {
            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
        NavRow("Kaynak kod", "github.com/alideniztartma/adt-music") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alideniztartma/adt-music")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        NavRow("Sözler: LRCLIB", "Açık ve ücretsiz senkron söz veritabanı") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lrclib.net")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Txt(
            "Bu proje YouTube, Google LLC veya bağlı kuruluşlarıyla ilişkili değildir ve onlar tarafından onaylanmamıştır. Eğitim ve kişisel kullanım amacıyla geliştirilmiştir. YouTube verilerine NewPipeExtractor ile erişilir.",
            AdtTheme.type.caption, color = c.faint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        if (version >= 5) {
            Txt("♪ Ali Deniz için yapıldı.", AdtTheme.type.title, color = c.accent, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(LocalBottomInset.current + 24.dp))
    }
}

/** Seçilen söz animasyonunun canlı önizlemesi. */
@Composable
private fun LyricsPreview(animation: String, align: String) {
    val c = AdtTheme.colors
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "lyPrev")
    val f by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "f",
    )
    val text = "Gecenin ortasında yıldızlar söner"
    val style = AdtTheme.type.headline.copy(
        textAlign = if (align == "center") androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
    )
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(AdtTheme.shape)
            .background(c.surface)
            .padding(16.dp)
    ) {
        Txt("Bir tek senin adın kalır", style, color = c.faint, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        when (animation) {
            "fill" -> androidx.compose.foundation.text.BasicText(
                text, Modifier.fillMaxWidth(),
                style = style.merge(androidx.compose.ui.text.TextStyle(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to c.accent, f to c.accent, (f + 0.02f).coerceAtMost(1f) to c.ink, 1f to c.ink,
                ))),
            )
            "word" -> {
                val words = text.split(" ")
                val total = words.sumOf { it.length + 1 }
                val annotated = androidx.compose.ui.text.buildAnnotatedString {
                    var acc = 0
                    words.forEachIndexed { i, w ->
                        val start = acc.toFloat() / total
                        acc += w.length + 1
                        val end = acc.toFloat() / total
                        val col = when {
                            f >= end -> c.accent
                            f > start -> androidx.compose.ui.graphics.lerp(c.ink, c.accent, (f - start) / (end - start))
                            else -> c.ink
                        }
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = col)); append(w); pop()
                        if (i < words.size - 1) append(" ")
                    }
                }
                androidx.compose.foundation.text.BasicText(annotated, Modifier.fillMaxWidth(), style = style)
            }
            "glow" -> androidx.compose.foundation.text.BasicText(
                text, Modifier.fillMaxWidth(),
                style = style.copy(color = c.accent, shadow = androidx.compose.ui.graphics.Shadow(c.accent.copy(alpha = 0.7f), blurRadius = 24f)),
            )
            "apple" -> {
                val words = text.split(" ")
                val total = words.sumOf { it.length + 1 }
                val annotated = androidx.compose.ui.text.buildAnnotatedString {
                    var acc = 0
                    words.forEachIndexed { i, w ->
                        val start = acc.toFloat() / total
                        acc += w.length + 1
                        val end = acc.toFloat() / total
                        val t = ((f - start) / (end - start)).coerceIn(0f, 1f)
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.lerp(c.ink.copy(alpha = 0.35f), c.ink, t)))
                        append(w); pop()
                        if (i < words.size - 1) append(" ")
                    }
                }
                androidx.compose.foundation.text.BasicText(
                    annotated, Modifier.fillMaxWidth(),
                    style = style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = style.fontSize * 1.08f),
                )
            }
            else -> Txt(text, style.copy(fontSize = style.fontSize * 1.05f), color = c.ink, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(6.dp))
        Txt("Rüzgâr eser, şehir susar", style, color = c.muted, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Group(title: String) {
    val c = AdtTheme.colors
    Spacer(Modifier.height(24.dp))
    Txt(title.uppercase(), AdtTheme.type.label, color = c.accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    Line(color = c.ink)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AdtTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .press(role = androidx.compose.ui.semantics.Role.Switch, label = title) { onChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Txt(title, AdtTheme.type.bodyStrong)
                if (subtitle != null) Txt(subtitle, AdtTheme.type.caption, color = c.muted)
            }
            Toggle(checked, onChange)
        }
        Line()
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    val c = AdtTheme.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .press(label = title, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(title, AdtTheme.type.bodyStrong)
                Txt(value, AdtTheme.type.caption, color = c.muted)
            }
            AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 18.dp)
        }
        Line()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Txt(title, AdtTheme.type.bodyStrong)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    Chip(label, value == selected, { onSelect(value) })
                }
            }
        }
        Line()
    }
}

/** "Yeni sürüm var" paneli. */
@Composable
fun UpdateContent(ui: com.alideniz.adtmusic.ui.AppUi) {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    val scope = rememberCoroutineScope()
    val info by app.updater.available.collectAsState()
    val progress by app.updater.progress.collectAsState()
    Column(Modifier.padding(horizontal = 24.dp)) {
        Txt("YENİ SÜRÜM", AdtTheme.type.label, color = c.accent)
        Spacer(Modifier.height(4.dp))
        val i = info
        if (i == null) {
            Txt("Uygulaman güncel.", AdtTheme.type.headline)
            Spacer(Modifier.height(16.dp))
            com.alideniz.adtmusic.ui.components.Btn("Tamam", { ui.dismiss() }, Modifier.fillMaxWidth(), style = com.alideniz.adtmusic.ui.components.BtnStyle.OUTLINE)
        } else {
            Txt(i.title, AdtTheme.type.headline)
            Txt("Derleme ${app.updater.currentBuild} → ${i.build} · ${formatBytes(i.sizeBytes)}", AdtTheme.type.caption, color = c.muted)
            if (i.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(AdtTheme.shape)
                        .background(c.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) { Txt(i.notes, AdtTheme.type.body) }
            }
            Spacer(Modifier.height(16.dp))
            if (progress >= 0f) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.line)) {
                    Box(Modifier.fillMaxWidth(progress).height(6.dp).background(c.accent))
                }
                Spacer(Modifier.height(6.dp))
                Txt("İndiriliyor… %${(progress * 100).toInt()}", AdtTheme.type.caption, color = c.muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.alideniz.adtmusic.ui.components.Btn("Sonra", { ui.dismiss() }, Modifier.weight(1f), style = com.alideniz.adtmusic.ui.components.BtnStyle.OUTLINE)
                    com.alideniz.adtmusic.ui.components.Btn("Güncelle", {
                        if (!app.updater.canInstall()) {
                            app.toast("Önce ADT Music'e uygulama yükleme izni ver, sonra tekrar dokun")
                            app.updater.openInstallPermission()
                        } else {
                            scope.launch {
                                runCatching { app.updater.downloadAndInstall(i) }
                                    .onFailure { app.toast("Güncelleme indirilemedi") }
                            }
                        }
                    }, Modifier.weight(1f), icon = AdtIcons.Download, style = com.alideniz.adtmusic.ui.components.BtnStyle.ACCENT)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
