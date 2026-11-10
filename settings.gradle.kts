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
        // Solo per il prototipo di Eurise Leggi, e solo per la libreria di Tesseract.
        if (System.getenv("EURISE_READ") == "1") {
            maven("https://jitpack.io") { content { includeGroup("cz.adaptech.tesseract4android") } }
        }
    }
}

rootProject.name = "eurise"

include(":app")
include(":gallery")
include(":password")
include(":voice")
// Prototipo di Eurise Leggi: si compila solo nel suo lavoro, per non toccare le altre compilazioni.
if (System.getenv("EURISE_READ") == "1") include(":read")
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
include(":feature:drive")
include(":feature:scanner")
include(":feature:calculator")
include(":feature:privacy")
include(":feature:people")
