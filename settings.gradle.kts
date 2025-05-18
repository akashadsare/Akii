pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Add the foojay-resolver-convention plugin to help Gradle find JDKs for toolchains
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version("0.10.0")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Akii"
include(":app")
