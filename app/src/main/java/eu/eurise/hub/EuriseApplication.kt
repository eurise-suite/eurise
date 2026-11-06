package eu.eurise.hub

import android.app.Application
import eu.eurise.core.data.EuriseData

/** Crea una sola volta l'accesso ai dati e lo condivide con tutte le schermate. */
class EuriseApplication : Application() {
    val data: EuriseData by lazy { EuriseData.create(this) }
}
