package com.alideniz.adtmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.clip
import com.alideniz.adtmusic.ui.screens.FollowingScreen
import com.alideniz.adtmusic.ui.screens.WrappedScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.YouTubeRepository
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.components.SheetHost
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.screens.ArtistScreen
import com.alideniz.adtmusic.ui.screens.CollectionScreen
import com.alideniz.adtmusic.ui.screens.DownloadsScreen
import com.alideniz.adtmusic.ui.screens.EqualizerScreen
import com.alideniz.adtmusic.ui.screens.HistoryScreen
import com.alideniz.adtmusic.ui.screens.HomeScreen
import com.alideniz.adtmusic.ui.screens.LibraryScreen
import com.alideniz.adtmusic.ui.screens.LikedScreen
import com.alideniz.adtmusic.ui.screens.LocalPlaylistScreen
import com.alideniz.adtmusic.ui.screens.MiniPlayer
import com.alideniz.adtmusic.ui.screens.MoodScreen
import com.alideniz.adtmusic.ui.screens.PlayerScreen
import com.alideniz.adtmusic.ui.screens.SearchScreen
import com.alideniz.adtmusic.ui.screens.SettingsScreen
import com.alideniz.adtmusic.ui.screens.StatsScreen
import com.alideniz.adtmusic.ui.theme.AdtOrange
import com.alideniz.adtmusic.ui.theme.AdtTheme
import com.alideniz.adtmusic.ui.theme.rememberCoverAccent
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingLink by mutableStateOf<String?>(null)
    private var pendingOpenPlayer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        container.player.connect()

        setContent {
            val settings = container.settings
            val themeMode by settings.theme.flow.collectAsState()
            val dynamic by settings.dynamicColor.flow.collectAsState()
            val accentInt by settings.accentColor.flow.collectAsState()
            val pureBlack by settings.pureBlack.flow.collectAsState()
            val cornerStyle by settings.cornerStyle.flow.collectAsState()
            val fontScale by settings.fontScale.flow.collectAsState()
            val reduceMotion by settings.reduceMotion.flow.collectAsState()
            val highRefresh by settings.highRefreshRate.flow.collectAsState()
            val playerState by container.player.state.collectAsState()
            val fixedAccent = Color(accentInt)
            val coverAccent = rememberCoverAccent(playerState.current?.thumbnail, fallback = fixedAccent)
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(highRefresh) { applyRefreshRate(highRefresh) }

            AdtTheme(
                themeMode = themeMode,
                accent = if (dynamic) coverAccent else fixedAccent,
                pureBlack = pureBlack,
                corner = when (cornerStyle) {
                    "soft" -> 10.dp
                    "round" -> 22.dp
                    else -> 0.dp
                },
                fontScale = fontScale,
                reduceMotion = reduceMotion,
            ) {
                AdtRoot(
                    pendingLink = pendingLink,
                    onLinkHandled = { pendingLink = null },
                    openPlayer = pendingOpenPlayer,
                    onOpenPlayerHandled = { pendingOpenPlayer = false },
                )
            }
        }
    }

    private var wantHighRefresh = true

    /**
     * Ekranın desteklediği en yüksek yenileme hızını (90/120/144 Hz) ister.
     * Bazı telefonlar (Xiaomi, Samsung, OnePlus) uygulamalara varsayılan olarak 60 Hz verir;
     * pencere için tercih edilen ekran modunu açıkça seçmek bunu aşar.
     */
    private fun applyRefreshRate(high: Boolean) {
        wantHighRefresh = high
        runCatching {
            @Suppress("DEPRECATION")
            val d = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            d ?: return
            val current = d.mode
            val modes = d.supportedModes.toList()
            val best = if (high) {
                val sameRes = modes.filter {
                    it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
                }.maxByOrNull { it.refreshRate }
                val fastest = modes.maxWithOrNull(
                    compareBy<android.view.Display.Mode> { it.refreshRate }
                        .thenBy { it.physicalWidth * it.physicalHeight }
                )
                // Aynı çözünürlükte en yüksek hız yoksa (ör. 144 Hz yalnızca FHD'de) en hızlı modu seç.
                if (fastest != null && sameRes != null && fastest.refreshRate > sameRes.refreshRate + 1f) fastest else sameRes
            } else null
            val attrs = window.attributes
            attrs.preferredDisplayModeId = best?.modeId ?: 0
            attrs.preferredRefreshRate = best?.refreshRate ?: 0f
            window.attributes = attrs

            // Android 15+: görünüm ağacının yüksek kare hızı istediğini bildir.
            if (Build.VERSION.SDK_INT >= 35) {
                val category = if (high) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
                window.decorView.setRequestedFrameRate(category)
                findViewById<ViewGroup>(android.R.id.content)?.let { root ->
                    root.setRequestedFrameRate(category)
                    for (i in 0 until root.childCount) root.getChildAt(i).setRequestedFrameRate(category)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRefreshRate(wantHighRefresh)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyRefreshRate(wantHighRefresh)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) pendingOpenPlayer = true
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (!link.isNullOrBlank()) pendingLink = link
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Ana sayfa", AdtIcons.Home),
    Tab(Routes.SEARCH, "Ara", AdtIcons.Search),
    Tab(Routes.LIBRARY, "Kütüphane", AdtIcons.Library),
)

@Composable
private fun AdtRoot(
    pendingLink: String?,
    onLinkHandled: () -> Unit,
    openPlayer: Boolean,
    onOpenPlayerHandled: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.container
    val c = AdtTheme.colors
    val reduce = AdtTheme.reduceMotion
    val nav = rememberNavController()
    val ui = remember(nav) {
        AppUi(
            navigateImpl = { route -> navigateTo(nav, route) },
            backImpl = { nav.popBackStack() },
        )
    }
    val playerState by app.player.state.collectAsState()
    val hasSong = playerState.current != null
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val keepOnLyrics by app.settings.keepScreenOnLyrics.flow.collectAsState()
    val haptics by app.settings.haptics.flow.collectAsState()

    // Bildirim izni (Android 13+): çalma kontrolleri bildirimde görünsün.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val start = app.settings.startTab.value
        if (start != Routes.HOME && TABS.any { it.route == start }) navigateTo(nav, start)
    }

    // Sözler açıkken ekran kapanmasın
    val keepScreenOn = keepOnLyrics && ui.playerExpanded && ui.lyricsMode && playerState.isPlaying
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    // Dışarıdan gelen YouTube bağlantıları
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        onLinkHandled()
        val list = YouTubeRepository.playlistIdOf(link)
        val video = YouTubeRepository.videoIdOf(link)
        when {
            video != null -> runCatching { app.youtube.song(video) }
                .onSuccess { app.player.playRadio(it); ui.openPlayer() }
                .onFailure { app.toast("Bağlantı açılamadı") }
            list != null -> ui.navigate(
                Routes.collection("https://www.youtube.com/playlist?list=$list", CollectionKind.PLAYLIST)
            )
            else -> app.toast("Bu bağlantı tanınmadı")
        }
    }
    LaunchedEffect(openPlayer, hasSong) {
        if (openPlayer && hasSong) {
            ui.openPlayer()
            onOpenPlayerHandled()
        }
    }

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + 61.dp + if (hasSong) 66.dp else 0.dp

    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    CompositionLocalProvider(
        LocalAppUi provides ui,
        LocalBottomInset provides bottomInset,
        com.alideniz.adtmusic.ui.components.LocalHapticsEnabled provides haptics,
    ) {
        Box(Modifier.fillMaxSize().background(c.paper)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = {
                    if (reduce) fadeIn(tween(150))
                    else slideInHorizontally(tween(380, easing = emphasized)) { it / 5 } + fadeIn(tween(260))
                },
                exitTransition = {
                    if (reduce) fadeOut(tween(120))
                    else slideOutHorizontally(tween(380, easing = emphasized)) { -it / 12 } + fadeOut(tween(200))
                },
                popEnterTransition = {
                    if (reduce) fadeIn(tween(150))
                    else slideInHorizontally(tween(380, easing = emphasized)) { -it / 12 } + fadeIn(tween(260))
                },
                popExitTransition = {
                    if (reduce) fadeOut(tween(120))
                    else slideOutHorizontally(tween(320, easing = emphasized)) { it / 5 } + fadeOut(tween(200))
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.HOME) { HomeScreen() }
                composable(Routes.SEARCH) { SearchScreen() }
                composable(Routes.LIBRARY) { LibraryScreen() }
                composable(Routes.LIKED) { LikedScreen() }
                composable(Routes.DOWNLOADS) { DownloadsScreen() }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.STATS) { StatsScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.EQUALIZER) { EqualizerScreen() }
                composable(Routes.WRAPPED) { WrappedScreen() }
                composable(Routes.FOLLOWING) { FollowingScreen() }
                composable(
                    Routes.COLLECTION,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType; defaultValue = "" },
                        navArgument("kind") { type = NavType.StringType; defaultValue = CollectionKind.PLAYLIST.name },
                    ),
                ) { entry ->
                    val url = entry.arguments?.getString("url").orEmpty()
                    val kind = runCatching {
                        CollectionKind.valueOf(entry.arguments?.getString("kind").orEmpty())
                    }.getOrDefault(CollectionKind.PLAYLIST)
                    CollectionScreen(url, kind)
                }
                composable(
                    Routes.ARTIST,
                    arguments = listOf(
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("thumb") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    ArtistScreen(
                        entry.arguments?.getString("name").orEmpty(),
                        entry.arguments?.getString("thumb")?.ifBlank { null },
                    )
                }
                composable(
                    Routes.PLAYLIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    LocalPlaylistScreen(entry.arguments?.getLong("id") ?: -1L)
                }
                composable(
                    Routes.MOOD,
                    arguments = listOf(
                        navArgument("label") { type = NavType.StringType; defaultValue = "" },
                        navArgument("query") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    MoodScreen(
                        entry.arguments?.getString("label").orEmpty(),
                        entry.arguments?.getString("query").orEmpty(),
                    )
                }
            }

            // ---- alt bölüm: mini oynatıcı + sekmeler
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                MessageHost()
                AnimatedVisibility(
                    visible = hasSong,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
                        MiniPlayer()
                    }
                }
                BottomTabs(currentRoute) { route -> navigateTo(nav, route) }
            }

            // ---- tam ekran oynatıcı
            AnimatedVisibility(
                visible = ui.playerExpanded && hasSong,
                enter = if (reduce) fadeIn(tween(150)) else
                    slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(tween(180)),
                exit = if (reduce) fadeOut(tween(120)) else
                    slideOutVertically(spring(dampingRatio = 1f, stiffness = 520f)) { it } + fadeOut(tween(220)),
            ) {
                PlayerScreen()
            }

            // Geri tuşu önce açık paneli, sonra oynatıcıyı kapatır (sonra eklenen işleyici önceliklidir).
            BackHandler(enabled = ui.playerExpanded) { ui.playerExpanded = false }
            SheetHost(ui)
            val showFps by app.settings.showFps.flow.collectAsState()
            if (showFps) FpsOverlay(Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * Alt sekmeler her ekrandan çalışır: sekmeye dokununca o sekmenin
 * köküne dönülür (ayarlar, albüm, sanatçı vb. sayfalar kapanır).
 */
private fun navigateTo(nav: NavHostController, route: String) {
    if (TABS.any { it.route == route }) {
        val startId = nav.graph.findStartDestination().id
        nav.navigate(route) {
            popUpTo(startId) { inclusive = false }
            launchSingleTop = true
        }
    } else {
        nav.navigate(route) { launchSingleTop = true }
    }
}

/** Bir sayfanın hangi sekmeye ait olduğu (alt çubukta işaretlemek için). */
private fun tabOf(route: String?): String? = when (route) {
    null -> null
    Routes.HOME, Routes.SEARCH, Routes.LIBRARY -> route
    Routes.LIKED, Routes.DOWNLOADS, Routes.HISTORY, Routes.STATS, Routes.PLAYLIST,
    Routes.FOLLOWING, Routes.WRAPPED, Routes.EQUALIZER -> Routes.LIBRARY
    else -> null
}

@Composable
private fun BottomTabs(currentRoute: String?, onSelect: (String) -> Unit) {
    val c = AdtTheme.colors
    val activeTab = tabOf(currentRoute)
    Column(Modifier.fillMaxWidth().background(c.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            TABS.forEach { tab ->
                val selected = activeTab == tab.route
                val barWidth by animateDpAsState(if (selected) 28.dp else 0.dp, spring(dampingRatio = 0.6f), label = "tabBar")
                val tint by animateColorAsState(if (selected) c.ink else c.faint, tween(200), label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .press(label = tab.label) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(barWidth).height(3.dp).background(c.accent))
                    Spacer(Modifier.height(9.dp))
                    AdtIcon(tab.icon, null, tint = tint, size = 22.dp)
                    Spacer(Modifier.height(4.dp))
                    Txt(tab.label.uppercase(), AdtTheme.type.label.copy(fontSize = AdtTheme.type.label.fontSize * 0.85f), color = tint)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Kısa bildirimler (ör. "Sıraya eklendi"). */
@Composable
private fun MessageHost() {
    val app = LocalContext.current.container
    val c = AdtTheme.colors
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        app.messages.collect { m ->
            message = m
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
    ) {
        var last by remember { mutableStateOf("") }
        message?.let { last = it }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(AdtTheme.shape)
                .background(c.ink)
                .press(label = "Kapat") { message = null }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Txt(last, AdtTheme.type.bodyStrong, color = c.paper, maxLines = 2)
        }
    }
}

/** Ekranın o anki yenileme hızı ve uygulamanın çizdiği kare sayısı (Ayarlar → Akıcılık). */
@Composable
private fun FpsOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fps by remember { mutableStateOf(0) }
    var hz by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var frames = 0
        var start = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (start == 0L) start = t
                frames++
                if (t - start >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    start = t
                    @Suppress("DEPRECATION")
                    val d = if (Build.VERSION.SDK_INT >= 30) (context as? Activity)?.display
                    else (context as? Activity)?.windowManager?.defaultDisplay
                    hz = d?.refreshRate ?: 0f
                }
            }
        }
    }
    Box(
        modifier
            .statusBarsPadding()
            .padding(8.dp)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Txt("${hz.toInt()} Hz · $fps FPS", AdtTheme.type.label, color = androidx.compose.ui.graphics.Color(0xFF7CFF8A))
    }
}
