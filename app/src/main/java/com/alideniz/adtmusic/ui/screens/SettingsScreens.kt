package com.alideniz.adtmusic.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val dynamic by s.dynamicColor.flow.collectAsState()
    val quality by s.audioQuality.flow.collectAsState()
    val autoplay by s.autoplay.flow.collectAsState()
    val skipSilence by s.skipSilence.flow.collectAsState()
    val cacheMb by s.cacheSizeMb.flow.collectAsState()
    val wifiOnly by s.downloadWifiOnly.flow.collectAsState()
    val saveSearch by s.saveSearchHistory.flow.collectAsState()
    val pauseHistory by s.pauseHistory.flow.collectAsState()
    val resume by s.resumeOnStart.flow.collectAsState()
    val lyricsSize by s.lyricsTextSize.flow.collectAsState()
    val sleep by app.sleepTimer.remaining.collectAsState()
    var cacheUsed by remember { mutableLongStateOf(app.media.playerCacheBytes()) }
    var version by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Ayarlar", onBack = { ui.back() })

        Group("Görünüm")
        Choice("Tema", listOf("system" to "Sistem", "light" to "Açık", "dark" to "Koyu"), theme) { s.theme.set(it) }
        SwitchRow("Kapaktan renk al", "Vurgu rengi çalan şarkının kapağına göre değişir.", dynamic) { s.dynamicColor.set(it) }
        Choice(
            "Söz yazı boyutu",
            listOf(22 to "Küçük", 26 to "Orta", 32 to "Büyük"),
            lyricsSize,
        ) { s.lyricsTextSize.set(it) }

        Group("Çalma")
        Choice("Ses kalitesi", listOf("high" to "Yüksek", "normal" to "Normal", "low" to "Tasarruf"), quality) { s.audioQuality.set(it) }
        SwitchRow("Otomatik oynatma", "Sıra bitince benzer şarkılarla radyo gibi devam eder.", autoplay) { s.autoplay.set(it) }
        SwitchRow("Sessiz kısımları atla", "Şarkı başı ve sonundaki sessizliği atlar.", skipSilence) { s.skipSilence.set(it) }
        SwitchRow("Kaldığın yerden devam", "Uygulama açılınca son sıranı hazırlar.", resume) { s.resumeOnStart.set(it) }
        NavRow("Ekolayzer", "Ön ayarlar, bantlar, bas") { ui.navigate(Routes.EQUALIZER) }
        NavRow(
            "Uyku zamanlayıcısı",
            when (val r = sleep) {
                null -> "Kapalı"
                -1L -> "Şarkı bitince"
                else -> "Kalan ${formatMs(r ?: 0L)}"
            },
        ) { ui.show(SheetSpec.SleepTimer) }

        Group("Depolama")
        Choice(
            "Akıllı önbellek",
            listOf(512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB"),
            cacheMb,
        ) {
            s.cacheSizeMb.set(it)
            app.toast("Yeni boyut uygulama yeniden açılınca geçerli olur")
        }
        Txt(
            "Dinlediğin şarkılar önbelleğe alınır; yer dolunca en eski dinlenen otomatik silinir. Şu an: ${formatBytes(cacheUsed)}",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
        )
        NavRow("Önbelleği temizle", "İndirmelerin silinmez") {
            ui.show(SheetSpec.Confirm("Önbellek temizlensin mi?", "Çevrimdışı dinleme için önbelleğe alınan şarkılar silinir. İndirdiklerin kalır.", "Temizle") {
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.media.clearPlayerCache() }
                    cacheUsed = app.media.playerCacheBytes()
                    app.toast("Önbellek temizlendi")
                }
            })
        }
        SwitchRow("Yalnızca Wi-Fi ile indir", "Mobil veriyi korur.", wifiOnly) { app.downloads.setWifiOnly(it) }
        NavRow("İndirilenler", formatBytes(app.media.downloadCacheBytes())) { ui.navigate(Routes.DOWNLOADS) }

        Group("Gizlilik")
        SwitchRow("Arama geçmişini kaydet", null, saveSearch) { s.saveSearchHistory.set(it) }
        SwitchRow("Dinleme geçmişini duraklat", "Açıkken dinlediklerin geçmişe ve istatistiklere eklenmez.", pauseHistory) { s.pauseHistory.set(it) }
        Txt(
            "ADT Music hiçbir takipçi, analiz ya da telemetri içermez. Hesap gerektirmez. Beğenilerin, listelerin, geçmişin ve indirmelerin yalnızca bu cihazda saklanır.",
            AdtTheme.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Group("Hakkında")
        NavRow("Sürüm", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") { version++ }
        NavRow("Bildirim ayarları", "Çalma kontrolleri için bildirim izni") {
            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
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
