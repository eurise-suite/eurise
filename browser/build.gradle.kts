plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Browser (tavole della pagina «Browser», approvate l'11 novembre): APK separato su GeckoView, il motore di
 * Firefox (MPL 2.0, da maven.mozilla.org). Scelta di Gecko su tutti i sistemi: docs/PIATTAFORME.md. L'estensione Eurise
 * (browser/extension, nucleo comune con Firefox sul computer) è integrata negli assets.
 * GeckoView pesa circa 50 MB per arm64: per questo l'APK è solo arm64 e si compila in un lavoro a parte (EURISE_BROWSER=1).
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

// Processore del motore: arm64 per i telefoni; x86_64 solo per provare l'avvio sull'emulatore (prova-avvio-browser.yml).
val geckoAbi = (findProperty("eurise.geckoAbi") as String?) ?: "arm64-v8a"

android {
    namespace = "eu.eurise.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.eurise.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
        buildConfigField("String", "SUITE_PERMISSION", "\"eu.eurise.permission.SUITE\"")
        // Pacchetti di prova (.debug) delle altre app: non BuildConfig.DEBUG, che segue «debuggable» (spento anche in prova).
        buildConfigField("boolean", "SUITE_DEBUG", "false")
        ndk { abiFilters += listOf(geckoAbi) }
    }

    sourceSets {
        // AGP 9 non accetta più un Provider qui: si passa la cartella già risolta (la copia resta legata da copyExtension).
        getByName("main") { assets.srcDir(layout.buildDirectory.dir("generated/eurise-assets").get().asFile) }
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
            buildConfigField("String", "SUITE_PERMISSION", "\"eu.eurise.permission.SUITE_DEBUG\"")
            buildConfigField("boolean", "SUITE_DEBUG", "true")
            // L'APK di prova è quello che il proprietario usa ogni giorno: compilato come la versione ufficiale (R8, non
            // «debuggable», profili di avvio di Compose applicati) va molto più svelto. Stesso nome, stessa chiave di
            // prova e stesso id: si aggiorna sopra quello installato. I nomi restano leggibili nei rapporti degli errori.
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Librerie compresse nell'APK: GeckoView (libxul 138 MB) scende a circa un terzo da scaricare; Android le estrae all'installazione.
    packaging { jniLibs { useLegacyPackaging = true } }
    // L'APK di prova non è più «debuggable», quindi Gradle vi farebbe girare il lint «vital», che si rompe su un rilevatore
    // di lifecycle (KaCallableMemberCall): il lint resta quello del lavoro apposito.
    lint { checkReleaseBuilds = false }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// L'estensione comune va negli assets come assets/eurise/ (GeckoView la carica da resource://android/assets/eurise/).
val copyExtension by tasks.registering(Copy::class) {
    from(rootProject.file("browser/extension"))
    into(layout.buildDirectory.dir("generated/eurise-assets/eurise"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyExtension) }
tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach { dependsOn(copyExtension) }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:suite"))
    // Le date nelle pagine (Lettura › Promemoria) con lo stesso lettore dei messaggi, e l'evento per il Calendario.
    implementation(project(":core:data"))
    implementation(project(":core:contacts"))
    // Motore Gecko (18 novembre 2026, ramo strumenti): sempre l'ultima GeckoView uscita. Firefox esce con correzioni di
    // sicurezza circa ogni quattro settimane e il Browser le prende al giro successivo. Dalla 142 serve compileSdk 37
    // e AGP 9, ora presenti in tutta la suite.
    implementation("org.mozilla.geckoview:geckoview-$geckoAbi:[160.0,300.0)")
    // Il QR dell'indirizzo (tavola BrwIndirizzo): ZXing è già nella suite (Scanner, Calendario, Chat).
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    // org.json vero per i test (in Android è solo uno stub).
    testImplementation(libs.org.json)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
