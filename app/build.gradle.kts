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

/**
 * Sign in with Apple (web flow): the Services ID (`-PappleServiceId=…`, the ID token's audience; empty shows
 * "Apple sign-in isn't set up yet.") and its return URL, the backend's `v1/auth/apple/callback` (`-PappleRedirectUri=…`).
 * The return URL must be an https address registered on the Services ID, so debug builds use production's by default.
 */
val appleServiceId: String = providers.gradleProperty("appleServiceId").orNull.orEmpty()
val appleRedirectUri: String = providers.gradleProperty("appleRedirectUri").orNull
    ?: "https://moneytogether.dpdns.org/service/vocab/v1/auth/apple/callback"

/**
 * AdMob (iOS `GADApplicationIdentifier` / `AdMobInterstitialUnitID`): `-PadmobAppId=…` and `-PadmobInterstitialUnitId=…`.
 * The defaults are Google's test IDs, like the IDs checked in on iOS; replace both for release (docs/ADS_SETUP.md).
 */
val admobAppId: String = providers.gradleProperty("admobAppId").orNull ?: "ca-app-pub-3940256099942544~3347511713"
val admobInterstitialUnitId: String = providers.gradleProperty("admobInterstitialUnitId").orNull
    ?: "ca-app-pub-3940256099942544/1033173712"

/** Links next to the subscription (iOS `PremiumLinks`): `-PtermsUrl=…`, `-PprivacyPolicyUrl=…`; empty leaves a link out. */
val termsUrl: String = providers.gradleProperty("termsUrl").orNull.orEmpty()
val privacyPolicyUrl: String = providers.gradleProperty("privacyPolicyUrl").orNull.orEmpty()

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
        buildConfigField("String", "APPLE_SERVICE_ID", "\"$appleServiceId\"")
        buildConfigField("String", "APPLE_REDIRECT_URI", "\"$appleRedirectUri\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitialUnitId\"")
        buildConfigField("String", "TERMS_URL", "\"$termsUrl\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
        manifestPlaceholders["admobAppId"] = admobAppId
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
    implementation(libs.androidx.browser)

    implementation(libs.billing)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    implementation(libs.rive.android)
    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
