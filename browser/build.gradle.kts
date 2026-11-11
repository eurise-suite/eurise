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

android {
    namespace = "eu.eurise.browser"
    compileSdk = 35

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
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    sourceSets {
        getByName("main") { assets.srcDir(layout.buildDirectory.dir("generated/eurise-assets")) }
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
    packaging { jniLibs { useLegacyPackaging = false } }
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
    // Motore Gecko: versione stabile più recente per arm64 (il registro di Gradle la scrive; si fissa dopo la prova).
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:+")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
