import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
// MSI güncellemesi için sürüm her derlemede artmalı: 1.5.<derleme no>
val appVersion = "1.5.$runNumber"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Arayüz (Android'deki gibi Material yok; her şey ADT tasarım diliyle çiziliyor)
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui)
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Ağ, görsel, YouTube Music (Android ile aynı çekirdek)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.json:json:20240303")
    implementation("com.google.zxing:core:3.5.3")

    // Ses: VLC (libVLC) üzerinden
    implementation("uk.co.caprica:vlcj:4.8.3")

    // Yerel veritabanı (Room'un masaüstü sürümü)
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.sqlite:sqlite-bundled:2.5.1")
    ksp("androidx.room:room-compiler:$room")
}

ksp {
    arg("room.generateKotlin", "true")
}

compose.desktop {
    application {
        mainClass = "com.alideniz.adtmusic.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx768m")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "ADT Music"
            packageVersion = appVersion
            description = "Reklamsız YouTube Music istemcisi"
            vendor = "Ali Deniz Tartma"
            copyright = "GPL-3.0"
            licenseFile.set(project.file("../LICENSE"))
            modules("java.sql", "java.naming", "jdk.unsupported", "java.net.http", "jdk.crypto.ec", "java.management")
            // Windows paketine CI'da indirilen VLC eklenir (bkz. .github/workflows/desktop.yml)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("app-resources"))

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "ADT Music"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f0d6b1e-3c55-4a8f-9a3c-adf00d5a1c01"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "adt-music"
                shortcut = true
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                debMaintainer = "alideniztartma@users.noreply.github.com"
            }
        }
    }
}

// Sürüm bilgisi (Android'deki BuildConfig karşılığı): uygulama içi güncelleme denetimi kullanır.
val genBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    val build = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
    inputs.property("build", build)
    inputs.property("version", appVersion)
    outputs.dir(out)
    doLast {
        val f = out.get().file("build-info.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$appVersion\ncode=10\nbuild=$build\n")
    }
}
sourceSets["main"].resources.srcDir(genBuildInfo)
