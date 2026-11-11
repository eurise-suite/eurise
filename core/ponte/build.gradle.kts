plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Ponte Eurise tra telefono e computer (docs/PIATTAFORME.md): abbinamento con il codice QR, chiavi, messaggi cifrati,
 * cornici per la rete locale e nomi dei file per il passaggio dal cloud dell'utente. Kotlin puro per la JVM, senza
 * API di Android né librerie: lo usano l'app Eurise sul telefono e l'app Eurise del computer (Ubuntu, Fedora).
 * La crittografia è quella standard della JVM (ECDH P-256, HKDF-SHA256, AES-256-GCM), presente anche su Android 8.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation(libs.junit)
}

// Nome del jar diverso da quello di desktop/ponte, che lo mette nella stessa cartella lib/.
base { archivesName.set("eurise-ponte-core") }
