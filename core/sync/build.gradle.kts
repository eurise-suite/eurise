plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Nucleo di sincronizzazione per l'app Eurise del computer (docs/PIATTAFORME.md): CalDAV per Calendario e Attività,
 * WebDAV per le Note con l'unione a tre vie, IMAP e SMTP per l'Email. Kotlin puro per la JVM, senza API di Android:
 * gli stessi formati e le stesse regole del telefono (cartella `Eurise/Note` in HTML, iCalendar, scritture sempre
 * condizionate all'etag letto). OkHttp è già nella suite; JavaMail è la stessa API che il telefono usa con android-mail.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.okhttp)
    api("com.sun.mail:javax.mail:1.6.2")
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

base { archivesName.set("eurise-sync") }
