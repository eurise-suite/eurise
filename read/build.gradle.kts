plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Leggi, PROTOTIPO: lettura del testo (OCR) delle scansioni sul telefono con Tesseract (Apache 2.0) tramite
 * Tesseract4Android (Apache 2.0). Serve a misurare peso e qualità in italiano prima di decidere il motore.
 * Si compila solo nel suo lavoro (EURISE_READ=1), così non tocca le altre compilazioni. La lingua italiana
 * (tessdata_fast, pochi MB) si scarica a richiesta, mai dentro l'APK.
 */
android {
    namespace = "eu.eurise.read"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.read"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.0.1-prototipo"
        resourceConfigurations += listOf("it", "en")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/debug-key/eurise-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Motivo: è il prototipo che deve misurarla. Libreria intera giustificata solo se il prototipo convince.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
}
