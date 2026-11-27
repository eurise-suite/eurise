package eu.eurise.postazione.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Un contatto con la stellina e il numero da comporre. */
data class Preferito(val nome: String, val numero: String) {
    val iniziali: String
        get() = nome.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

/**
 * I contatti preferiti (quelli con la stellina nella rubrica) per la scheda Viaggio. Serve il permesso dei contatti,
 * chiesto solo accendendo la funzione dopo la spiegazione. Si leggono nome e numero dei soli preferiti; il tocco apre il
 * tastierino con il numero già scritto (la Postazione non chiama da sola e non ha il permesso di farlo).
 */
object Preferiti {
    /** Permesso di Android e interruttore della Postazione ([Uso.Contatti]). */
    fun permesso(context: Context): Boolean = concesso(context) && Uso.Contatti.acceso(context)

    fun concesso(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Da chiamare fuori dal thread principale. */
    fun leggi(context: Context, max: Int = 5): List<Preferito> {
        if (!permesso(context)) return emptyList()
        val trovati = LinkedHashMap<Long, Preferito>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER),
                Phone.STARRED + "=1", null, Phone.IS_SUPER_PRIMARY + " DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val numero = c.getString(2).orEmpty()
                    if (id !in trovati && numero.isNotBlank()) trovati[id] = Preferito(c.getString(1).orEmpty(), numero)
                }
            }
        }
        return trovati.values.sortedBy { it.nome.lowercase() }.take(max)
    }
}
