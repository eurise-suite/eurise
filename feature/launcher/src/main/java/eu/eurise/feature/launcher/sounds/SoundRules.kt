package eu.eurise.feature.launcher.sounds

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer

/** Per che cosa suona un suono (le quattro schede di Suoni). */
enum class SoundUse { Calls, Messages, Email, Other }

/** Valori speciali al posto dell'id di un suono. */
object SoundIds {
    /** Nessun suono (resta la vibrazione, se il telefono la usa). */
    const val SILENT = "silent"
}

/**
 * Una persona con i suoi suoni. [key] è la chiave di ricerca della rubrica; numeri ed email servono a riconoscerla
 * nelle notifiche delle app che li mettono (SMS, posta), il nome per le chat che mostrano solo quello.
 */
data class PersonRule(
    val key: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val calls: String? = null,
    val messages: String? = null,
    val email: String? = null,
    /** Suona anche con Non disturbare (scelto dall'utente, persona per persona). */
    val breakDnd: Boolean = false,
) {
    fun soundFor(use: SoundUse): String? = when (use) {
        SoundUse.Calls -> calls
        SoundUse.Messages -> messages
        SoundUse.Email -> email
        SoundUse.Other -> null
    }

    /** «Tutto» quando chiamate, messaggi ed email hanno lo stesso suono. */
    val sameForAll: String? get() = calls?.takeIf { it == messages && it == email }
}

/** Il suono di un'app; [groups] per le conversazioni di gruppo; fuori dalle ore [fromHour]–[toHour] resta in silenzio. */
data class AppRule(
    val pkg: String,
    val label: String,
    val sound: String,
    val groups: String? = null,
    val fromHour: Int? = null,
    val toHour: Int? = null,
)

data class SoundSettings(
    /** Suono generale per uso; per Chiamate e Altro è anche la suoneria e l'avviso del sistema. */
    val defaults: Map<SoundUse, String> = emptyMap(),
    val persons: List<PersonRule> = emptyList(),
    val apps: List<AppRule> = emptyList(),
) {
    val isEmpty: Boolean get() = persons.isEmpty() && apps.isEmpty() && defaults[SoundUse.Messages] == null && defaults[SoundUse.Email] == null
}

/** Chi ha mandato la notifica, per quanto l'app lo dice. */
data class Sender(val names: List<String> = emptyList(), val phones: List<String> = emptyList(), val emails: List<String> = emptyList())

/** Che cosa fare per una notifica. */
sealed interface SoundChoice {
    data class Play(val sound: String, val breakDnd: Boolean = false) : SoundChoice
    data object Silent : SoundChoice
    /** Nessuna regola: suona com'è sempre stato (l'app o il sistema). */
    data object Untouched : SoundChoice
}

/**
 * La regola più precisa vince: prima la persona (fuori dai gruppi), poi l'app, poi il suono generale dell'uso.
 * Logica pura, provata dai test.
 */
object SoundDecider {
    fun pick(s: SoundSettings, pkg: String, use: SoundUse, sender: Sender?, isGroup: Boolean, hour: Int): SoundChoice {
        if (sender != null && !isGroup) {
            val person = s.persons.firstOrNull { matches(it, sender) }
            person?.soundFor(use)?.let { return choice(it, person.breakDnd) }
        }
        s.apps.firstOrNull { it.pkg == pkg }?.let { app ->
            if (!inHours(hour, app.fromHour, app.toHour)) return SoundChoice.Silent
            return choice(if (isGroup) app.groups ?: app.sound else app.sound, false)
        }
        if (use == SoundUse.Messages || use == SoundUse.Email) s.defaults[use]?.let { return choice(it, false) }
        return SoundChoice.Untouched
    }

    private fun choice(sound: String, breakDnd: Boolean) =
        if (sound == SoundIds.SILENT) SoundChoice.Silent else SoundChoice.Play(sound, breakDnd)

    /** Ore come 9–18 (dalle 9 alle 18) o 22–7 (scavalca la mezzanotte). */
    fun inHours(hour: Int, from: Int?, to: Int?): Boolean {
        if (from == null || to == null || from == to) return true
        return if (from < to) hour in from until to else hour >= from || hour < to
    }

    fun matches(p: PersonRule, s: Sender): Boolean {
        if (s.phones.any { n -> p.phones.any { samePhone(it, n) } }) return true
        if (s.emails.any { e -> p.emails.any { it.equals(e.trim(), ignoreCase = true) } }) return true
        val name = norm(p.name)
        return name.isNotEmpty() && s.names.any { norm(it) == name }
    }

    /** Ultime nove cifre: «+39 347 123 4567» = «3471234567». */
    fun samePhone(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit)
        val y = b.filter(Char::isDigit)
        if (x.length < 6 || y.length < 6) return false
        return x.takeLast(9) == y.takeLast(9)
    }

    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

/** Le scelte dei suoni, sul telefono. */
object SoundStore {
    private const val PREFS = "eurise_sounds"
    private val _settings = MutableStateFlow(SoundSettings())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()
    @Volatile private var loaded = false

    fun get(context: Context): SoundSettings {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                _settings.value = decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("rules", null))
                loaded = true
            }
        }
        return _settings.value
    }

    fun update(context: Context, transform: (SoundSettings) -> SoundSettings) {
        val next = transform(get(context))
        _settings.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("rules", encode(next)).apply()
    }

    /** App che suonano ancora per conto loro (canale con suono), viste dall'ascolto delle notifiche, con il canale. */
    fun appSounds(context: Context): Map<String, String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("app_sounds", "").orEmpty().lines()
            .mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    fun noteAppSound(context: Context, pkg: String, channel: String?) {
        val now = appSounds(context)
        val next = if (channel == null) now - pkg else now + (pkg to channel)
        if (next == now) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("app_sounds", next.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    // Formato: una riga per voce, campi separati da tabulazioni (che nei nomi non entrano).
    internal fun encode(s: SoundSettings): String = buildList {
        s.defaults.forEach { (use, id) -> add("d\t${use.name}\t$id") }
        s.persons.forEach { p ->
            add(listOf("p", p.key, clean(p.name), p.phones.joinToString(","), p.emails.joinToString(","),
                p.calls.orEmpty(), p.messages.orEmpty(), p.email.orEmpty(), if (p.breakDnd) "1" else "0").joinToString("\t"))
        }
        s.apps.forEach { a ->
            add(listOf("a", a.pkg, clean(a.label), a.sound, a.groups.orEmpty(), a.fromHour?.toString().orEmpty(), a.toHour?.toString().orEmpty()).joinToString("\t"))
        }
    }.joinToString("\n")

    internal fun decode(text: String?): SoundSettings {
        if (text.isNullOrBlank()) return SoundSettings()
        val defaults = HashMap<SoundUse, String>()
        val persons = ArrayList<PersonRule>()
        val apps = ArrayList<AppRule>()
        text.lines().forEach { line ->
            val f = line.split('\t')
            when (f.firstOrNull()) {
                "d" -> if (f.size >= 3) SoundUse.entries.firstOrNull { it.name == f[1] }?.let { defaults[it] = f[2] }
                "p" -> if (f.size >= 9) persons += PersonRule(
                    f[1], f[2], f[3].split(',').filter(String::isNotBlank), f[4].split(',').filter(String::isNotBlank),
                    f[5].ifEmpty { null }, f[6].ifEmpty { null }, f[7].ifEmpty { null }, f[8] == "1",
                )
                "a" -> if (f.size >= 7) apps += AppRule(f[1], f[2], f[3], f[4].ifEmpty { null }, f[5].toIntOrNull(), f[6].toIntOrNull())
            }
        }
        return SoundSettings(defaults, persons, apps)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
