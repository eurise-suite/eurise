package eu.eurise.core.data.alfred

import java.util.UUID

/**
 * Chi apre Alfred con una frase già scritta. AlfredActivity è esportata (tasto dell'assistente, Office): una frase che
 * arriva da un'altra app non si esegue da sola, resta nel campo e parte solo con Invio (tavola AlfFinestra). Le parti
 * dell'app Eurise (dock, Scrivania) mettono questo segno, che esiste solo nella memoria del processo e cambia a ogni avvio.
 */
object AlfredTrust {
    const val EXTRA = "eu.eurise.extra.ALFRED_TRUST"
    val token: String = UUID.randomUUID().toString()
}
