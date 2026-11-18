plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "eu.eurise.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    // Lo schema del database resta nel repository: serve per scrivere le migrazioni future.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    api(libs.kotlinx.coroutines.android)
    implementation(project(":core:suite"))
    // Il lettore dei PDF (spostato in un modulo piccolo il 17 novembre: lo usa anche la Chat).
    api(project(":core:pdf"))

    testImplementation(libs.junit)
    // org.json vero per i test (in Android è solo uno stub).
    testImplementation(libs.org.json)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
