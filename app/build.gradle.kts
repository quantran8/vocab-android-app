plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** Backend override, the Android counterpart of iOS `VOCABFLOW_API_URL`: `-PvocabflowApiUrl=…` or gradle.properties. */
val apiUrlOverride: String? = providers.gradleProperty("vocabflowApiUrl").orNull

/**
 * Google sign-in: the backend's *Web* OAuth client ID (`….apps.googleusercontent.com`), the audience of the ID token.
 * `-PgoogleServerClientId=…` or gradle.properties; empty shows "Google sign-in isn't set up yet." (iOS `GoogleClientID`).
 */
val googleServerClientId: String = providers.gradleProperty("googleServerClientId").orNull.orEmpty()

android {
    namespace = "com.syll"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.syll"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The emulator reaches the host's local backend at 10.0.2.2 (iOS simulator: 127.0.0.1).
            buildConfigField("String", "API_URL", "\"${apiUrlOverride ?: "http://10.0.2.2:3000/"}\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField(
                "String",
                "API_URL",
                "\"${apiUrlOverride ?: "https://moneytogether.dpdns.org/service/vocab/"}\"",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Per-app language (Settings → Language), generated from AppLanguage's list.
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.rive.android)
    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
