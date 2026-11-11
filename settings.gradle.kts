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
        // GeckoView (motore di Eurise Browser): solo i pacchetti di Mozilla da qui.
        maven("https://maven.mozilla.org/maven2/") { content { includeGroupByRegex("org\\.mozilla\\..*") } }
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
// Eurise Browser (GeckoView, circa 50 MB): solo nel suo lavoro, per non appesantire gli altri.
if (System.getenv("EURISE_BROWSER") == "1") include(":browser")
include(":core:designsystem")
include(":core:data")
include(":core:cloud")
include(":core:links")
include(":core:suite")
include(":core:contacts")
include(":core:ponte")
include(":core:files")
// Eurise per il computer (Linux): per ora il Ponte da riga di comando.
include(":desktop:ponte")
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
include(":feature:ponte")
