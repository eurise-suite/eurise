plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Chat: APK separato (pacchetto eu.eurise.chat), tavole approvate il 12 novembre (ChatElenco, ChatConversazione,
 * ChatAccesso). Matrix, protocollo aperto e federato: account sul server scelto (matrix.org, il tuo, poi quello di
 * Eurise), accesso dal sito del server. API client-server con HttpURLConnection e org.json: nessuna libreria nuova.
 * Cifratura end-to-end con il modulo crittografico ufficiale di Matrix (org.matrix.rustcomponents:crypto-android,
 * matrix-sdk-crypto in Rust con vodozemac, Apache 2.0, lo stesso di Element): senza rete propria, si innesta sul nostro
 * client HTTP. Porta con sé una libreria nativa (circa 10 MB per arm64) e JNA: per questo l'APK è solo arm64.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        ndk { abiFilters += listOf("arm64-v8a") }
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
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
    lint { abortOnError = false }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // Chiave di recupero nel backup cifrato della suite (SecretsContract), solo tra app con la stessa firma.
    implementation(project(":core:suite"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Cifratura end-to-end (versione usata da Element per Android) e JNA, che collega Kotlin alla libreria in Rust.
    implementation("org.matrix.rustcomponents:crypto-android:26.05.12")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    // Il QR sulla carta della chiave di recupero (ZXing è già nella suite: Scanner, Calendario).
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    // org.json vero per i test (in Android è dentro il sistema, nei test sulla JVM no).
    testImplementation("org.json:json:20240303")
}
