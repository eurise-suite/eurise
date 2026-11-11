plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.eurise.feature.launcher"
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
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:links"))
    implementation(project(":core:suite"))
    implementation(project(":core:cloud"))
    implementation(project(":core:contacts"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Salute: lettura dei dati che orologi e band (per esempio tramite Gadgetbridge) scrivono in Health Connect.
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
