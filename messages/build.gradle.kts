plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Messaggi: APK separato (struttura dell'8 novembre 2026), app per gli SMS e gli MMS di sistema (ruolo «SMS»).
 * Conversazioni divise in Persone, Codici e consegne, Avvisi; in ogni messaggio le proposte capite sul telefono
 * (MessageInsights): appuntamento confermato → Calendario, numero → chiama, SMS, WhatsApp, Telegram, rubrica; altra lingua →
 * Traduci con Eurise Leggi; cosa da fare → Attività; appunto → Note. Interfaccia nostra in Compose; la parte di sistema
 * segue Fossify Messages (GPL-3.0, base approvata l'11 novembre). Per gli MMS il codice AOSP in third_party/aosp-mms
 * (Apache 2.0), compilato dai sorgenti. WorkManager (già nella suite) per i codici che si cancellano e l'invio programmato.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.messages"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.messages"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
        buildConfigField("String", "READ_PACKAGE", "\"eu.eurise.read\"")
        buildConfigField("String", "SUITE_PERMISSION", "\"eu.eurise.permission.SUITE\"")
    }

    sourceSets {
        getByName("main") { java.srcDir(rootProject.file("third_party/aosp-mms/java")) }
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
            buildConfigField("String", "READ_PACKAGE", "\"eu.eurise.read.debug\"")
            buildConfigField("String", "SUITE_PERMISSION", "\"eu.eurise.permission.SUITE_DEBUG\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Il codice AOSP usa API nascoste per le versioni vecchie: non è codice nostro.
        abortOnError = false
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:contacts"))
    implementation(project(":core:suite"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
