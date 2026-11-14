@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.alideniz.adtmusic.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * YouTube (googlevideo.com) tek parça uzun isteklerde hızı düşürür.
 * Bu kaynak isteği [chunkSize] baytlık parçalara böler; her parça tam hızla gelir.
 * Böylece şarkılar hem daha hızlı başlar hem de çok daha hızlı iner.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK,
) : DataSource {

    private var spec: DataSpec? = null
    private var passthrough = false
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val host = dataSpec.uri.host.orEmpty()
        if (!host.endsWith("googlevideo.com")) {
            passthrough = true
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        spec = dataSpec
        position = dataSpec.position
        bytesRemaining = dataSpec.length

        openChunk()
        val total = totalFromHeaders()
        if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            if (total <= 0) {
                // Toplam boyut bilinmiyorsa bölmeden devam et.
                closeUpstream()
                passthrough = true
                upstreamOpen = true
                return upstream.open(dataSpec)
            }
            bytesRemaining = total - dataSpec.position
        }
        return bytesRemaining
    }

    private fun openChunk() {
        val s = spec ?: return
        val want = if (bytesRemaining == C.LENGTH_UNSET.toLong()) chunkSize else minOf(chunkSize, bytesRemaining)
        val sub = s.buildUpon().setPosition(position).setLength(want).build()
        upstream.open(sub)
        upstreamOpen = true
    }

    private fun totalFromHeaders(): Long {
        val header = upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull() ?: return -1
        return header.substringAfterLast('/').trim().toLongOrNull() ?: -1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val max = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        var n = upstream.read(buffer, offset, max)
        if (n == C.RESULT_END_OF_INPUT) {
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) return C.RESULT_END_OF_INPUT
            closeUpstream()
            openChunk()
            n = upstream.read(buffer, offset, max)
            if (n == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
        }
        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        return n
    }

    private fun closeUpstream() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        closeUpstream()
    }

    companion object {
        const val DEFAULT_CHUNK = 1L * 1024 * 1024
    }
}
