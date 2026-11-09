package eu.eurise.password.logic

import eu.eurise.password.kdbx.CardData
import eu.eurise.password.kdbx.EntryDraft
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

enum class ImportKind { Login, Card, Note, Code }

data class ImportItem(val kind: ImportKind, val draft: EntryDraft)

sealed interface ImportOutcome {
    /** [skipped]: voci di tipo che Eurise non conserva; [passkeys]: passkey che restano da rifare sui siti. */
    data class Ready(val items: List<ImportItem>, val skipped: Int, val passkeys: Int) : ImportOutcome
    /** Il file è cifrato: va esportato senza cifratura, altrimenti Eurise non può leggerlo. */
    data object Encrypted : ImportOutcome
    data object Unknown : ImportOutcome
}

/**
 * Riconosce da solo le esportazioni di Proton Pass (ZIP o data.json), Proton Authenticator, Aegis, 2FAS e i file di
 * testo con un indirizzo otpauth:// per riga. Lavora tutto sul telefono e non scrive nulla da nessuna parte.
 */
object Importer {
    fun read(input: InputStream): ImportOutcome {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val head = ByteArray(4).also { stream.read(it) }
        stream.reset()
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return readZip(stream)
        return parse(String(stream.readBytes(), Charsets.UTF_8))
    }

    /** Legge solo il file dei dati: gli allegati nello stesso ZIP non vengono caricati in memoria. */
    private fun readZip(stream: InputStream): ImportOutcome {
        val zip = ZipInputStream(stream)
        var encrypted = false
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.lowercase()
            if (name.endsWith("data.pgp")) encrypted = true
            if (name.endsWith(".json") || name.endsWith(".txt")) return parse(String(zip.readBytes(), Charsets.UTF_8))
        }
        return if (encrypted) ImportOutcome.Encrypted else ImportOutcome.Unknown
    }

    fun parse(text: String): ImportOutcome {
        val clean = text.trim().removePrefix("﻿")
        if (clean.startsWith("-----BEGIN PGP")) return ImportOutcome.Encrypted
        if (!clean.startsWith("{")) return otpLines(clean)
        val json = try {
            JSONObject(clean)
        } catch (e: org.json.JSONException) {
            return ImportOutcome.Unknown
        }
        return when {
            json.has("vaults") -> protonPass(json)
            json.has("db") -> aegis(json)
            json.has("services") -> twoFas(json)
            json.has("entries") -> protonAuthenticator(json)
            else -> ImportOutcome.Unknown
        }
    }

    private fun protonPass(json: JSONObject): ImportOutcome {
        if (json.optBoolean("encrypted", false)) return ImportOutcome.Encrypted
        val out = ArrayList<ImportItem>()
        var skipped = 0
        var passkeys = 0
        val vaults = json.getJSONObject("vaults")
        for (vaultId in vaults.keys()) {
            val items = vaults.getJSONObject(vaultId).optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optInt("state", 1) != 1) continue
                val data = item.getJSONObject("data")
                val meta = data.optJSONObject("metadata") ?: JSONObject()
                val content = data.optJSONObject("content") ?: JSONObject()
                val title = meta.str("name")
                val extras = extraFields(data.optJSONArray("extraFields"))
                val notes = listOf(meta.str("note"), extras.text).filter { it.isNotBlank() }.joinToString("\n\n")
                when (data.optString("type")) {
                    "login" -> {
                        val email = content.str("itemEmail")
                        val user = content.str("itemUsername").ifEmpty { content.str("username") }.ifEmpty { email }
                        val urls = content.optJSONArray("urls").strings()
                        val otherUrls = urls.drop(1).joinToString("\n")
                        val fullNotes = listOf(notes, otherUrls, if (email.isNotEmpty() && email != user) "Email: $email" else "").filter { it.isNotBlank() }.joinToString("\n\n")
                        val otp = content.str("totpUri").ifEmpty { extras.otp.orEmpty() }.ifEmpty { null }
                        passkeys += content.optJSONArray("passkeys")?.length() ?: 0
                        out += ImportItem(ImportKind.Login, EntryDraft(title, user, content.str("password"), urls.firstOrNull().orEmpty(), fullNotes, otp))
                    }
                    "creditCard" -> {
                        val pin = content.str("pin")
                        val cardNotes = listOf(notes, if (pin.isNotEmpty()) "PIN: $pin" else "").filter { it.isNotBlank() }.joinToString("\n\n")
                        val card = CardData(content.str("number"), content.str("cardholderName"), expiry(content.str("expirationDate")), content.str("verificationNumber"), "")
                        out += ImportItem(ImportKind.Card, EntryDraft(title, "", "", "", cardNotes, null, card))
                    }
                    "note" -> out += ImportItem(ImportKind.Note, EntryDraft(title, "", "", "", notes, null))
                    "alias" -> out += ImportItem(ImportKind.Login, EntryDraft(title, item.str("aliasEmail"), "", "", notes, null))
                    else -> skipped++
                }
            }
        }
        return ImportOutcome.Ready(out, skipped, passkeys)
    }

    private class Extras(val text: String, val otp: String?)

    private fun extraFields(fields: JSONArray?): Extras {
        if (fields == null) return Extras("", null)
        val lines = ArrayList<String>()
        var otp: String? = null
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            val data = field.optJSONObject("data") ?: continue
            if (field.optString("type") == "totp") {
                if (otp == null) otp = data.str("totpUri").ifEmpty { null }
            } else {
                val value = data.str("content").ifEmpty { data.str("timestamp") }
                if (value.isNotEmpty()) lines += field.str("fieldName") + ": " + value
            }
        }
        return Extras(lines.joinToString("\n"), otp)
    }

    /** "2028-03" e "032028" diventano "03/28"; ciò che non si riconosce resta com'è. */
    internal fun expiry(value: String): String {
        val v = value.trim()
        Regex("""^(\d{4})-(\d{1,2})$""").find(v)?.let { return it.groupValues[2].padStart(2, '0') + "/" + it.groupValues[1].takeLast(2) }
        Regex("""^(\d{2})(\d{4})$""").find(v)?.let { return it.groupValues[1] + "/" + it.groupValues[2].takeLast(2) }
        return v
    }

    private fun aegis(json: JSONObject): ImportOutcome {
        val db = json.optJSONObject("db") ?: return ImportOutcome.Encrypted
        val entries = db.optJSONArray("entries") ?: return ImportOutcome.Unknown
        val out = ArrayList<ImportItem>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val info = e.optJSONObject("info")
            if (e.optString("type") != "totp" || info == null) { skipped++; continue }
            out += code(e.str("issuer").ifEmpty { e.str("name") }, e.str("name"), uri(info.str("secret"), info.str("algo"), info.optInt("digits", 6), info.optInt("period", 30)))
        }
        return ImportOutcome.Ready(out, skipped, 0)
    }

    private fun twoFas(json: JSONObject): ImportOutcome {
        val services = json.optJSONArray("services") ?: return ImportOutcome.Unknown
        if (services.length() == 0 && json.has("servicesEncrypted")) return ImportOutcome.Encrypted
        val out = ArrayList<ImportItem>()
        var skipped = 0
        for (i in 0 until services.length()) {
            val s = services.getJSONObject(i)
            val otp = s.optJSONObject("otp") ?: JSONObject()
            val type = otp.str("tokenType").ifEmpty { "TOTP" }
            if (!type.equals("TOTP", ignoreCase = true) || s.str("secret").isEmpty()) { skipped++; continue }
            out += code(s.str("name").ifEmpty { otp.str("issuer") }, otp.str("account"), uri(s.str("secret"), otp.str("algorithm"), otp.optInt("digits", 6), otp.optInt("period", 30)))
        }
        return ImportOutcome.Ready(out, skipped, 0)
    }

    /** Proton non documenta i nomi dei campi: si cerca l'indirizzo otpauth:// dentro ogni voce, ovunque si trovi. */
    private fun protonAuthenticator(json: JSONObject): ImportOutcome {
        val entries = json.optJSONArray("entries") ?: return ImportOutcome.Encrypted
        val out = ArrayList<ImportItem>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val found = findOtp(entry)
            if (found == null) { skipped++; continue }
            out += fromUri(found, entry.optJSONObject("content")?.str("name").orEmpty())
        }
        return ImportOutcome.Ready(out, skipped, 0)
    }

    private fun findOtp(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.startsWith("otpauth://totp/", ignoreCase = true) }
        is JSONObject -> value.keys().asSequence().firstNotNullOfOrNull { findOtp(value.opt(it)) }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findOtp(value.opt(it)) }
        else -> null
    }

    private fun otpLines(text: String): ImportOutcome {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val uris = lines.filter { it.startsWith("otpauth://totp/", ignoreCase = true) && "secret=" in it.lowercase() }
        if (uris.isEmpty()) return ImportOutcome.Unknown
        return ImportOutcome.Ready(uris.map { fromUri(it, "") }, lines.size - uris.size, 0)
    }

    private fun fromUri(uri: String, name: String): ImportItem {
        val label = decode(uri.substringAfter("://").substringAfter('/').substringBefore('?'))
        val issuer = uri.substringAfter('?', "").split('&').firstOrNull { it.startsWith("issuer=", ignoreCase = true) }
            ?.substringAfter('=')?.let(::decode).orEmpty()
        val account = label.substringAfter(':', label).trim()
        val fromLabel = if (':' in label) label.substringBefore(':').trim() else ""
        return code(issuer.ifEmpty { fromLabel }.ifEmpty { name }.ifEmpty { account }, if (account == name) "" else account, uri)
    }

    private fun code(title: String, account: String, uri: String) =
        ImportItem(ImportKind.Code, EntryDraft(title, account, "", "", "", uri))

    private fun uri(secret: String, algorithm: String, digits: Int, period: Int) =
        "otpauth://totp/?secret=$secret&algorithm=${algorithm.ifEmpty { "SHA1" }.uppercase()}&digits=$digits&period=$period"

    private fun decode(text: String) = runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
}
