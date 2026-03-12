pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Meshify"

// App module
include(":app")

// Core modules
include(":core:common")
include(":core:data")
include(":core:domain")
include(":core:network")
include(":core:ui")

// Feature modules
include(":feature:home")
include(":feature:chat")
include(":feature:discovery")
include(":feature:settings")
