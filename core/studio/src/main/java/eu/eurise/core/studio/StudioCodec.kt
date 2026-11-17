package eu.eurise.core.studio

import org.json.JSONArray
import org.json.JSONObject

/** Lo Studio in JSON (versione 1), per l'archivio cifrato sul telefono. Ciò che non si legge si salta, non si perde il resto. */
object StudioCodec {
    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)

    private fun JSONObject.str(key: String): String? = optString(key).takeIf { has(key) && !isNull(key) && it.isNotEmpty() }

    fun encode(s: StudioState): String = JSONObject()
        .put("v", 1)
        .put("enabled", s.enabled)
        .put("enabledAt", s.enabledAt)
        .put("patients", JSONArray().apply { s.patients.forEach { put(patient(it)) } })
        .put("requests", JSONArray().apply { s.requests.forEach { put(request(it)) } })
        .put("codes", JSONArray().apply { s.codes.forEach { put(JSONObject().put("p", it.patientId).put("c", it.code).put("t", it.createdAt)) } })
        .toString()

    fun decode(text: String): StudioState {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return StudioState()
        fun <T> list(key: String, read: (JSONObject) -> T?): List<T> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> runCatching { read(a.getJSONObject(i)) }.getOrNull() }
        }
        return StudioState(
            enabled = o.optBoolean("enabled"),
            enabledAt = o.optLong("enabledAt"),
            patients = list("patients", ::patient),
            requests = list("requests", ::request),
            codes = list("codes") { PendingCode(it.getString("p"), it.getString("c"), it.getLong("t")) },
        )
    }

    private fun patient(p: Patient) = JSONObject()
        .put("id", p.id).put("name", p.name)
        .put("practices", JSONArray(p.practices.map { it.name }))
        .putOpt("matrix", p.matrixId).putOpt("email", p.email).putOpt("phone", p.phone)
        .put("preferred", p.preferred.name)
        .putOpt("verifiedAt", p.verifiedAt).putOpt("verifiedBy", p.verifiedBy?.name)
        .put("consents", JSONArray().apply { p.consents.forEach { put(JSONObject().put("c", it.consent.name).put("g", it.granted).put("t", it.at)) } })
        .put("therapies", JSONArray().apply { p.therapies.forEach { put(JSONObject().put("id", it.id).put("n", it.name).put("d", it.dose).put("s", it.since)) } })
        .put("created", p.createdAt)

    private fun patient(o: JSONObject): Patient {
        val pr = o.optJSONArray("practices")
        val practices = (0 until (pr?.length() ?: 0)).mapNotNull { i -> runCatching { Practice.valueOf(pr!!.getString(i)) }.getOrNull() }.toSet()
        val cs = o.optJSONArray("consents") ?: JSONArray()
        val th = o.optJSONArray("therapies") ?: JSONArray()
        return Patient(
            id = o.getString("id"),
            name = o.getString("name"),
            practices = practices.ifEmpty { setOf(Practice.General) },
            matrixId = o.str("matrix"), email = o.str("email"), phone = o.str("phone"),
            preferred = enumOf(o.str("preferred"), Channel.Chat),
            verifiedAt = if (o.has("verifiedAt")) o.getLong("verifiedAt") else null,
            verifiedBy = o.str("verifiedBy")?.let { enumOf<Channel>(it, Channel.Chat) },
            consents = (0 until cs.length()).mapNotNull { i ->
                val c = cs.getJSONObject(i)
                runCatching { ConsentEvent(Consent.valueOf(c.getString("c")), c.getBoolean("g"), c.getLong("t")) }.getOrNull()
            },
            therapies = (0 until th.length()).map { i ->
                val t = th.getJSONObject(i)
                Therapy(t.getString("id"), t.getString("n"), t.optString("d"), t.optString("s"))
            },
            createdAt = o.optLong("created"),
        )
    }

    private fun request(r: Request) = JSONObject()
        .put("id", r.id).put("p", r.patientId).put("ch", r.channel.name).put("need", r.need.name).put("pr", r.practice.name)
        .put("text", r.text).put("t", r.time).put("ref", r.ref).putOpt("done", r.doneAt)

    private fun request(o: JSONObject) = Request(
        id = o.getString("id"), patientId = o.getString("p"),
        channel = enumOf(o.str("ch"), Channel.Chat), need = enumOf(o.str("need"), Need.Question),
        practice = enumOf(o.str("pr"), Practice.General),
        text = o.optString("text"), time = o.getLong("t"), ref = o.getString("ref"),
        doneAt = if (o.has("done")) o.getLong("done") else null,
    )
}
