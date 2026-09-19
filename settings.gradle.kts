pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "ebuzz-tv"
include(":adultfilter", ":shared")
include(":core:ui", ":core:data", ":core:playback", ":core:catalog", ":core:link")
include(":feature:live", ":feature:movies", ":feature:music")
include(":app")
