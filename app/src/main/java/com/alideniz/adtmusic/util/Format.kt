package com.alideniz.adtmusic.util

import android.content.Context
import android.content.Intent
import com.alideniz.adtmusic.data.Song
import java.util.Calendar

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).coerceAtLeast(0)).ifEmpty { "0:00" }

fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f Mr".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1f Mn".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f B".format(n / 1_000.0)
    else -> n.toString()
}.replace(".0 ", " ")

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun formatListenTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} sa ${minutes % 60} dk"
        else -> "$minutes dk"
    }
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

fun shareSong(context: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}\n${song.watchUrl}")
    }
    context.startActivity(Intent.createChooser(send, "Şarkıyı paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
