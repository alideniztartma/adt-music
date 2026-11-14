package com.alideniz.adtmusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.alideniz.adtmusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val build: Int,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Uygulama içi güncelleme: GitHub'daki son sürümü denetler, APK'yı indirir ve
 * Android'in kurulum ekranını açar. Sürümler GitHub Actions ile otomatik çıkar.
 */
class Updater(private val context: Context, private val client: OkHttpClient, private val settings: Settings) {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    /** -1: indirme yok, 0..1: ilerleme */
    private val _progress = MutableStateFlow(-1f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    val currentBuild: Int get() = BuildConfig.BUILD_NUMBER

    /** Yeni sürüm varsa bilgisini döner. [force] değilse 6 saatte bir denetler. */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - settings.lastUpdateCheck.value < 6 * 3600_000L) return@withContext _available.value
        settings.lastUpdateCheck.set(now)
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ADT-Music")
            .build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext null
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val build = tag.substringAfter("build-", "").toIntOrNull() ?: return@withContext null
        val assets = o.optJSONArray("assets")
        var url = ""
        var size = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url"); size = a.optLong("size"); break
                }
            }
        }
        val info = if (build > currentBuild && url.isNotBlank() && currentBuild > 0) {
            UpdateInfo(build, o.optString("name", tag), o.optString("body").trim(), url, size)
        } else null
        _available.value = info
        info
    }

    /** APK'yı indirir ve kurulum ekranını açar. */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "adt-music-${info.build}.apk")
            _progress.value = 0f
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", "ADT-Music").build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("İndirme başarısız (${res.code})")
                val total = res.body?.contentLength()?.takeIf { it > 0 } ?: info.sizeBytes
                res.body!!.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            sum += read
                            if (total > 0) _progress.value = (sum.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            out
        }
        _progress.value = -1f
        install(file)
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val REPO = "alideniztartma/adt-music"
    }
}
