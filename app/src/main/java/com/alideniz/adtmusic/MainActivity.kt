package com.alideniz.adtmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
            val playerState by container.player.state.collectAsState()
            val coverAccent = rememberCoverAccent(playerState.current?.thumbnail)
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

            AdtTheme(themeMode = themeMode, accent = if (dynamic) coverAccent else AdtOrange) {
                AdtRoot(
                    pendingLink = pendingLink,
                    onLinkHandled = { pendingLink = null },
                    openPlayer = pendingOpenPlayer,
                    onOpenPlayerHandled = { pendingOpenPlayer = false },
                )
            }
        }
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

    // Bildirim izni (Android 13+): çalma kontrolleri bildirimde görünsün.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    CompositionLocalProvider(LocalAppUi provides ui, LocalBottomInset provides bottomInset) {
        Box(Modifier.fillMaxSize().background(c.paper)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(140)) },
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
                if (hasSong) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
                    MiniPlayer()
                }
                BottomTabs(currentRoute) { route -> navigateTo(nav, route) }
            }

            // ---- tam ekran oynatıcı
            AnimatedVisibility(
                visible = ui.playerExpanded && hasSong,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
            ) {
                PlayerScreen()
            }

            // Geri tuşu önce açık paneli, sonra oynatıcıyı kapatır (sonra eklenen işleyici önceliklidir).
            BackHandler(enabled = ui.playerExpanded) { ui.playerExpanded = false }
            SheetHost(ui)
        }
    }
}

private fun navigateTo(nav: NavHostController, route: String) {
    if (TABS.any { it.route == route }) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        nav.navigate(route) { launchSingleTop = true }
    }
}

@Composable
private fun BottomTabs(currentRoute: String?, onSelect: (String) -> Unit) {
    val c = AdtTheme.colors
    Column(Modifier.fillMaxWidth().background(c.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .press(label = tab.label) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                    )
                    Spacer(Modifier.height(9.dp))
                    AdtIcon(tab.icon, null, tint = if (selected) c.ink else c.faint, size = 22.dp)
                    Spacer(Modifier.height(4.dp))
                    Txt(tab.label.uppercase(), AdtTheme.type.label.copy(fontSize = AdtTheme.type.label.fontSize * 0.85f), color = if (selected) c.ink else c.faint)
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
        enter = slideInVertically { it } + fadeIn(),
        exit = fadeOut(),
    ) {
        var last by remember { mutableStateOf("") }
        message?.let { last = it }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .background(c.ink)
                .border(1.dp, c.ink)
                .press(label = "Kapat") { message = null }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Txt(last, AdtTheme.type.bodyStrong, color = c.paper, maxLines = 2)
        }
    }
}
