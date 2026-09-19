plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "world.ebuzz.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "world.ebuzz"
        minSdk = 23
        targetSdk = 35
        versionCode = 18
        versionName = "1.8.1"
    }

    // Two editions from one codebase. What differs is which feature modules each one links (see dependencies)
    // and the section list in src/<edition>/kotlin/.../Sections.kt.
    //   tv            – Live TV only.
    //   entertainment – Live TV + Movies.
    // Separate application ids, so both can be installed side by side.
    flavorDimensions += "edition"
    productFlavors {
        create("tv") {
            dimension = "edition"
            applicationIdSuffix = ".tv"
            resValue("string", "app_name", "eBuzz TV")
        }
        create("entertainment") {
            dimension = "edition"
            applicationIdSuffix = ".entertainment"
            resValue("string", "app_name", "eBuzz Entertainment")
        }
    }
    // Size: the UI is English-only, so the ~80 translated copies of library strings are dropped from the resource table.
    androidResources { localeFilters += "en" }
    packaging {
        resources.excludes += setOf(
            "kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module", "META-INF/com/android/build/gradle/**",
            "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/NOTICE*",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:playback"))       // contributes the player activity + service via manifest merge
    implementation(project(":core:link"))
    implementation(project(":feature:live"))

    // Only the Entertainment edition links these modules; the TV edition never sees them.
    "entertainmentImplementation"(project(":feature:movies"))
    "entertainmentImplementation"(project(":feature:music"))

    implementation("androidx.core:core-ktx:1.15.0")
}
