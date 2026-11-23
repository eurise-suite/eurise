plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/*
 * Versioni minime di librerie che arrivano nelle app di riflesso (OSV-Scanner, 19 novembre 2026; docs/SICUREZZA.md):
 * BouncyCastle 1.72 (tramite pdfbox-android) e Guava 31.1/33.3.1 avevano vulnerabilità note. Si alza solo ciò che è più
 * vecchio; gli strumenti di compilazione (versioni -jre, jdk18on di AGP) non si toccano.
 */
subprojects {
    /*
     * Compose 1.13 (BOM 2026.11) chiede compileSdk 37.1 (23 novembre 2026): la versione minore si dà qui a tutti i moduli
     * Android; chi ne vuole una più alta la scrive nel suo android {} (Browser: 37.2 per GeckoView), che viene dopo.
     */
    listOf("com.android.application", "com.android.library").forEach { id ->
        plugins.withId(id) {
            extensions.configure(com.android.build.api.dsl.CommonExtension::class.java) { compileSdkMinor = 1 }
        }
    }
    /*
     * APK di prova non «debuggable» (22 novembre 2026, docs/SICUREZZA.md): sono quelli che il proprietario usa ogni giorno, con
     * dati veri. Da debuggable, con adb autorizzato, run-as leggeva i dati privati di Password, Messaggi, Posta…, e l'app
     * girava più lenta (niente codice precompilato, controlli JNI). Stesso pacchetto e stessa chiave: si aggiornano sopra.
     * Le prove sull'emulatore entrano nei dati con «adb root» (.github/scripts/emulatore-root.sh). Per un debugger:
     * -Peurise.debuggable=true. La variante (prova/ufficiale) si legge dal nome del pacchetto, mai da BuildConfig.DEBUG.
     */
    plugins.withId("com.android.application") {
        extensions.configure(com.android.build.api.dsl.ApplicationExtension::class.java) {
            buildTypes.getByName("debug").isDebuggable = providers.gradleProperty("eurise.debuggable").orNull == "true"
            /*
             * R8 anche negli APK di prova (22 novembre 2026, docs/SICUREZZA.md, «Pulizia e velocità»): senza, Compose gira
             * senza ottimizzazioni e l'avvio è lento. Regole prudenti in proguard-suite.pro (codice nostro tenuto, nomi
             * leggibili, librerie con riflessione tenute) più quelle di ogni app. Niente riduzione delle risorse (Launcher e
             * temi delle icone le cercano per nome). Il Browser ha già le sue; il motore di Office non si riduce (JNI).
             * Per spegnerlo in un'app sola: -Peurise.r8.senza=:mail,:chat (prova d'avvio a ogni app, prova-avvio.yml).
             */
            val senzaR8 = providers.gradleProperty("eurise.r8.senza").orNull.orEmpty().split(',').map { it.trim() }
            /*
             * R8 meno prudente (23 novembre 2026): le app in questo elenco non tengono più tutto il codice nostro
             * (proguard-nostro.pro), solo ciò che serve davvero. Una alla volta, con la prova d'avvio e poi un giro delle
             * funzioni sul telefono; se un'app si guasta, si toglie da qui.
             */
            val r8Pieno = setOf(":password")
            if (path != ":office:motore" && path != ":browser" && path !in senzaR8) {
                val regoleApp = listOf(file("proguard-rules.pro")).filter { it.isFile }
                val comuni = listOfNotNull(rootProject.file("proguard-suite.pro"), rootProject.file("proguard-nostro.pro").takeIf { path !in r8Pieno })
                buildTypes.getByName("debug") {
                    isMinifyEnabled = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                    comuni.forEach { proguardFile(it) }
                    regoleApp.forEach { proguardFile(it) }
                }
                comuni.forEach { buildTypes.getByName("release").proguardFile(it) }
            }
            // Da non debuggable Gradle farebbe girare il lint «vital» a ogni APK: il lint resta quello del lavoro apposito.
            lint.checkReleaseBuilds = false
        }
    }
    configurations.configureEach {
        // BouncyCastle: la variante jdk15to18 1.85 ha una classe in due archivi (bcprov e bcutil) e Android si ferma;
        // si passa alla variante jdk18on, la stessa degli strumenti di compilazione (Android ha Java 8 e oltre).
        resolutionStrategy.dependencySubstitution {
            listOf("bcprov", "bcpkix", "bcutil").forEach { m ->
                substitute(module("org.bouncycastle:$m-jdk15to18"))
                    .using(module("org.bouncycastle:$m-jdk18on:1.85"))
                    .because("BouncyCastle 1.72: vulnerabilità note (GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26 e altre)")
            }
        }
        resolutionStrategy.eachDependency {
            val r = requested
            if (r.group == "com.google.guava" && r.name == "guava" && (r.version ?: "").endsWith("-android")) {
                val v = r.version!!.removeSuffix("-android").split('.').map { it.toIntOrNull() ?: 0 }
                val old = v.getOrElse(0) { 0 } < 33 || (v.getOrElse(0) { 0 } == 33 && (v.getOrElse(1) { 0 } < 7 || (v.getOrElse(1) { 0 } == 7 && v.getOrElse(2) { 0 } < 2)))
                if (old) {
                    useVersion("33.7.2-android")
                    because("Guava sotto la 33.7.2: vulnerabilità note (GHSA-xxph-c9ww-hj94 e altre)")
                }
            }
        }
    }
}
