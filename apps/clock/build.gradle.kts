plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise Orologio: APK separato (vedi CLAUDE.md, "Struttura degli APK"). Parla con l'APK principale solo con il
 * permesso di firma della suite. Niente database: poche sveglie e timer stanno nelle preferenze.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.clock"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.clock"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("it", "en")
        manifestPlaceholders["suitePermission"] = "eu.eurise.permission.SUITE"
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
    buildFeatures { compose = true }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "/META-INF/*.version")
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:suite"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
