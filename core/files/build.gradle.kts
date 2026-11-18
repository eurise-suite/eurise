plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Eurise File comune (tavole FileSalvaIn e FileApriDa): «Salva in…» e «Apri da…» su telefono e cloud dell'utente, con le
 * proposte imparate da dove si salvano le cose. Lo usano Scanner, File e, dall'ingresso con il permesso della suite,
 * Office, Galleria e Browser. I moduli feature non dipendono tra loro: il riquadro comune sta qui.
 */
android {
    namespace = "eu.eurise.core.files"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:cloud"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
