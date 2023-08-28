pluginManagement {
    repositories {
        // Prefer mavenCentral as that has signed artifacts
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        val customLintRepo = System.getenv("LINT_REPO")
        if (customLintRepo != null) {
            logger.warn("Building using custom $customLintRepo maven repository")
            maven { url = uri(customLintRepo) }
        }
    }
    versionCatalogs {
        create("libs") {
            val lintOverride = System.getenv("LINT_VERSION")
            if (lintOverride != null) {
                logger.warn("Building using custom $lintOverride version of Android Lint.")
                version("androidLint", lintOverride)
            }
        }
    }
}

if (!System.getenv("INTEGRATION").isNullOrBlank()) {
    include(":integration")
}
include(":stub-annotations")