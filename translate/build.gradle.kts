plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Traduci, PROTOTIPO: traduzione sul telefono con i modelli di Firefox (Bergamot, licenza MPL 2.0) e il motore
 * slimt (MIT) con ruy e SentencePiece, compilati dai sorgenti di `third_party/`. Serve a misurare peso, velocità e qualità
 * prima di decidere dove mettere il motore (la Tastiera non ha Internet e non può scaricare i modelli).
 * Si compila solo nel suo lavoro (EURISE_TRANSLATE=1). I modelli si scaricano a richiesta, mai dentro l'APK.
 */
android {
    namespace = "eu.eurise.translate"
    compileSdk = 35
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "eu.eurise.translate"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.0.1-prototipo"
        resourceConfigurations += listOf("it", "en")
        ndk { abiFilters += listOf("arm64-v8a") }
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        externalNativeBuild {
            cmake {
                // La traduzione è lenta senza ottimizzazioni: anche le prove si compilano ottimizzate.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
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
            manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE_DEBUG"
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
}
