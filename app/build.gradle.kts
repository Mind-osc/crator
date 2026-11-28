plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Το κλειδί υπογραφής δεν μπαίνει ποτέ στο repo: το CI το γράφει από τα GitHub Secrets
val signingFile = System.getenv("CRATOR_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.crator.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crator.launcher"
        minSdk = 31
        targetSdk = 35
        versionCode = 45
        versionName = "1.4.1"
    }

    signingConfigs {
        if (signingFile != null) create("release") {
            storeFile = signingFile
            storePassword = System.getenv("CRATOR_STORE_PASSWORD")
            keyAlias = System.getenv("CRATOR_KEY_ALIAS") ?: "crator"
            keyPassword = System.getenv("CRATOR_KEY_PASSWORD") ?: System.getenv("CRATOR_STORE_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
