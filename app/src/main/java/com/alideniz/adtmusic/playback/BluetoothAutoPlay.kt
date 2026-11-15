package com.alideniz.adtmusic.playback

import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alideniz.adtmusic.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bluetooth kulaklık / hoparlör / araba bağlanınca (A2DP) müziği kaldığı yerden başlatır.
 * Sıra boşsa beğenilen şarkıları karışık çalar.
 */
class BluetoothAutoPlay : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_A2DP) return
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        if (state != BluetoothProfile.STATE_CONNECTED) return
        val app = context.applicationContext.container
        if (!app.settings.btAutoPlay.value) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    // Ses yönlendirmesi oturana kadar kısa bir bekleme
                    delay(1_200)
                    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    val future = MediaController.Builder(context, token).buildAsync()
                    val controller = withContext(Dispatchers.IO) { future.get() }
                    try {
                        if (controller.isPlaying) return@withTimeoutOrNull
                        if (controller.mediaItemCount == 0) {
                            val settings = app.settings
                            var songs = songsFromJson(settings.lastQueue.value)
                            var index = settings.lastIndex.value
                            var position = settings.lastPosition.value
                            if (songs.isEmpty()) {
                                songs = withContext(Dispatchers.IO) {
                                    app.dao.likedSongsOnce().map { it.toSong() }.shuffled().take(100)
                                }
                                index = 0; position = 0
                            }
                            if (songs.isEmpty()) return@withTimeoutOrNull
                            controller.setMediaItems(songs.map { it.toMediaItem() }, index.coerceIn(0, songs.size - 1), position)
                        }
                        controller.prepare()
                        controller.play()
                        app.toast("Bluetooth bağlandı, müzik başlıyor")
                        delay(1_500)
                    } finally {
                        controller.release()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_A2DP = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
    }
}
