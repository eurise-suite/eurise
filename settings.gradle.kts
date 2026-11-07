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

rootProject.name = "eurise"

include(":app")
include(":core:designsystem")
include(":core:data")
include(":feature:today")
include(":feature:notes")
include(":feature:tasks")
include(":feature:calendar")
