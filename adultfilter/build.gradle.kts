plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Pure Kotlin: no Android plugin, no dependencies, no I/O. Android consumes the jvm variant, the web the js one.
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    js(IR) { browser() }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1") }
    }
}
