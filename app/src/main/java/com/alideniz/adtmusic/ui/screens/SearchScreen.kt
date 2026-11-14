package com.alideniz.adtmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alideniz.adtmusic.AppContainer
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.CollectionKind
import com.alideniz.adtmusic.data.SearchCursor
import com.alideniz.adtmusic.data.SearchFilter
import com.alideniz.adtmusic.data.SearchItem
import com.alideniz.adtmusic.data.YouTubeRepository
import com.alideniz.adtmusic.data.db.SearchHistoryEntity
import com.alideniz.adtmusic.ui.AppUi
import com.alideniz.adtmusic.ui.LocalAppUi
import com.alideniz.adtmusic.ui.LocalBottomInset
import com.alideniz.adtmusic.ui.Routes
import com.alideniz.adtmusic.ui.SheetSpec
import com.alideniz.adtmusic.ui.components.ArtistRow
import com.alideniz.adtmusic.ui.components.Chip
import com.alideniz.adtmusic.ui.components.CollectionRow
import com.alideniz.adtmusic.ui.components.IconBtn
import com.alideniz.adtmusic.ui.components.Line
import com.alideniz.adtmusic.ui.components.LoadingBlock
import com.alideniz.adtmusic.ui.components.MessageBlock
import com.alideniz.adtmusic.ui.components.SongRow
import com.alideniz.adtmusic.ui.components.TextInput
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.icons.AdtIcon
import com.alideniz.adtmusic.ui.icons.AdtIcons
import com.alideniz.adtmusic.ui.theme.AdtTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(private val app: AppContainer) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var filter by mutableStateOf(SearchFilter.SONGS)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var results by mutableStateOf<List<SearchItem>>(emptyList())
        private set
    var correction by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var cursor: SearchCursor? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        suggestJob?.cancel()
        if (q.isBlank()) {
            suggestions = emptyList()
            return
        }
        if (!app.settings.searchSuggestions.value) return
        suggestJob = viewModelScope.launch {
            delay(220)
            suggestions = app.youtube.suggestions(q)
        }
    }

    fun clear() {
        query = ""
        submitted = ""
        suggestions = emptyList()
        results = emptyList()
        error = null
        cursor = null
    }

    /** Arama ya da yapıştırılan YouTube bağlantısı. */
    fun submit(q: String = query, ui: AppUi) {
        val text = q.trim()
        if (text.isEmpty()) return
        if (handleLink(text, ui)) return
        query = text
        submitted = text
        suggestions = emptyList()
        if (app.settings.saveSearchHistory.value) {
            viewModelScope.launch { app.dao.saveSearch(SearchHistoryEntity(text, System.currentTimeMillis())) }
        }
        runSearch()
    }

    fun chooseFilter(f: SearchFilter) {
        if (f == filter) return
        filter = f
        if (submitted.isNotBlank()) runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        loading = true
        error = null
        results = emptyList()
        cursor = null
        val q = submitted
        val f = filter
        searchJob = viewModelScope.launch {
            runCatching { app.youtube.search(q, f) }
                .onSuccess {
                    results = it.items
                    cursor = it.cursor
                    correction = it.correction
                }
                .onFailure { error = it.message ?: "Arama başarısız" }
            loading = false
        }
    }

    fun loadMore() {
        val cur = cursor ?: return
        if (loadingMore || loading || !cur.hasMore) return
        loadingMore = true
        val f = filter
        viewModelScope.launch {
            runCatching { app.youtube.searchMore(cur, f) }
                .onSuccess { page ->
                    val existing = results
                    results = existing + page.items
                    cursor = page.cursor
                }
            loadingMore = false
        }
    }

    private fun handleLink(text: String, ui: AppUi): Boolean {
        if (!text.contains("youtu")) return false
        val list = YouTubeRepository.playlistIdOf(text)
        val video = YouTubeRepository.videoIdOf(text)
        if (list != null && !list.startsWith("RD") && video == null) {
            ui.navigate(Routes.collection("https://www.youtube.com/playlist?list=$list", CollectionKind.PLAYLIST))
            return true
        }
        if (video != null) {
            viewModelScope.launch {
                runCatching { app.youtube.song(video) }
                    .onSuccess { app.player.playRadio(it); ui.openPlayer() }
                    .onFailure { app.toast("Bağlantı açılamadı") }
            }
            query = ""
            return true
        }
        return false
    }
}

@Composable
fun SearchScreen() {
    val app = LocalContext.current.container
    val ui = LocalAppUi.current
    val c = AdtTheme.colors
    val activity = LocalContext.current as ComponentActivity
    val vm: SearchViewModel = viewModel(viewModelStoreOwner = activity) { SearchViewModel(app) }
    val history by remember { app.dao.searchHistory() }.collectAsState(initial = emptyList())
    val player by app.player.state.collectAsState()
    val likedIds by app.likedIds.collectAsState()
    val downloads by app.downloads.states.collectAsState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val typing = vm.query.isNotBlank() && vm.query != vm.submitted
    val showResults = vm.submitted.isNotBlank() && !typing

    LaunchedEffect(Unit) {
        if (vm.submitted.isBlank()) runCatching { focus.requestFocus() }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, showResults) {
        if (nearEnd && showResults) vm.loadMore()
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Txt("Ara", AdtTheme.type.display, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(16.dp))
                TextInput(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = "Şarkı, sanatçı, albüm ya da bağlantı",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = AdtTheme.type.bodyStrong,
                    focusRequester = focus,
                    onSubmit = {
                        vm.submit(ui = ui)
                        focusManager.clearFocus()
                    },
                    leading = { AdtIcon(AdtIcons.Search, null, size = 20.dp) },
                    trailing = {
                        if (vm.query.isNotEmpty()) IconBtn(AdtIcons.Close, "Temizle", { vm.clear(); runCatching { focus.requestFocus() } }, size = 36.dp, iconSize = 18.dp)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            typing -> {
                items(vm.suggestions, key = { "sg-$it" }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .press(label = s) { vm.submit(s, ui); focusManager.clearFocus() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AdtIcon(AdtIcons.Search, null, tint = c.faint, size = 18.dp)
                        Spacer(Modifier.width(16.dp))
                        Txt(s, AdtTheme.type.body, modifier = Modifier.weight(1f), maxLines = 1)
                        AdtIcon(AdtIcons.ChevronRight, null, tint = c.faint, size = 16.dp)
                    }
                }
            }

            showResults -> {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SearchFilter.entries) { f ->
                            Chip(f.label, f == vm.filter, { vm.chooseFilter(f) })
                        }
                    }
                    vm.correction?.let { fix ->
                        Txt(
                            "Bunu mu demek istedin: $fix", AdtTheme.type.caption, color = c.accent,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).press { vm.submit(fix, ui) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    vm.loading -> item { LoadingBlock() }
                    vm.error != null -> item {
                        MessageBlock("Arama yapılamadı", vm.error ?: "", icon = AdtIcons.Offline, action = "Tekrar dene", onAction = { vm.submit(vm.submitted, ui) })
                    }
                    vm.results.isEmpty() -> item {
                        MessageBlock("Sonuç yok", "“${vm.submitted}” için ${vm.filter.label.lowercase()} bulunamadı. Başka bir filtre dene.")
                    }
                    else -> {
                        val songs = vm.results.mapNotNull { (it as? SearchItem.SongItem)?.song }
                        itemsIndexed(vm.results, key = { i, item -> "r-$i-${item.hashCode()}" }) { _, item ->
                            when (item) {
                                is SearchItem.SongItem -> SongRow(
                                    song = item.song,
                                    onClick = {
                                        val idx = songs.indexOfFirst { it.id == item.song.id }
                                        if (vm.filter == SearchFilter.SONGS || vm.filter == SearchFilter.VIDEOS) {
                                            app.player.playRadio(item.song)
                                        } else {
                                            app.player.play(songs, idx.coerceAtLeast(0))
                                        }
                                    },
                                    onMore = { ui.show(SheetSpec.SongOptions(item.song)) },
                                    isCurrent = player.current?.id == item.song.id,
                                    isPlaying = player.isPlaying,
                                    liked = item.song.id in likedIds,
                                    download = downloads[item.song.id],
                                )
                                is SearchItem.CollectionItem -> CollectionRow(item.ref) {
                                    ui.navigate(Routes.collection(item.ref.url, item.ref.kind))
                                }
                                is SearchItem.ArtistItem -> ArtistRow(item.ref) {
                                    ui.navigate(Routes.artist(item.ref.name, item.ref.thumbnail))
                                }
                            }
                        }
                        if (vm.loadingMore) item { LoadingBlock() }
                    }
                }
            }

            else -> {
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt("SON ARAMALAR", AdtTheme.type.label, color = c.muted, modifier = Modifier.weight(1f))
                            TextLink("Temizle", { scope.launch { app.dao.clearSearches() } })
                        }
                    }
                    items(history, key = { "h-$it" }) { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .press(label = h) { vm.submit(h, ui) }
                                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AdtIcon(AdtIcons.History, null, tint = c.faint, size = 18.dp)
                            Spacer(Modifier.width(16.dp))
                            Txt(h, AdtTheme.type.body, modifier = Modifier.weight(1f), maxLines = 1)
                            IconBtn(AdtIcons.Close, "Sil", {
                                scope.launch { app.dao.deleteSearch(h) }
                            }, size = 40.dp, iconSize = 16.dp, tint = c.faint)
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
                item {
                    Txt("GÖZ AT", AdtTheme.type.label, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    Line()
                }
                itemsIndexed(MOODS, key = { _, m -> "m-${m.label}" }) { i, mood ->
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .press(label = mood.label) { ui.navigate(Routes.mood(mood.label, mood.query)) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt("%02d".format(i + 1), AdtTheme.type.caption, color = c.accent, modifier = Modifier.width(36.dp))
                            Txt(mood.label, AdtTheme.type.headline, modifier = Modifier.weight(1f))
                            AdtIcon(AdtIcons.ChevronRight, null, tint = c.ink, size = 20.dp)
                        }
                        Line()
                    }
                }
            }
        }

        item { Box(Modifier.height(LocalBottomInset.current + 24.dp)) }
    }
}
