plugins {
    alias(libs.plugins.android.application)
}

/**
 * Eurise Office Motore (strada A del motore a richiesta, scelta dal proprietario il 13 novembre 2026): APK compagno
 * con il motore dei documenti, Collabora Online + LibreOffice, cioè la libreria Android (AAR) prodotta da
 * office-app.yml. Non ha icona nel cassetto: Office lo apre con un intent esplicito verso LOActivity, protetta dal
 * permesso della suite, e controlla che sia firmato con la sua stessa chiave. Il motore gira nel suo processo e legge
 * i suoi asset dal proprio APK, come in Collabora: nessuna modifica al core per stare fuori da Office.
 *
 * Esiste solo dentro office-app.yml (settings.gradle.kts lo include con EURISE_OFFICE_AAR).
 */
val engineAar: String = System.getenv("EURISE_OFFICE_AAR")!!
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.office.motore"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.office.motore"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.2.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        // Il motore esiste solo per arm64.
        ndk { abiFilters += listOf("arm64-v8a") }
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
            // Le classi della libreria si chiamano anche dal codice nativo (JNI): niente riduzione.
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Librerie native compresse nell'APK (si estraggono all'installazione): il download scende da circa 200 MB a
        // circa 70 (tavola OffMotore). Il motore legge dall'APK solo gli asset, non le librerie.
        jniLibs.useLegacyPackaging = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(files(engineAar))
    // Le dipendenze della libreria android/lib di Collabora (l'AAR in file non le porta con sé).
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
}
