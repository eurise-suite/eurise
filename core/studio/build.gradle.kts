plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Lo Studio medico (tavole StuSpazio e StuPaziente, approvate il 17 novembre): pazienti, consensi, terapia abituale,
 * smistamento delle richieste e accordo tra Chat, Email e Messaggi. Logica pura con i suoi test; nessuna dipendenza.
 */
android {
    namespace = "eu.eurise.core.studio"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // SuiteAccess: i messaggi dei pazienti vanno solo alla Chat della suite (porte d'ingresso, docs/SICUREZZA.md).
    implementation(project(":core:suite"))
    testImplementation(libs.junit)
    // org.json vero per i test (in Android è dentro il sistema, nei test sulla JVM no).
    testImplementation("org.json:json:20240303")
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
