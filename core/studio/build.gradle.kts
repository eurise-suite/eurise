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
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation(libs.junit)
    // org.json vero per i test (in Android è dentro il sistema, nei test sulla JVM no).
    testImplementation("org.json:json:20240303")
}
