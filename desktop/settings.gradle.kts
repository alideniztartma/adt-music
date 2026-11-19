// ADT Music — masaüstü (Windows + Linux) sürümü.
// Android projesinden tamamen bağımsız ayrı bir Gradle derlemesidir; app/ klasörüne dokunmaz.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "ADTMusicDesktop"
