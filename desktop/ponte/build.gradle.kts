plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Eurise per il computer, primo pezzo: il Ponte (docs/PIATTAFORME.md). Programma per Linux (Ubuntu e Fedora di
 * riferimento) che si collega al telefono con il codice QR e riceve appunti, dettatura, «Continua qui» e «Ti hanno
 * cercato». Per ora da riga di comando; l'interfaccia Compose e il Flatpak arrivano con l'app Eurise del computer.
 * Usa core/ponte (lo stesso codice del telefono) e ZXing (già nella suite) per disegnare il QR nel terminale.
 */
application {
    mainClass.set("eu.eurise.desktop.MainKt")
    applicationName = "eurise-ponte"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:ponte"))
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}

base { archivesName.set("eurise-ponte-app") }
