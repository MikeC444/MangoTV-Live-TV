import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// API_BASE_URL is developer/deployment-specific (like the local Postgres
// credentials on the server side), so it lives in the gitignored
// local.properties rather than being hardcoded here. The fallback is
// deliberately not a real-looking value — anyone who forgets to set it
// gets an obvious placeholder that fails loudly (DNS/connection error)
// instead of an app that silently tries to talk to nothing in particular.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
// Env var checked first so CI (which never has a local.properties file --
// it's gitignored, never committed) can inject the real backend URL from a
// repo secret for a release build; local dev keeps using local.properties
// exactly as before.
val apiBaseUrl: String =
    System.getenv("API_BASE_URL")
        ?: localProperties.getProperty("API_BASE_URL")
        ?: "https://not-configured.invalid"

// Milestone 14: fail the build, not just at runtime, if this ever points
// at plain http:// -- every account API client sends a bearer token on
// almost every request, and the manifest's usesCleartextTraffic="true"
// (needed for the pre-existing, unrelated addon ecosystem, which fetches
// arbitrary user-supplied http:// and https:// addon URLs by design) means
// the OS itself won't block a misconfigured http:// API_BASE_URL from
// being attempted. Enforcing the scheme here, at build-config generation
// time, is what actually closes that gap for this app's own account
// traffic specifically -- unlike a network security config, which can't
// reference a value only known at build time like this one.
require(apiBaseUrl.startsWith("https://")) {
    "API_BASE_URL must use https:// (was: $apiBaseUrl) -- this app sends bearer tokens on almost every account API request, which must never go out over plaintext HTTP."
}

// Set only by the release-publishing CI workflow (-PversionNameOverride=...
// -PversionCodeOverride=...), derived there from the git tag being
// released -- see .github/workflows/release.yml. Absent for every local/
// debug build, which keeps using the plain values below unchanged.
val versionNameOverride: String? = (project.findProperty("versionNameOverride") as String?)?.takeIf { it.isNotBlank() }
val versionCodeOverride: Int? = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull()

// Same env-var-first, local.properties-fallback pattern as apiBaseUrl above
// -- CI provides these from repo secrets (see .github/workflows/release.yml);
// a local release build can instead set them in local.properties. Left null
// (rather than defaulting to something) when neither is configured: signing
// a release build is only ever meaningful once a real keystore exists, and
// silently falling back to no signing at all should be visible as "release
// build type has no signingConfig", not hidden behind a fake default.
fun releaseSigningProperty(name: String): String? =
    System.getenv("RELEASE_$name") ?: localProperties.getProperty("RELEASE_$name")
val releaseKeystorePath = releaseSigningProperty("KEYSTORE_PATH")
val releaseKeystorePassword = releaseSigningProperty("KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("KEY_PASSWORD")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank()

android {
    namespace = "com.mangotv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mangotv.app"
        minSdk = 23
        targetSdk = 34
        versionCode = versionCodeOverride ?: 1
        versionName = versionNameOverride ?: "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    implementation(libs.tink.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
