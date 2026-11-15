package com.alideniz.adtmusic.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Uygulama simgesini vurgu rengine göre değiştirir (manifestteki activity-alias'lar).
 * Simge, uygulama arka plana geçince değiştirilir; böylece açık ekran kapanmaz.
 */
object AppIcon {
    private val PRESETS = listOf(
        "Orange" to 0xFFFF5A1F.toInt(),
        "Red" to 0xFFE5383B.toInt(),
        "Pink" to 0xFFFF4D8D.toInt(),
        "Purple" to 0xFF8A5CFF.toInt(),
        "Blue" to 0xFF2F6BFF.toInt(),
        "Teal" to 0xFF00A6A6.toInt(),
        "Green" to 0xFF18B36B.toInt(),
        "Yellow" to 0xFFFFB400.toInt(),
    )

    private fun nearest(argb: Int): String = PRESETS.minBy { (_, p) ->
        val dr = ((argb shr 16) and 0xFF) - ((p shr 16) and 0xFF)
        val dg = ((argb shr 8) and 0xFF) - ((p shr 8) and 0xFF)
        val db = (argb and 0xFF) - (p and 0xFF)
        dr * dr * 3 + dg * dg * 4 + db * db * 2
    }.first

    private fun component(context: Context, name: String) =
        ComponentName(context.packageName, "com.alideniz.adtmusic.Launcher$name")

    /** Şu an etkin olan simge adı. */
    fun current(context: Context): String {
        val pm = context.packageManager
        return PRESETS.firstOrNull { (name, _) ->
            when (pm.getComponentEnabledSetting(component(context, name))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> name == "Orange"
                else -> false
            }
        }?.first ?: "Orange"
    }

    /** Gerekirse simgeyi değiştirir; değiştiyse true döner. */
    fun apply(context: Context, accent: Int, follow: Boolean): Boolean {
        val want = if (follow) nearest(accent) else "Orange"
        val now = current(context)
        if (want == now) return false
        val pm = context.packageManager
        return runCatching {
            // Önce yenisini aç, sonra eskisini kapat: başlatıcıda simgesiz an olmasın.
            pm.setComponentEnabledSetting(
                component(context, want),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            PRESETS.forEach { (name, _) ->
                if (name != want) {
                    pm.setComponentEnabledSetting(
                        component(context, name),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            true
        }.getOrDefault(false)
    }
}
