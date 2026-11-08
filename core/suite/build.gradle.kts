plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/** Accordi tra gli APK della suite (permesso di firma, azioni): nessuna dipendenza, pochi byte. */
android {
    namespace = "eu.eurise.core.suite"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
