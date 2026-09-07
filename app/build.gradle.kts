import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Live TV's configurable endpoints (see LiveTvConfig). None of these are
// secrets -- they're plain URLs -- but they're still kept out of version
// control via local.properties (already gitignored) so each deployment can
// point at its own backend/checkout/playlist without editing tracked files.
// Real payment credentials/API keys belong on the backend only and must
// never be added here.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun liveTvConfigValue(key: String, default: String): String {
    val raw = localProperties.getProperty(key)
        ?: (project.findProperty(key) as String?)
        ?: System.getenv(key)
        ?: default
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.mangotv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mangotv.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // See docs/LIVE_TV_BACKEND.md for what these mean and README.md for
        // how to override them via local.properties.
        buildConfigField("String", "IPTV_PLAYLIST_URL", "\"${liveTvConfigValue("IPTV_PLAYLIST_URL", "https://iptv-org.github.io/iptv/index.m3u")}\"")
        buildConfigField("String", "LIVE_TV_ALLOWED_GROUPS", "\"${liveTvConfigValue("LIVE_TV_ALLOWED_GROUPS", "")}\"")
        buildConfigField("String", "EPG_URL", "\"${liveTvConfigValue("EPG_URL", "")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${liveTvConfigValue("API_BASE_URL", "")}\"")
        buildConfigField("String", "PREMIUM_CHECKOUT_URL", "\"${liveTvConfigValue("PREMIUM_CHECKOUT_URL", "")}\"")
    }

    buildTypes {
        release {
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

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
}
