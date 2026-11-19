package com.alideniz.adtmusic.platform

import java.io.File
import java.util.Properties
import java.util.concurrent.Executors

/**
 * SharedPreferences'ın masaüstü karşılığı: ayarları tek bir .properties dosyasında tutar.
 * Yazma işlemleri arka planda, sırayla yapılır.
 */
class PrefStore(private val file: File) {
    private val props = Properties()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "adt-prefs").apply { isDaemon = true } }

    init {
        runCatching { if (file.exists()) file.inputStream().use { props.load(it) } }
    }

    @Synchronized fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def
    fun getBoolean(key: String, def: Boolean): Boolean = getString(key, null)?.toBooleanStrictOrNull() ?: def
    fun getInt(key: String, def: Int): Int = getString(key, null)?.toIntOrNull() ?: def
    fun getLong(key: String, def: Long): Long = getString(key, null)?.toLongOrNull() ?: def
    fun getFloat(key: String, def: Float): Float = getString(key, null)?.toFloatOrNull() ?: def

    @Synchronized fun put(key: String, value: Any) {
        props.setProperty(key, value.toString())
        val snapshot = Properties().also { it.putAll(props) }
        writer.execute {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.outputStream().use { snapshot.store(it, "ADT Music ayarları") }
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }
}
