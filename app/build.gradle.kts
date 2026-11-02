plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma para release: Codemagic inyecta estas variables cuando se configura "android_signing"
val cmKeystorePath: String? = System.getenv("CM_KEYSTORE_PATH")

android {
    namespace = "com.appscz.cbfinance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.appscz.cbfinance"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Firma fija incluida en el proyecto: todas las versiones (debug y release) se instalan
        // una encima de otra sin perder datos. Para Play Store usa el keystore de Codemagic (ver codemagic.yaml).
        create("sideload") {
            storeFile = file("signing/cb-sideload.jks")
            storePassword = "cbconsultores"
            keyAlias = "cb"
            keyPassword = "cbconsultores"
        }
        if (cmKeystorePath != null) {
            create("cmrelease") {
                storeFile = file(cmKeystorePath)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            // R8 + recorte de recursos: la app release es bastante más rápida y liviana que la debug
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("CM_USE_PLAY_KEY") == "true" && cmKeystorePath != null) {
                signingConfigs.getByName("cmrelease")
            } else {
                signingConfigs.getByName("sideload")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Compatibilidad con los hashes de contraseña del sistema web (Werkzeug: pbkdf2 / scrypt)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
