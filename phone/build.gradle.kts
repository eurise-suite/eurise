plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Telefono: APK separato (struttura dell'8 novembre 2026), app per le chiamate di sistema (ruolo «Telefono»):
 * recenti con «da richiamare», preferiti, rubrica, tastierino e schermata della chiamata con gli impegni della persona
 * e la nota che finisce in Note. Interfaccia nostra in Compose; la parte di sistema (InCallService, registro, numeri
 * bloccati) segue le API Android come Fossify Phone (GPL-3.0), base approvata l'11 novembre. Nessuna libreria nuova.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.phone"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
        buildConfigField("String", "MESSAGES_PACKAGE", "\"eu.eurise.messages\"")
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
            buildConfigField("String", "MESSAGES_PACKAGE", "\"eu.eurise.messages.debug\"")
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    testImplementation(libs.junit)
    implementation(project(":core:contacts"))
    implementation(project(":core:suite"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
