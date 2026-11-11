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
include(":gallery")
include(":password")
include(":voice")
include(":phone")
include(":messages")
// Eurise Leggi (motore nativo, compilazione lunga): si compila solo nel suo lavoro, per non allungare le altre.
if (System.getenv("EURISE_READ") == "1") include(":read")
// Eurise AI (llama.cpp dai sorgenti, compilazione lunga): solo nel suo lavoro, come Leggi.
if (System.getenv("EURISE_AI") == "1") include(":ai")
include(":core:designsystem")
include(":core:data")
include(":core:cloud")
include(":core:links")
include(":core:suite")
include(":core:contacts")
include(":feature:clock")
include(":feature:today")
include(":feature:notes")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:launcher")
include(":feature:cloud")
include(":feature:drive")
include(":feature:scanner")
include(":feature:calculator")
include(":feature:weather")
include(":feature:privacy")
include(":feature:people")
