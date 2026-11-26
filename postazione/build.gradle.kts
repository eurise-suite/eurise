plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Postazione: la nuova schermata iniziale (tela «Eurise · nuova interfaccia», 26 novembre 2026). APK a parte per
 * provarla accanto alla Scrivania di oggi, che resta dov'è. **Senza permesso di rete**: impegni e cose da fare arrivano
 * dall'app Eurise con il permesso di firma della suite ([eu.eurise.core.suite.PostazioneContract]).
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    androidResources { localeFilters += listOf("it", "en") }
    namespace = "eu.eurise.postazione"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.eurise.postazione"
        minSdk = 30
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
        manifestPlaceholders["hubPackage"] = "eu.eurise.hub"
        buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub\"")
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
            buildConfigField("String", "HUB_PACKAGE", "\"eu.eurise.hub.debug\"")
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
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
