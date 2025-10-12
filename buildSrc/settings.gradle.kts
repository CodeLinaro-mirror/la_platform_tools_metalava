pluginManagement {
    repositories {
        maven("../../prebuilts/androidx/external")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}