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

settings.gradle.beforeProject {
    val outDir = if (System.getenv("OUT_DIR") != null) {
        File(System.getenv("OUT_DIR"))
    } else {
        File(rootDir, "../../out")
    }
    val suffix = "${path.replace(":", "/")}/build"
    layout.buildDirectory.set(File(outDir, "metalava$suffix"))
}

if (!System.getenv("INTEGRATION").isNullOrBlank()) {
    include(":integration")
}

include(":metalava-model")
include(":metalava-model-psi")
include(":metalava-model-testsuite")
include(":metalava-model-text")
include(":metalava-reporter")
include(":metalava-testing")
include(":stub-annotations")
