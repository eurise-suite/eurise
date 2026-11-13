plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Office: APK a parte (struttura degli APK, 8 novembre 2026). Qui stanno le parti native, in Kotlin e Compose come
 * il resto della suite: la schermata iniziale (tavola OffHome), la vista rapida (OffLettura…) e l'editor PDF (OffPdf,
 * OffPdfPagine, OffPdfCondividi), che funzionano senza il motore dei documenti.
 *
 * Il motore (Collabora Online + LibreOffice) è l'APK compagno «Eurise Office Motore» (office/motore, strada A decisa il
 * 13 novembre 2026): Office lo scarica la prima volta che serve (tavola OffMotore), controlla che sia firmato con la
 * stessa chiave e gli passa i documenti con un intent esplicito.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.office"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.office"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.2.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
        buildConfigField("String", "ENGINE_PACKAGE", "\"eu.eurise.office.motore\"")
        // Archivio delle versioni (GitHub, gratuito: regola di spesa): l'ultima versione ufficiale del motore.
        buildConfigField("String", "ENGINE_URL", "\"https://github.com/eurise-suite/eurise/releases/latest/download/eurise-office-motore.apk\"")
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
            buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub.debug\"")
            buildConfigField("String", "ENGINE_PACKAGE", "\"eu.eurise.office.motore.debug\"")
            // Il motore di prova del ramo office (office-app.yml lo pubblica a ogni giro).
            buildConfigField("String", "ENGINE_URL", "\"https://github.com/eurise-suite/eurise/releases/download/apk-prova-office/eurise-office-motore-debug.apk\"")
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Editor PDF (motivo nel commit): annotazioni vere, pagine, moduli, cifratura e oscuramento scritti nel file.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
