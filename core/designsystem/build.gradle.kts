plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.eurise.core.designsystem"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    // Vetro satinato: sfocatura di ciò che scorre dietro barre e testate (Apache 2.0).
    api(libs.haze)
    implementation(libs.androidx.core.ktx)
    // I servizi esterni e il loro interruttore (enum e preferenze, nessuna dipendenza): per l'avviso comune.
    api(project(":core:suite"))

    testImplementation(libs.junit)
}
