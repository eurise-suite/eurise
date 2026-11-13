plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Office: APK a parte (struttura degli APK, 8 novembre 2026). Qui stanno le parti native, in Kotlin e Compose come
 * il resto della suite: la schermata iniziale (tavola OffHome) e l'editor PDF (OffPdf, OffPdfPagine, OffPdfCondividi),
 * che funzionano anche senza il motore dei documenti.
 *
 * Il motore (Collabora Online + LibreOffice, office/app ed office/engine) è una libreria Android (AAR) prodotta dal lavoro
 * office-app.yml: con EURISE_OFFICE_AAR (percorso dell'AAR) entra nell'APK insieme a LOActivity e alle sue dipendenze.
 * Senza, l'APK resta piccolo e i documenti di testo, fogli e presentazioni chiedono il motore (tavola OffMotore).
 */
val engineAar: String? = System.getenv("EURISE_OFFICE_AAR")?.takeIf { it.isNotBlank() && file(it).exists() }
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
        buildConfigField("boolean", "ENGINE_IN_APK", (engineAar != null).toString())
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
    packaging {
        // L'AAR del motore porta le sue librerie native già pronte e non compresse (si caricano dall'APK).
        jniLibs.useLegacyPackaging = false
    }
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
    // LOActivity del motore è un'AppCompatActivity: il suo tema (Theme.EuriseOffice.Document) vive qui anche senza motore.
    implementation("androidx.appcompat:appcompat:1.7.0")

    if (engineAar != null) {
        implementation(files(engineAar))
        // Le dipendenze della libreria android/lib di Collabora (l'AAR in file non le porta con sé).
        implementation("com.google.android.material:material:1.12.0")
        implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    }

    testImplementation(libs.junit)
}
