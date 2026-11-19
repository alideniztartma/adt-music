package com.alideniz.adtmusic.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.imageio.ImageIO

/**
 * Android'deki Context'in masaüstü karşılığı: yalnızca uygulamanın gerçekten
 * kullandığı klasörleri sunar. Veriler kullanıcının kendi klasöründe tutulur:
 *  - Windows: %APPDATA%\ADT Music
 *  - Linux:   ~/.local/share/adt-music  (önbellek: ~/.cache/adt-music)
 */
class Context private constructor() {
    val packageName = "com.alideniz.adtmusic"

    val filesDir: File by lazy { dataRoot().apply { mkdirs() } }
    val cacheDir: File by lazy { cacheRoot().apply { mkdirs() } }

    private fun dataRoot(): File {
        val home = System.getProperty("user.home")
        return if (isWindows) {
            File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "ADT Music")
        } else {
            File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "adt-music")
        }
    }

    private fun cacheRoot(): File {
        val home = System.getProperty("user.home")
        return if (isWindows) {
            File(System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local", "ADT Music\\cache")
        } else {
            File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache", "adt-music")
        }
    }

    companion object {
        val instance: Context by lazy { Context() }
    }
}

val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

val LocalContext = staticCompositionLocalOf { Context.instance }

/** android.net.Uri.encode/decode karşılığı (yol ve sorgu için güvenli). */
object Uri {
    fun encode(s: String?): String = URLEncoder.encode(s ?: "", Charsets.UTF_8).replace("+", "%20")
    fun decode(s: String?): String = URLDecoder.decode(s ?: "", Charsets.UTF_8)
}

/** Bağlantıyı varsayılan tarayıcıda açar. */
fun openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else if (isWindows) {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
        } else {
            ProcessBuilder("xdg-open", url).start()
        }
    }
}

/** Dosya ya da klasörü sistemin dosya yöneticisiyle açar. */
fun openFile(file: File) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else if (isWindows) {
            ProcessBuilder("explorer", file.absolutePath).start()
        } else {
            ProcessBuilder("xdg-open", file.absolutePath).start()
        }
    }
}

fun copyToClipboard(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

/** Bir görüntüyü dosyaya yazar (PNG ya da JPEG). */
fun ImageBitmap.writeTo(file: File, format: String = "png") {
    file.parentFile?.mkdirs()
    val awt = toAwtImage()
    val img = if (format == "jpg") {
        // JPEG saydamlık desteklemez: beyaz zemine çiz
        val rgb = java.awt.image.BufferedImage(awt.width, awt.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply { drawImage(awt, 0, 0, java.awt.Color.WHITE, null); dispose() }
        rgb
    } else awt
    ImageIO.write(img, format, file)
}

/** Paylaşılan görselleri kaydetmek için Resimler klasörü. */
fun picturesDir(): File {
    val home = File(System.getProperty("user.home"))
    val pics = listOf(File(home, "Pictures"), File(home, "Resimler")).firstOrNull { it.isDirectory } ?: home
    return File(pics, "ADT Music").apply { mkdirs() }
}

// ------------------------------------------------------------------ geri tuşu

/**
 * Android'deki BackHandler'ın karşılığı. Esc tuşu ve farenin "geri" düğmesi,
 * en son eklenen etkin işleyiciyi çalıştırır; yoksa sayfa geri gider.
 */
object BackDispatcher {
    internal class Entry(var enabled: Boolean, var onBack: () -> Unit)

    internal val entries = mutableStateListOf<Entry>()

    /** Bir işleyici çalıştıysa true döner. */
    fun dispatch(): Boolean {
        val top = entries.lastOrNull { it.enabled } ?: return false
        top.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current.value() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackDispatcher.entries.add(entry)
        onDispose { BackDispatcher.entries.remove(entry) }
    }
}

/** Ekranlar arası paylaşılan ViewModel deposu (Android'deki Activity düzeyinin karşılığı). */
object AppViewModelStore : androidx.lifecycle.ViewModelStoreOwner {
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
}

/** Dosya kaydetme penceresi (sistem iletişim kutusu). */
fun chooseSaveFile(suggestedName: String, title: String = "Kaydet"): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** Dosya açma penceresi (sistem iletişim kutusu). */
fun chooseOpenFile(title: String = "Aç"): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** HtmlCompat.fromHtml(...).toString() karşılığı: etiketleri atar, satır sonlarını ve varlıkları çözer. */
fun htmlToText(html: String): String {
    var s = html.replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("<[^>]+>"), "")
    s = s.replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
        val code = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toIntOrNull(16) else m.groupValues[2].toIntOrNull()
        code?.let { String(Character.toChars(it)) } ?: m.value
    }
    return s.replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
}
