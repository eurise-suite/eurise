import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Voce: dettatura sul telefono con Whisper (whisper.cpp, licenza MIT, copia ridotta in third_party/).
 * APK separato (documento di progetto, "Eurise Voce"): solo qui stanno il microfono, i modelli e Internet,
 * che serve soltanto a scaricare il modello scelto. Eurise Tastiera resta senza Internet.
 * Peso: il motore nativo per arm64 è di qualche MB; il modello (da 57 MB in su) si scarica a richiesta.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

/**
 * La voce di Alfred: sherpa-onnx (Apache 2.0, motore ONNX per le voci Piper). La libreria pubblicata dal progetto si
 * scarica una volta in voice/libs (fuori dal repository) e si controlla con la sua impronta. Peso: circa 12 MB in più
 * nell'APK di Voce (solo arm64); le voci (19–60 MB) si scaricano a richiesta. Motivo: la sintesi del telefono è spesso
 * metallica e legge la punteggiatura (prova del proprietario, 13 novembre).
 */
val sherpaVersion = "1.13.7"
val sherpaSha256 = "c4ef49e309f24fcee5c106b8a279481aaecaabb078cd37b2cd6e9a62cc8a73c8"
val sherpaAar: File = file("libs/sherpa-onnx-$sherpaVersion.aar").also { aar ->
    fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    if (!aar.isFile || sha(aar) != sherpaSha256) {
        aar.parentFile.mkdirs()
        val part = File(aar.path + ".part")
        URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
            .toURL().openStream().use { input -> part.outputStream().use { out -> input.copyTo(out) } }
        check(sha(part) == sherpaSha256) { "sherpa-onnx: impronta diversa da quella attesa" }
        part.renameTo(aar)
    }
}

android {
    namespace = "eu.eurise.voice"
    compileSdk = 35
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "eu.eurise.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                // Whisper è lentissimo senza ottimizzazioni: anche le prove si compilano ottimizzate.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
                cFlags += "-O3"
                cppFlags += "-O3"
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/debug-key/eurise-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("EURISE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EURISE_KEY_ALIAS")
                keyPassword = System.getenv("EURISE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE_DEBUG"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:suite"))
    implementation(files(sherpaAar))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
