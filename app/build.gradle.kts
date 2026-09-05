import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.antidaze.pocketvm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antidaze.pocketvm"
        // targetSdk stays at 28 on purpose: Android only allows executing bundled
        // binaries from app data for legacy-target apps (same model Termux uses).
        minSdk = 26
        targetSdk = 28
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.4.1"
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file(
                keystoreProps.getProperty("storeFile", "keystore/pocketvm-release.keystore")
            ).takeIf { it.exists() }
            if (ksFile != null) {
                storeFile = ksFile
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }
    val hasReleaseSigning = signingConfigs.getByName("release").storeFile != null

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
}
