pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "ebuzz-tv"
include(":shared")
include(":core:ui", ":core:data", ":core:playback", ":core:catalog")
include(":feature:live", ":feature:movies", ":feature:music")
include(":app")
