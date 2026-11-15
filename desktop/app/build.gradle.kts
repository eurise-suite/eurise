import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

/**
 * Eurise per il computer (docs/PIATTAFORME.md): un'app sola in Compose Desktop che tiene il Ponte con il telefono, fa da
 * host del native messaging per l'estensione di Firefox e accoglie Oggi, Calendario, Note, Attività, Email e Alfred
 * (tavole della pagina «Eurise sul computer», da approvare). Per ora lo scheletro: finestra, sezioni, Ponte.
 * Compose Multiplatform (JetBrains, Apache 2.0) è la stessa Compose del telefono, per la JVM: serve per l'interfaccia.
 */
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":desktop:ponte"))
    implementation(project(":core:ponte"))
    implementation(project(":core:sync"))
    implementation(libs.zxing.core)
}

compose.desktop {
    application {
        mainClass = "eu.eurise.desktop.app.MainKt"
        jvmArgs += listOf("-Dawt.useSystemAAFontSettings=on", "-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "eurise"
            packageVersion = "0.1.0"
            description = "Eurise per il computer"
            vendor = "Eurise"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.naming", "jdk.crypto.ec", "java.net.http")
            linux {
                iconFile.set(project.file("src/main/resources/eurise.png"))
                packageName = "eurise"
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
