package eu.eurise.core.insieme

import android.content.Context
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SuiteAccess
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persone e ruoli (tavola InsRuoli), senza Android nella parte di logica: si prova con i test.
 *
 * Si dice una volta chi è chi: capo, segreteria, collaboratore, collega, famiglia, amici. Da qui partono le proposte quando
 * affidi, condividi o passi la palla, e lo spazio (Lavoro, Famiglia, Amici, tavola SpaSpazi) di ogni conversazione. Niente esce
 * finché non condividi tu: il ruolo dice solo cosa vede di base quando lo fai (lo stato, vede, modifica), e si cambia persona
 * per persona. I ruoli stanno nella Rubrica (APK Telefono); Chat ed Email li leggono e li scrivono con [RuoliContract].
 */
object Ruoli {
    enum class Role { Capo, Segreteria, Collaboratore, Collega, Famiglia, Amici }
    enum class Level { Stato, Vede, Modifica }
    enum class Space { Lavoro, Famiglia, Amici }

    /** [key]: il contatto della Rubrica («lookup» o l'indirizzo); [addresses]: numeri, email e utenti della Chat della persona. */
    data class Person(val key: String, val name: String, val role: Role, val level: Level? = null, val addresses: List<String> = emptyList()) {
        val shown get() = level ?: defaultLevel(role)
    }

    fun defaultLevel(r: Role): Level = when (r) {
        Role.Capo -> Level.Stato
        Role.Segreteria, Role.Amici -> Level.Vede
        Role.Collaboratore, Role.Collega, Role.Famiglia -> Level.Modifica
    }

    fun label(r: Role): Int = when (r) {
        Role.Capo -> R.string.ins_role_capo; Role.Segreteria -> R.string.ins_role_segreteria; Role.Collaboratore -> R.string.ins_role_collab
        Role.Collega -> R.string.ins_role_collega; Role.Famiglia -> R.string.ins_role_famiglia; Role.Amici -> R.string.ins_role_amici
    }

    fun label(l: Level): Int = when (l) { Level.Stato -> R.string.ins_level_stato; Level.Vede -> R.string.ins_level_vede; Level.Modifica -> R.string.ins_level_modifica }

    fun label(s: Space): Int = when (s) { Space.Lavoro -> R.string.ins_space_lavoro; Space.Famiglia -> R.string.ins_space_famiglia; Space.Amici -> R.string.ins_space_amici }

    fun space(r: Role): Space = when (r) {
        Role.Capo, Role.Segreteria, Role.Collaboratore, Role.Collega -> Space.Lavoro
        Role.Famiglia -> Space.Famiglia
        Role.Amici -> Space.Amici
    }

    /** Un indirizzo confrontabile: email e utenti della Chat in minuscolo, numeri con le sole cifre (senza +39 o 0039 davanti). */
    fun norm(address: String): String {
        val a = address.trim().removePrefix("mailto:").removePrefix("tel:").substringBefore('#')
        if (a.startsWith("@") || a.contains('@')) return a.lowercase()
        val digits = a.filter { it.isDigit() }
        if (digits.length < 6) return a.lowercase()
        return when {
            a.startsWith("+39") -> digits.removePrefix("39")
            digits.startsWith("0039") -> digits.removePrefix("0039")
            else -> digits
        }
    }

    fun find(list: List<Person>, address: String): Person? {
        val n = norm(address)
        return list.firstOrNull { p -> p.addresses.any { norm(it) == n } }
    }

    /** Cosa fa ognuno, per le proposte: a chi si affida o si passa la palla prima (collaboratori, segreteria, colleghi). */
    fun order(r: Role?): Int = when (r) {
        Role.Collaboratore -> 0; Role.Segreteria -> 1; Role.Collega -> 2; Role.Capo -> 3; Role.Famiglia -> 4; Role.Amici -> 5; null -> 6
    }

    /** Mette, cambia o toglie (con [role] null) il ruolo della persona con [key]. */
    fun set(list: List<Person>, key: String, name: String, role: Role?, level: Level?, addresses: List<String>): List<Person> {
        val rest = list.filterNot { it.key == key }
        return if (role == null) rest else rest + Person(key, name.trim(), role, level, addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }

    fun encode(list: List<Person>): String = JSONArray().apply {
        list.forEach { p -> put(JSONObject().put("key", p.key).put("name", p.name).put("role", p.role.name).put("level", p.level?.name ?: "").put("addresses", JSONArray(p.addresses))) }
    }.toString()

    fun decode(s: String?): List<Person> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val role = runCatching { Role.valueOf(o.optString("role")) }.getOrNull() ?: return@mapNotNull null
            val level = o.optString("level").takeIf { it.isNotEmpty() }?.let { l -> runCatching { Level.valueOf(l) }.getOrNull() }
            val addr = o.optJSONArray("addresses")?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            Person(o.optString("key").ifEmpty { return@mapNotNull null }, o.optString("name"), role, level, addr)
        }
    }.getOrDefault(emptyList())
}

/**
 * I ruoli tra le app: stanno nella Rubrica (fornitore `<Telefono>.persone`, solo app della suite); Chat ed Email li chiedono e
 * li cambiano da qui. Se la Rubrica non c'è, la lista è vuota e il ruolo non si salva.
 */
object RuoliContract {
    const val METHOD_ROLES = "roles"
    const val METHOD_SET_ROLE = "set_role"
    const val EXTRA_RESULT = "result"
    const val EXTRA_PERSON = "person"
    const val EXTRA_KEY = "key"

    private fun uri(context: Context) = Uri.parse("content://eu.eurise.phone" + (if (SuiteAccess.isDebug(context)) ".debug" else "") + ".persone")

    fun all(context: Context): List<Ruoli.Person> = runCatching {
        Ruoli.decode(SuiteAccess.call(context, uri(context), METHOD_ROLES, null, null)?.getString(EXTRA_RESULT))
    }.getOrDefault(emptyList())

    /** Mette il ruolo (o lo toglie con [role] null). Vero se la Rubrica l'ha salvato. */
    fun set(context: Context, key: String, name: String, role: Ruoli.Role?, level: Ruoli.Level?, addresses: List<String>): Boolean = runCatching {
        val p = JSONObject().put("key", key).put("name", name).put("role", role?.name ?: "").put("level", level?.name ?: "").put("addresses", JSONArray(addresses))
        SuiteAccess.call(context, uri(context), METHOD_SET_ROLE, null, Bundle().apply { putString(EXTRA_PERSON, p.toString()) })?.getBoolean(EXTRA_RESULT) == true
    }.getOrDefault(false)

    /** Una richiesta di [set]: [role] null vuol dire togliere il ruolo. */
    data class Request(val key: String, val name: String, val role: Ruoli.Role?, val level: Ruoli.Level?, val addresses: List<String>)

    /** Dal fornitore: la richiesta di [set] letta. */
    fun readSet(extras: Bundle?): Request? = runCatching {
        val o = JSONObject(extras?.getString(EXTRA_PERSON) ?: return null)
        val role = o.optString("role").takeIf { it.isNotEmpty() }?.let { Ruoli.Role.valueOf(it) }
        val level = o.optString("level").takeIf { it.isNotEmpty() }?.let { Ruoli.Level.valueOf(it) }
        val addr = o.optJSONArray("addresses")?.let { x -> (0 until x.length()).map { x.optString(it) } }.orEmpty()
        Request(o.getString("key"), o.optString("name"), role, level, addr)
    }.getOrNull()
}
