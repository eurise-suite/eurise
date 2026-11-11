plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Email: APK separato (pacchetto eu.eurise.mail, già previsto da «Ti hanno cercato»). Tavole approvate il
 * 12 novembre: MailPosta, MailLettura, MailScrivi, MailCaselle. Tutte le caselle in una, Persone/Avvisi/Newsletter
 * decise sul telefono, immagini che spiano bloccate, server trovati da soli. IMAP e SMTP con Jakarta Mail per Android
 * (android-mail 1.6.7, EPL 2.0/GPL 2.0 con eccezione: libera, su Maven Central, usata da molte app su F-Droid):
 * scrivere da zero un client IMAP con MIME e codifiche non vale il rischio. WorkManager (già nella suite) per il
 * controllo della posta ogni 15 minuti, solo con la rete.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.mail"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.mail"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
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
        abortOnError = false
    }
    packaging {
        resources {
            excludes += listOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt",
                "META-INF/mimetypes.default", "META-INF/mailcap.default")
        }
    }
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
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation(libs.junit)
}
