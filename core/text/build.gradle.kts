plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Capire i testi, senza librerie e senza Android: date e ore scritte a parole («giovedì alle 15», «entro venerdì»,
 * `QuickAdd`) e quello che c'è in un messaggio (appuntamenti, cose da fare, numeri, codici, `MessageInsights`).
 * Modulo piccolo (come core/pdf) così lo usano l'APK principale (tramite core/data, stessi nomi dei pacchetti), Messaggi
 * e la Chat, che non porta con sé il database.
 */
android {
    namespace = "eu.eurise.core.text"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
