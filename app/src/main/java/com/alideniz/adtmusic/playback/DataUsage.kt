package com.alideniz.adtmusic.playback

import android.content.Context
import android.net.ConnectivityManager
import com.alideniz.adtmusic.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class Usage(val month: String, val mobileBytes: Long, val wifiBytes: Long)

/** Müzik akışı ve indirmelerin aylık veri kullanımı (mobil / Wi-Fi ayrı). */
class DataUsage(private val context: Context, private val settings: Settings) {

    private val pendingMobile = AtomicLong(0)
    private val pendingWifi = AtomicLong(0)
    @Volatile private var meteredAt = 0L
    @Volatile private var metered = false

    private val _usage = MutableStateFlow(read())
    val usage: StateFlow<Usage> = _usage.asStateFlow()

    private fun monthKey() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    private fun read(): Usage {
        val m = monthKey()
        if (settings.dataMonth.value != m) {
            settings.dataMonth.set(m)
            settings.dataMobileBytes.set(0L)
            settings.dataWifiBytes.set(0L)
        }
        return Usage(m, settings.dataMobileBytes.value, settings.dataWifiBytes.value)
    }

    private fun isMetered(): Boolean {
        val now = System.currentTimeMillis()
        if (now - meteredAt > 5_000) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            metered = cm?.isActiveNetworkMetered == true
            meteredAt = now
        }
        return metered
    }

    fun record(bytes: Long) {
        if (bytes <= 0) return
        val total = if (isMetered()) pendingMobile.addAndGet(bytes) else pendingWifi.addAndGet(bytes)
        if (total > 512 * 1024) flush()
    }

    @Synchronized
    fun flush() {
        val mob = pendingMobile.getAndSet(0)
        val wifi = pendingWifi.getAndSet(0)
        if (mob == 0L && wifi == 0L) return
        val cur = read()
        settings.dataMobileBytes.set(cur.mobileBytes + mob)
        settings.dataWifiBytes.set(cur.wifiBytes + wifi)
        _usage.value = Usage(cur.month, cur.mobileBytes + mob, cur.wifiBytes + wifi)
    }

    @Synchronized
    fun reset() {
        pendingMobile.set(0); pendingWifi.set(0)
        settings.dataMobileBytes.set(0L)
        settings.dataWifiBytes.set(0L)
        _usage.value = Usage(monthKey(), 0, 0)
    }

    /** Yanıt gövdesinden okunan baytları sayan OkHttp ağ ara katmanı. */
    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.body ?: return@Interceptor response
        response.newBuilder().body(CountingBody(body) { record(it) }).build()
    }

    private class CountingBody(private val delegate: ResponseBody, private val onRead: (Long) -> Unit) : ResponseBody() {
        private val source: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val n = super.read(sink, byteCount)
                    if (n > 0) onRead(n)
                    return n
                }
            }.buffer()
        }
        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun source(): BufferedSource = source
        override fun close() = delegate.close()
    }

    companion object {
        fun format(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.2f GB".format(Locale("tr"), bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.0f MB".format(Locale("tr"), bytes / (1L shl 20).toDouble())
            else -> "%.0f KB".format(Locale("tr"), bytes / 1024.0)
        }
    }
}
