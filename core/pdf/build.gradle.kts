plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Il testo dei PDF, letto sul telefono senza librerie (biglietti, ricevute, promemoria delle ricette di Millewin).
 * Modulo piccolo e senza dipendenze, così lo usano sia l'APK principale (tramite core/data) sia la Chat dello Studio.
 */
android {
    namespace = "eu.eurise.core.pdf"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
