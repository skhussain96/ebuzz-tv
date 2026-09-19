import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "world.ebuzz.tv.core.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        // tmdb.key in local.properties (never committed); empty = text and rating rules only
        val tmdbKey = rootProject.file("local.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) }.getProperty("tmdb.key") }.orEmpty()
        buildConfigField("String", "TMDB_KEY", "\"$tmdbKey\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }
}

dependencies {
    api(project(":shared"))
    implementation("androidx.core:core-ktx:1.15.0")
}
