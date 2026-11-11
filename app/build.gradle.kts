plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firma delle versioni ufficiali: i dati della chiave arrivano solo da variabili d'ambiente
 * (in GitHub Actions dai "secrets" del repository). La chiave non entra mai nel codice.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.hub"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.hub"
        minSdk = 26
        targetSdk = 35
        // Su GitHub Actions il numero cresce a ogni compilazione: ogni versione aggiorna la precedente.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        // Solo le lingue della suite: le librerie portano traduzioni in decine di lingue che nessuno vedrebbe.
        resourceConfigurations += listOf("it", "en")
        // Permesso di firma della suite (vedi core/suite ClockContract): uno per le ufficiali, uno per le prove.
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
    }

    signingConfigs {
        // Chiave di debug fissa (nel repository, senza valore): le versioni di prova si aggiornano senza
        // disinstallare. Non è la chiave delle versioni ufficiali.
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
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            // File di servizio delle librerie, inutili sul telefono.
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
    dependenciesInfo {
        // Niente blocco di metadati firmato da Google: richiesto da F-Droid.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:cloud"))
    implementation(project(":core:links"))
    implementation(project(":feature:cloud"))
    implementation(project(":feature:today"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:clock"))
    implementation(project(":feature:drive"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:calculator"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:people"))
    implementation(project(":feature:ponte"))
    implementation(project(":core:suite"))
    implementation(project(":core:files"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
