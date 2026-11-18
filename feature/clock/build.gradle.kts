plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Orologio: dentro l'APK principale (deciso l'8 novembre 2026), con la sua icona nel cassetto. Niente database:
 * poche sveglie e timer stanno nelle preferenze.
 */
android {
    namespace = "eu.eurise.feature.clock"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:suite"))
    // Catalogo, disposizioni e parole di Alfred per la sua sveglia (AlfSveglia); il meteo già scaricato.
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
