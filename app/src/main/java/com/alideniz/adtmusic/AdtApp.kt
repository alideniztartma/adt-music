@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.alideniz.adtmusic.data.LyricsRepository
import com.alideniz.adtmusic.data.NewPipeDownloader
import com.alideniz.adtmusic.data.Settings
import com.alideniz.adtmusic.data.YouTubeRepository
import com.alideniz.adtmusic.data.db.AdtDatabase
import com.alideniz.adtmusic.playback.AudioEffects
import com.alideniz.adtmusic.playback.DownloadTracker
import com.alideniz.adtmusic.playback.MediaStack
import com.alideniz.adtmusic.playback.PlayerConnection
import com.alideniz.adtmusic.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class AdtApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.httpClient)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("covers"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .crossfade(220)
        .respectCacheHeaders(false)
        .build()
}

/** Uygulama genelindeki tekil nesneler (basit, elle bağımlılık enjeksiyonu). */
class AppContainer(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    val settings = Settings(context)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val database: AdtDatabase = AdtDatabase.create(context)
    val dao = database.dao()

    /** Beğenilen şarkı kimlikleri; bütün ekranlar aynı akışı paylaşır. */
    val likedIds: StateFlow<Set<String>> = dao.likedIds()
        .map { it.toSet() }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    val youtube = YouTubeRepository()
    val lyrics = LyricsRepository(httpClient, dao)

    val media = MediaStack(context, settings, youtube, httpClient)
    val downloads = DownloadTracker(context, media, settings, dao, appScope)
    val effects = AudioEffects(settings)
    val sleepTimer = SleepTimer(appScope)
    val player = PlayerConnection(context, appScope)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Kullanıcıya gösterilecek kısa bildirimler (hata, "listeye eklendi" vb.). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun toast(message: String) {
        mainHandler.post { _messages.tryEmit(message) }
    }

    init {
        NewPipe.init(
            NewPipeDownloader(httpClient),
            Localization("tr", "TR"),
            ContentCountry("TR"),
        )
    }
}

val Context.container: AppContainer get() = (applicationContext as AdtApp).container
