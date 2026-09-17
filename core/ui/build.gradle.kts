plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "world.ebuzz.tv.core.ui"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    api("androidx.appcompat:appcompat:1.7.0")
    api("androidx.fragment:fragment-ktx:1.8.5")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    api("androidx.recyclerview:recyclerview:1.4.0")
    api("io.coil-kt:coil:2.7.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
