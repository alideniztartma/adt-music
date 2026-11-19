package com.alideniz.adtmusic.data

import com.alideniz.adtmusic.platform.Context
import com.alideniz.adtmusic.platform.isWindows
import com.alideniz.adtmusic.platform.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class UpdateInfo(
    val build: Int,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Masaüstü güncelleme denetimi: GitHub'daki son masaüstü sürümüne ("desktop-<n>")
 * bakar; yeni sürüm varsa indirme sayfasını tarayıcıda açar.
 */
class Updater(private val context: Context, private val client: OkHttpClient, private val settings: Settings) {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    /** -1: indirme yok, 0..1: ilerleme */
    private val _progress = MutableStateFlow(-1f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    val currentBuild: Int get() = BuildInfo.BUILD_NUMBER

    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - settings.lastUpdateCheck.value < 6 * 3600_000L) return@withContext _available.value
        settings.lastUpdateCheck.set(now)
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ADT-Music-Desktop")
            .build()
        val body = runCatching {
            client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        val list = JSONArray(body)
        val suffix = if (isWindows) ".msi" else ".deb"
        var info: UpdateInfo? = null
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val tag = o.optString("tag_name")
            val build = tag.substringAfter("desktop-", "").toIntOrNull() ?: continue
            val assets = o.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name").endsWith(suffix)) {
                    if (build > currentBuild && currentBuild > 0) {
                        info = UpdateInfo(build, o.optString("name", tag), o.optString("body").trim(),
                            o.optString("html_url"), a.optLong("size"))
                    }
                    break
                }
            }
            break // yalnızca en yeni masaüstü sürümü
        }
        _available.value = info
        info
    }

    /** Masaüstünde kurulum dosyasını kullanıcı indirir: sürüm sayfasını açar. */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        openUrl(info.apkUrl.ifBlank { "https://github.com/$REPO/releases" })
    }

    fun canInstall(): Boolean = true

    fun openInstallPermission() {}

    companion object {
        const val REPO = "alideniztartma/adt-music"
    }
}
