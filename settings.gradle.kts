pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Copia de Maven Central mantenida por Google: más estable y sin el límite de descargas
        // ("429 Too Many Requests") que a veces pone Maven Central a las máquinas de GitHub
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        maven("https://jitpack.io") // NewPipe Extractor
    }
}
rootProject.name = "Oido"
include(":app")
