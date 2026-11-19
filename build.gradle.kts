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
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val r = requested
            if (r.group == "org.bouncycastle" && r.name.endsWith("-jdk15to18") && (r.version ?: "") < "1.85") {
                useVersion("1.85")
                because("BouncyCastle 1.72: vulnerabilità note (GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26 e altre)")
            }
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
