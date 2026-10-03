import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: read from a gitignored keystore.properties in the project root.
// If it is missing (e.g. a clean checkout), the release build falls back to the debug key
// so that `assembleRelease` still produces an installable APK.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true

val buildDate: String = ZonedDateTime.now(ZoneOffset.UTC)
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))

android {
    namespace = "se.eldebosh.nastastopp"
    compileSdk = 37

    defaultConfig {
        applicationId = "se.eldebosh.nastastopp"
        minSdk = 29
        targetSdk = 37
        versionCode = 36
        versionName = "1.35"
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Real phones only (the OCR native library is ~11 MB per ABI); debug keeps all ABIs
            // so it can also run on x86 emulators.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            // R8 is OFF on purpose: in R8 full mode (AGP 9 default) it removed the constructors
            // of ML Kit's component registrars, which ML Kit creates by reflection, so text
            // recognition failed on every image. Release now ships the same code as debug.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties not found: signing release with the debug key")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("ar", "sv", "en")
    }

    // In-app language switch: never split languages out of an app bundle.
    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Release is intentionally phone-only (arm ABIs), see DECISIONS.md.
        disable += "ChromeOsAbiSupport"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Bundled on-device OCR model (NOT the play-services unbundled variant).
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    // Robolectric smoke tests (no emulator/device needed): UI screens + route controller.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
