import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    // One UMD file the plain-HTML web player loads with a <script> tag: window.ebuzzShared
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "ebuzz-shared.js"
                output.library = "ebuzzShared"
                output.libraryTarget = "umd"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":adultfilter"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            implementation("io.ktor:ktor-client-core:3.1.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }
        // HttpURLConnection engine: no OkHttp/Okio in the APK (Media3 streams over HttpURLConnection too)
        androidMain.dependencies { implementation("io.ktor:ktor-client-android:3.1.1") }
        jsMain.dependencies { implementation("io.ktor:ktor-client-js:3.1.1") }
    }
}

android {
    namespace = "world.ebuzz.tv.shared"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
