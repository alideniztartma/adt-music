package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.appContainer
import com.alideniz.adtmusic.data.Settings
import com.alideniz.adtmusic.data.Song
import com.alideniz.adtmusic.data.db.AdtDao
import com.alideniz.adtmusic.platform.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

enum class DlState { QUEUED, DOWNLOADING, DONE, FAILED }

data class DlInfo(val state: DlState, val progress: Float)

/**
 * Masaüstü indirmeleri: ses dosyası parça parça (YouTube'un hız sınırına takılmadan)
 * uygulama klasörüne indirilir. İnternetsiz çalma bu dosyalardan yapılır.
 */
class DownloadTracker(
    private val context: Context,
    private val media: MediaStack,
    private val settings: Settings,
    private val dao: AdtDao,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, DlInfo>>(emptyMap())
    val states: StateFlow<Map<String, DlInfo>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val limiter = Semaphore(3)

    init {
        val done = media.downloadsDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.associate { it.nameWithoutExtension to DlInfo(DlState.DONE, 1f) }
            .orEmpty()
        _states.value = done
        // Yarım kalmış indirmeleri temizle
        media.downloadsDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private fun set(id: String, info: DlInfo?) {
        _states.value = if (info == null) _states.value - id else _states.value + (id to info)
    }

    fun download(song: Song) {
        if (_states.value[song.id]?.state == DlState.DONE || jobs.containsKey(song.id)) return
        set(song.id, DlInfo(DlState.QUEUED, 0f))
        jobs[song.id] = scope.launch(Dispatchers.IO) {
            try {
                dao.ensureSong(song)
                limiter.withPermit {
                    set(song.id, DlInfo(DlState.DOWNLOADING, 0f))
                    fetch(song.id)
                }
                set(song.id, DlInfo(DlState.DONE, 1f))
                // Sözleri de önceden al: internetsiz dinlerken de görünsün.
                runCatching { appContainer.lyrics.lyricsFor(song) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                set(song.id, DlInfo(DlState.FAILED, 0f))
            } finally {
                jobs.remove(song.id)
            }
        }
    }

    private suspend fun fetch(id: String) {
        var stream = media.resolve(id)
        val ext = when {
            stream.mimeType.orEmpty().contains("webm") -> "webm"
            else -> "m4a"
        }
        val part = File(media.downloadsDir, "$id.$ext.part")
        val target = File(media.downloadsDir, "$id.$ext")
        var total = -1L
        var pos = 0L
        var retried = false
        RandomAccessFile(part, "rw").use { out ->
            out.setLength(0)
            while (total < 0 || pos < total) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val end = if (total > 0) minOf(pos + CHUNK, total) - 1 else pos + CHUNK - 1
                val req = Request.Builder().url(stream.url).header("Range", "bytes=$pos-$end").build()
                val ok = media.mediaClient.newCall(req).execute().use { res ->
                    if (res.code == 403 && !retried) {
                        return@use false
                    }
                    if (!res.isSuccessful) throw IOException("İndirme başarısız (${res.code})")
                    if (total < 0) {
                        total = res.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: res.body?.contentLength()?.takeIf { res.code == 200 } ?: -1L
                    }
                    val body = res.body ?: throw IOException("Boş yanıt")
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                        }
                    }
                    if (res.code == 200) total = pos // sunucu aralığı yok saydı: hepsi geldi
                    true
                }
                if (!ok) {
                    // Akış adresinin süresi dolmuş: bir kez yeniden çöz
                    retried = true
                    media.invalidate(id)
                    stream = media.resolve(id)
                    continue
                }
                if (total > 0) set(id, DlInfo(DlState.DOWNLOADING, (pos.toFloat() / total).coerceIn(0f, 1f)))
                if (total < 0) break
            }
        }
        if (pos <= 0) throw IOException("Boş dosya")
        target.delete()
        if (!part.renameTo(target)) throw IOException("Dosya kaydedilemedi")
    }

    fun downloadAll(songs: List<Song>) = songs.forEach { if (_states.value[it.id]?.state != DlState.DONE) download(it) }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        scope.launch(Dispatchers.IO) {
            media.downloadsDir.listFiles { f -> f.name.startsWith("$id.") }?.forEach { it.delete() }
            withContext(Dispatchers.Main) { set(id, null) }
        }
    }

    fun removeAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.launch(Dispatchers.IO) {
            media.downloadsDir.listFiles()?.forEach { it.delete() }
            withContext(Dispatchers.Main) { _states.value = emptyMap() }
        }
    }

    /** Masaüstünde bağlantı türü ayrımı yok; ayar yalnızca saklanır. */
    fun setWifiOnly(wifiOnly: Boolean) {
        settings.downloadWifiOnly.set(wifiOnly)
    }

    companion object {
        /** YouTube büyük tek istekleri yavaşlattığı için 1 MB'lık parçalar. */
        private const val CHUNK = 1L shl 20
    }
}
