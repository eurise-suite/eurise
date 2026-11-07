package eu.eurise.core.data.dates

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.MonthDay

/** Una data da ricordare: compleanno o anniversario, con l'anno se noto (per contare gli anni). */
data class ImportantDate(
    val id: Long,
    val name: String,
    val month: Int,
    val day: Int,
    val year: Int?,
    val kind: Kind,
    /** Il proprio compleanno o il proprio anniversario: cambiano anche lo sfondo della Scrivania. */
    val mine: Boolean,
    val relation: Relation = if (mine) Relation.Self else Relation.Other,
    /** "Regalo per …?" tra le cose da fare dieci giorni prima, finché non lo si spunta o passa il giorno. */
    val giftReminder: Boolean = relation.closeFamily,
    /** Avviso il giorno prima e il giorno stesso. */
    val notify: Boolean = true,
) {
    enum class Kind {
        Birthday, Wedding, Engagement, Anniversary;

        val isAnniversary: Boolean get() = this != Birthday
    }

    /** Chi è per l'utente: decide il promemoria regalo predefinito. */
    enum class Relation(val closeFamily: Boolean) {
        Self(false), Partner(true), Child(true), Mother(true), Father(true), Sibling(true), Grandparent(true), Friend(false), Other(false)
    }

    val monthDay: MonthDay get() = MonthDay.of(month, day)

    /** Prossima ricorrenza a partire da [from] (compreso); il 29 febbraio cade il 28 negli anni non bisestili. */
    fun nextOn(from: LocalDate): LocalDate {
        fun inYear(y: Int) = if (month == 2 && day == 29 && !java.time.Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28) else LocalDate.of(y, month, day)
        val thisYear = inYear(from.year)
        return if (thisYear.isBefore(from)) inYear(from.year + 1) else thisYear
    }

    fun occursOn(date: LocalDate): Boolean = nextOn(date) == date

    /** Anni compiuti (o festeggiati) in quella data, se l'anno di partenza è noto. */
    fun yearsOn(date: LocalDate): Int? = year?.let { date.year - it }?.takeIf { it > 0 }
}

/**
 * Date importanti salvate sul telefono (nessuna sincronizzazione). Le leggono il Calendario, che le mostra
 * e le ricorda, e la Scrivania, che per le proprie cambia lo sfondo.
 */
class ImportantDatesRepository internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_dates", Context.MODE_PRIVATE)
    private val _dates = MutableStateFlow(load())
    val dates: StateFlow<List<ImportantDate>> = _dates.asStateFlow()

    fun save(date: ImportantDate) {
        val withId = if (date.id == 0L) date.copy(id = System.currentTimeMillis()) else date
        // Uno solo "mio" compleanno e un solo "mio" anniversario.
        val others = _dates.value.filterNot { it.id == withId.id }
            .map { if (withId.mine && it.mine && it.kind.isAnniversary == withId.kind.isAnniversary) it.copy(mine = false, relation = ImportantDate.Relation.Other) else it }
        persist((others + withId).sortedWith(compareBy({ it.month }, { it.day })))
    }

    fun delete(id: Long) = persist(_dates.value.filterNot { it.id == id })

    /** Aggiunge le date che non ci sono già (stesso nome e giorno). */
    fun addAll(list: List<ImportantDate>) {
        val existing = _dates.value
        var nextId = System.currentTimeMillis()
        val fresh = list.filter { n -> existing.none { it.name.equals(n.name, true) && it.month == n.month && it.day == n.day } }
            .map { it.copy(id = nextId++) }
        persist((existing + fresh).sortedWith(compareBy({ it.month }, { it.day })))
    }

    fun myBirthday(): ImportantDate? = _dates.value.firstOrNull { it.mine && it.kind == ImportantDate.Kind.Birthday }

    fun myAnniversary(): ImportantDate? = _dates.value.firstOrNull { it.mine && it.kind.isAnniversary }

    private fun persist(list: List<ImportantDate>) {
        _dates.value = list
        val array = JSONArray()
        list.forEach { d ->
            array.put(
                JSONObject().put("id", d.id).put("name", d.name).put("month", d.month).put("day", d.day)
                    .put("year", d.year ?: 0).put("kind", d.kind.name).put("mine", d.mine)
                    .put("relation", d.relation.name).put("gift", d.giftReminder).put("notify", d.notify),
            )
        }
        prefs.edit().putString("dates", array.toString()).apply()
    }

    private fun load(): List<ImportantDate> = runCatching {
        val array = JSONArray(prefs.getString("dates", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val mine = o.optBoolean("mine")
            val relation = runCatching { ImportantDate.Relation.valueOf(o.getString("relation")) }.getOrElse { if (mine) ImportantDate.Relation.Self else ImportantDate.Relation.Other }
            ImportantDate(
                o.getLong("id"), o.getString("name"), o.getInt("month"), o.getInt("day"),
                o.optInt("year").takeIf { it > 0 },
                runCatching { ImportantDate.Kind.valueOf(o.getString("kind")) }.getOrDefault(ImportantDate.Kind.Birthday),
                mine, relation, o.optBoolean("gift", relation.closeFamily), o.optBoolean("notify", true),
            )
        }
    }.getOrDefault(emptyList())
}
