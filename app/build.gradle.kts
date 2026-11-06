plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firma delle versioni ufficiali: i dati della chiave arrivano solo da variabili d'ambiente
 * (in GitHub Actions dai "secrets" del repository). La chiave non entra mai nel codice.
 */
val releaseKeystore: String? = System.getenv("EURISE_KEYSTORE_PATH")

android {
    namespace = "eu.eurise.hub"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.eurise.hub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("EURISE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EURISE_KEY_ALIAS")
                keyPassword = System.getenv("EURISE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        // Niente blocco di metadati firmato da Google: richiesto da F-Droid.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:today"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:tasks"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
}
