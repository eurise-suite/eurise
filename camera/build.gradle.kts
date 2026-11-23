plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Fotocamera: APK separato **senza permesso di rete** (decisione del 22 novembre 2026, docs/stato/FOTOCAMERA.md).
 * Fotocamera, microfono e posizione restano solo qui; con le altre app della suite parla solo con il permesso di firma.
 * Compila APK controlla a ogni giro che il permesso INTERNET non entri nel manifesto finale.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    androidResources { localeFilters += listOf("it", "en") }
    namespace = "eu.eurise.camera"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.eurise.camera"
        minSdk = 30
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        // Una variante per compilazione (lezione di Mappe, 21 novembre): un solo permesso e un solo nome per app.
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        manifestPlaceholders["hubPackage"] = "eu.eurise.hub"
        manifestPlaceholders["galleryPackage"] = "eu.eurise.gallery"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
        buildConfigField("String", "GALLERY_PACKAGE", "\"eu.eurise.gallery\"")
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
            manifestPlaceholders["hubPackage"] = "eu.eurise.hub.debug"
            manifestPlaceholders["galleryPackage"] = "eu.eurise.gallery.debug"
            buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub.debug\"")
            buildConfigField("String", "GALLERY_PACKAGE", "\"eu.eurise.gallery.debug\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:suite"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // CameraX (già nel catalogo per lo Scanner): mirino, scatto, video.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    // La Notte del telefono dove il produttore la offre (estensioni di CameraX, codice aperto).
    implementation(libs.androidx.camera.extensions)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
