package com.alideniz.adtmusic.data

/** Android'deki BuildConfig'in karşılığı. Sürüm bilgisi pakete gömülen dosyadan okunur. */
object BuildInfo {
    private val props by lazy {
        java.util.Properties().apply {
            runCatching { BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use { load(it) } }
        }
    }
    val VERSION_NAME: String get() = props.getProperty("version", "1.5.0")
    val VERSION_CODE: Int get() = props.getProperty("code", "10").toIntOrNull() ?: 10
    val BUILD_NUMBER: Int get() = props.getProperty("build", "0").toIntOrNull() ?: 0
}
