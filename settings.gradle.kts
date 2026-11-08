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
include(":core:cloud")
include(":core:links")
include(":core:suite")
include(":feature:clock")
include(":feature:today")
include(":feature:notes")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:launcher")
include(":feature:cloud")
