plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eobapp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eobapp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    /**
     * Signing for CI. The keystore never lives in the repository: the workflow writes it from a
     * secret and passes its location and passwords as environment variables. Locally these are
     * unset, so this block does nothing and Android Studio's own signing dialog still works.
     */
    val ciKeystore = System.getenv("EOBAPP_KEYSTORE")
    signingConfigs {
        if (!ciKeystore.isNullOrBlank()) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("EOBAPP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EOBAPP_KEY_ALIAS")
                keyPassword = System.getenv("EOBAPP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (!ciKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ci")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        // Exposes versionName so Settings can show which build is installed.
        buildConfig = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // Service-token secret lives in the Android keystore-backed encrypted prefs.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
