package eu.eurise.core.data.meds

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Un farmaco da prendere: dose, orari di assunzione, giorni (vuoto = tutti) e data di fine facoltativa.
 */
data class Medicine(
    val id: Long,
    val name: String,
    val dose: String,
    val times: List<LocalTime>,
    val days: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
) {
    fun dueOn(date: LocalDate): Boolean =
        (days.isEmpty() || date.dayOfWeek in days) && (until == null || !date.isAfter(until))
}

/** Farmaci salvati sul telefono, senza sincronizzazione. */
class MedicinesRepository internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_meds", Context.MODE_PRIVATE)
    private val _medicines = MutableStateFlow(load())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()

    fun save(medicine: Medicine) {
        val withId = if (medicine.id == 0L) medicine.copy(id = System.currentTimeMillis()) else medicine
        persist((_medicines.value.filterNot { it.id == withId.id } + withId).sortedBy { it.name.lowercase() })
    }

    fun delete(id: Long) = persist(_medicines.value.filterNot { it.id == id })

    private fun persist(list: List<Medicine>) {
        _medicines.value = list
        val array = JSONArray()
        list.forEach { m ->
            array.put(
                JSONObject().put("id", m.id).put("name", m.name).put("dose", m.dose)
                    .put("times", m.times.joinToString(",") { it.toString() })
                    .put("days", m.days.joinToString(",") { it.value.toString() })
                    .put("until", m.until?.toString() ?: ""),
            )
        }
        prefs.edit().putString("meds", array.toString()).apply()
    }

    private fun load(): List<Medicine> = runCatching {
        val array = JSONArray(prefs.getString("meds", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Medicine(
                id = o.getLong("id"),
                name = o.getString("name"),
                dose = o.optString("dose"),
                times = o.optString("times").split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it) }.sorted(),
                days = o.optString("days").split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                until = o.optString("until").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            )
        }
    }.getOrDefault(emptyList())
}
