plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Telefono e computer (tavola Ponte): collegare un computer con il QR, «Invia al computer» dal menu Condividi,
 * «Detta al computer». Il protocollo è core/ponte, lo stesso del computer (desktop/ponte).
 */
android {
    namespace = "eu.eurise.feature.ponte"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ponte"))
    // Il contratto con Eurise Password (accessi chiesti dal computer): già nella suite.
    implementation(project(":core:suite"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Già nella suite (Drive, Cloud): il collegamento dal computer è un lavoro in primo piano con il vincolo del Wi-Fi.
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
