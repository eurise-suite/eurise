plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Lavorare insieme (tavole Ins*, Fam*, Ami*, Lav*, Spa*, approvate il 17 novembre): la stessa scheda in Chat e in Email
 * (tavola InsStessaScheda). Un modello solo per Affida, Faccende, Uscite, Spese, Disponibilità e il resto; cambia solo la
 * strada: un messaggio strutturato nella stanza Matrix cifrata o un'email con i pulsanti e la risposta «fatto» per chi non
 * usa Eurise. Logica pura con i suoi test; dipende solo da core:suite (SuiteAccess, per il contratto tra Chat ed Email).
 */
android {
    namespace = "eu.eurise.core.insieme"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:suite"))
    testImplementation(libs.junit)
    // org.json vero per i test (in Android è dentro il sistema, nei test sulla JVM no).
    testImplementation(libs.org.json)
}

// Kotlin 2.3 (AGP 9): il bersaglio Java si indica qui, non più in android { kotlinOptions }.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
