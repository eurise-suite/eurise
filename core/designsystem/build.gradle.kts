plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.eurise.core.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    // Vetro satinato: sfocatura di ciò che scorre dietro barre e testate (Apache 2.0).
    api(libs.haze)
    implementation(libs.androidx.core.ktx)
    // La schermata dell'errore (CrashActivity): già in tutte le app della suite.
    implementation(libs.androidx.activity.compose)
    // I servizi esterni e il loro interruttore (enum e preferenze, nessuna dipendenza): per l'avviso comune.
    api(project(":core:suite"))

    testImplementation(libs.junit)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
