plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.eurise.core.contacts"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

// Rubrica di sistema per Eurise Telefono ed Eurise Messaggi: elenco, ricerca per numero, salvataggio, modi per
// raggiungere una persona (chiamata, SMS, WhatsApp, Telegram, email) e l'avatar con le iniziali. Tutto sul telefono.
dependencies {
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    api(libs.kotlinx.coroutines.android)
}
