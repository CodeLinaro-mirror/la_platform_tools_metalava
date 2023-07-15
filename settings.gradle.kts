pluginManagement {
    repositories {
        // Prefer mavenCentral as that has signed artifacts
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

if (!System.getenv("INTEGRATION").isNullOrBlank()) {
    include(":integration")
}
include(":stub-annotations")