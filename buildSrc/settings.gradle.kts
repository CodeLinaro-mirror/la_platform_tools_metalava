pluginManagement {
    repositories {
        // For CI builds, it is important to use prebuilts because maven central throttles requests
        // for artifacts. The `DOWNLOAD_DEPENDENCIES` flag allows building locally in repos which do
        // not include the metalava prebuilts repo.
        if (System.getenv("DOWNLOAD_DEPENDENCIES") == "true") {
            // Prefer mavenCentral as that has signed artifacts
            mavenCentral()
            gradlePluginPortal()
        } else {
            // Use custom repo path if provided (necessary for androidx_with_metalava).
            val repos = System.getenv("METALAVA_PREBUILTS_REPOS")?.split(",") ?: listOf("../../prebuilts/metalava")
            repos.forEach { maven(it) }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        repositories {
            // For CI builds, it is important to use prebuilts because maven central throttles requests for
            // artifacts. The `DOWNLOAD_DEPENDENCIES` flag allows building locally in repos which do not
            // include the metalava prebuilts repo.
            if (System.getenv("DOWNLOAD_DEPENDENCIES") == "true") {
                mavenCentral()
                google()
            } else {
                // Use custom repo path if provided (necessary for androidx_with_metalava).
                val repos = System.getenv("METALAVA_PREBUILTS_REPOS")?.split(",") ?: listOf("../../prebuilts/metalava")
                repos.forEach { maven(it) }
            }
        }

    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}