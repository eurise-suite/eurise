plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/** Accordi tra gli APK della suite (permesso di firma, azioni): nessuna dipendenza, pochi byte. */
android {
    namespace = "eu.eurise.core.suite"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
