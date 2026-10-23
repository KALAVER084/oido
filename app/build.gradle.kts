plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Cambia este número para actualizar NewPipe Extractor cuando YouTube cambie algo:
// https://github.com/TeamNewPipe/NewPipeExtractor/releases
val versionExtractor = "v0.26.5"

android {
    namespace = "hn.oido"
    compileSdk = 35

    defaultConfig {
        applicationId = "hn.oido.app"
        minSdk = 26
        targetSdk = 35
        // Número de versión según la fecha: siempre sube, venga de la tarea que venga
        versionCode = (System.currentTimeMillis() / 60_000).toInt()
        versionName = System.getenv("OIDO_VERSION") ?: "local"
    }

    // Firma fija: así cada APK nuevo se instala encima del anterior sin perder tus listas.
    // La llave NO está en el código: GitHub la saca de sus Secrets al compilar.
    signingConfigs {
        create("oido") {
            storeFile = file("oido.keystore")
            storePassword = System.getenv("OIDO_KEY_PASSWORD")
            keyAlias = "oido"
            keyPassword = System.getenv("OIDO_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("oido")
        }
        debug {
            signingConfig = signingConfigs.getByName("oido")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // requerido por NewPipe Extractor
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/9/module-info.class",
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:$versionExtractor")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1") // en vivos
    implementation("com.google.guava:guava:33.3.1-android")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")

    // Para el generador de "poToken" (copiado de NewPipe) que YouTube exige a veces
    implementation("io.reactivex.rxjava3:rxjava:3.1.9")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
}
