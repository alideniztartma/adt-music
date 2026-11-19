package com.alideniz.adtmusic.playback

import com.alideniz.adtmusic.platform.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Görselleştirici için frekans dağılımı. Masaüstünde VLC sesi doğrudan ses kartına
 * gönderdiği için anlık spektrum alınamıyor; görselleştirici kapalı kalır.
 */
class Spectrum(@Suppress("unused") private val context: Context) {

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Masaüstünde desteklenmiyor. */
    fun hasPermission(): Boolean = false

    fun acquire() {}
    fun release() {}

    companion object {
        const val BANDS = 16
    }
}
