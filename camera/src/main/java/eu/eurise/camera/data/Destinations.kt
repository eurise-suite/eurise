package eu.eurise.camera.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Dove va la foto (tavola FotDestinazione, 22 novembre 2026): il Rullino o una cartella per cliente o progetto, scelta
 * prima dello scatto. Logica pura, senza Android, così si prova senza telefono.
 *
 * Tocco breve sulla pillola = l'ultima usata (si scambia con quella attuale); pressione lunga = l'elenco.
 * La preferita (stellina) è quella con cui si apre la Fotocamera.
 */
data class DestinationState(
    val folders: List<String> = emptyList(),
    /** null = Rullino. */
    val current: String? = null,
    val previous: String? = null,
    val favorite: String? = null,
) {
    /** Tocco breve: torna all'ultima usata. Se non ce n'è un'altra, resta dov'è. */
    fun toggle(): DestinationState =
        if (previous == current || (previous != null && previous !in folders)) this
        else copy(current = previous, previous = current)

    fun select(dest: String?): DestinationState =
        if (dest == current) this else copy(current = dest, previous = current)

    fun add(raw: String): DestinationState {
        val name = Destinations.clean(raw) ?: return this
        val list = if (folders.any { it.equals(name, ignoreCase = true) }) folders else (folders + name).sortedBy { it.lowercase() }
        return select(list.first { it.equals(name, ignoreCase = true) }).copy(folders = list)
    }

    fun remove(name: String): DestinationState = copy(
        folders = folders - name,
        current = current.takeUnless { it == name },
        previous = previous.takeUnless { it == name },
        favorite = favorite.takeUnless { it == name },
    )

    fun star(dest: String?): DestinationState = copy(favorite = dest)

    /** All'apertura: la preferita. */
    fun atStart(): DestinationState = select(favorite?.takeIf { it in folders })
}

object Destinations {
    const val MAX_LENGTH = 40
    const val ROLL_PATH = "DCIM/Camera"
    const val FOLDERS_PATH = "DCIM/Eurise"

    /** Il nome di una cartella: lettere, cifre, spazi e pochi segni; niente barre o punti in testa. Null se vuoto. */
    fun clean(raw: String): String? {
        val kept = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '\'' || it == '.' || it == '&' }
        val name = kept.trim().trimStart('.').replace(Regex(" {2,}"), " ").take(MAX_LENGTH).trim()
        return name.ifEmpty { null }
    }

    /** La cartella di MediaStore (RELATIVE_PATH) della destinazione. */
    fun relativePath(dest: String?): String = if (dest == null) ROLL_PATH else "$FOLDERS_PATH/$dest"
}

/** Le scelte salvate della Fotocamera: poche chiavi, nessun database. */
class CameraPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("fotocamera", Context.MODE_PRIVATE)

    var locationOn: Boolean
        get() = prefs.getBoolean(KEY_LOCATION, false)
        set(value) { prefs.edit().putBoolean(KEY_LOCATION, value).apply() }

    fun destinations(): DestinationState = DestinationState(
        folders = prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty().sortedBy { it.lowercase() },
        current = prefs.getString(KEY_CURRENT, null),
        previous = prefs.getString(KEY_PREVIOUS, null),
        favorite = prefs.getString(KEY_FAVORITE, null),
    )

    fun save(state: DestinationState) {
        prefs.edit()
            .putStringSet(KEY_FOLDERS, state.folders.toSet())
            .putString(KEY_CURRENT, state.current)
            .putString(KEY_PREVIOUS, state.previous)
            .putString(KEY_FAVORITE, state.favorite)
            .apply()
    }

    private companion object {
        const val KEY_LOCATION = "posizione"
        const val KEY_FOLDERS = "cartelle"
        const val KEY_CURRENT = "attuale"
        const val KEY_PREVIOUS = "precedente"
        const val KEY_FAVORITE = "preferita"
    }
}
