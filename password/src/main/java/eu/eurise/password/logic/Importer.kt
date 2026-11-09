package eu.eurise.password.logic

import eu.eurise.password.autofill.Matching
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
    /** Un file KeePass: serve la sua frase principale per leggerlo. */
    class Keepass(val bytes: ByteArray) : ImportOutcome
    data object Unknown : ImportOutcome
}

/**
 * Riconosce da solo le esportazioni di Proton Pass, Bitwarden, Proton Authenticator, Aegis, 2FAS, i CSV dei browser e
 * dei gestori di password, i file KeePass, i QR di Google Authenticator e i testi con un indirizzo otpauth:// per riga. Lavora tutto sul telefono e non scrive nulla da nessuna parte.
 */
object Importer {
    fun read(input: InputStream): ImportOutcome {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val head = ByteArray(4).also { stream.read(it) }
        stream.reset()
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return readZip(stream)
        if (head[0] == 0x03.toByte() && head[1] == 0xD9.toByte() && head[2] == 0xA2.toByte() && head[3] == 0x9A.toByte()) {
            return ImportOutcome.Keepass(stream.readBytes())
        }
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
        if (clean.startsWith("otpauth", ignoreCase = true)) return otpLines(clean)
        if (!clean.startsWith("{")) return csv(clean) ?: otpLines(clean)
        val json = try {
            JSONObject(clean)
        } catch (e: org.json.JSONException) {
            return ImportOutcome.Unknown
        }
        return when {
            json.has("vaults") -> protonPass(json)
            json.has("items") -> bitwarden(json)
            json.has("db") -> aegis(json)
            json.has("services") -> twoFas(json)
            json.has("entries") -> protonAuthenticator(json)
            else -> ImportOutcome.Unknown
        }
    }

    /** Un CSV qualunque, dai nomi delle colonne: copre Chrome, Firefox, Edge, Brave, Proton Pass, Bitwarden, 1Password, KeePassXC. */
    private fun csv(text: String): ImportOutcome? {
        val rows = csvRows(text)
        if (rows.size < 2) return null
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val password = col("password", "login_password", "pass")
        val number = col("card number", "cardnumber", "number")
        if (password == null && number == null) return null
        val title = col("name", "title", "account", "item name")
        val url = col("url", "login_uri", "website", "web site", "uri", "urls", "hostname")
        val user = col("username", "login_username", "user name", "login name", "user")
        val email = col("email")
        val notes = col("note", "notes", "comments", "extra")
        val otp = col("totp", "login_totp", "otpauth", "otp", "one-time password", "one time password")
        val kind = col("type")
        val holder = col("cardholder", "cardholder name", "name on card")
        val expiryCol = col("expiry", "expiration", "expiration date", "exp")
        val cvv = col("cvv", "cvc", "security code", "verification number", "code")
        val out = ArrayList<ImportItem>()
        var skipped = 0
        for (row in rows.drop(1)) {
            fun at(i: Int?) = i?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val address = at(url)
            val name = at(title).ifEmpty { Matching.host(address) }
            val type = at(kind).lowercase()
            val cardNumber = at(number)
            when {
                cardNumber.isNotEmpty() -> out += ImportItem(ImportKind.Card, EntryDraft(name, "", "", "", at(notes), null, CardData(cardNumber, at(holder), expiry(at(expiryCol)), at(cvv), "")))
                at(password).isNotEmpty() || at(user).isNotEmpty() || at(otp).isNotEmpty() -> {
                    val login = at(user).ifEmpty { at(email) }
                    out += ImportItem(ImportKind.Login, EntryDraft(name, login, at(password), address, at(notes), at(otp).ifEmpty { null }))
                }
                at(notes).isNotEmpty() && name.isNotEmpty() && (type.isEmpty() || "note" in type) -> out += ImportItem(ImportKind.Note, EntryDraft(name, "", "", "", at(notes), null))
                else -> skipped++
            }
        }
        return ImportOutcome.Ready(out, skipped, 0)
    }

    internal fun csvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val delimiter = if (text.lineSequence().first().count { it == ';' } > text.lineSequence().first().count { it == ',' }) ';' else ','
        fun endCell() { row.add(cell.toString()); cell.clear() }
        fun endRow() { endCell(); if (row.any { it.isNotBlank() }) rows.add(row); row = ArrayList() }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == delimiter -> endCell()
                c == '\n' -> endRow()
                c != '\r' -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun bitwarden(json: JSONObject): ImportOutcome {
        if (json.optBoolean("encrypted", false)) return ImportOutcome.Encrypted
        val items = json.getJSONArray("items")
        val out = ArrayList<ImportItem>()
        var skipped = 0
        var passkeys = 0
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.str("name")
            val extras = item.optJSONArray("fields")?.let { f ->
                (0 until f.length()).map { f.getJSONObject(it) }.joinToString("\n") { it.str("name") + ": " + it.str("value") }
            }.orEmpty()
            val notes = listOf(item.str("notes"), extras).filter { it.isNotBlank() }.joinToString("\n\n")
            when (item.optInt("type")) {
                1 -> {
                    val login = item.optJSONObject("login") ?: JSONObject()
                    val uris = login.optJSONArray("uris")
                    val first = uris?.optJSONObject(0)?.str("uri").orEmpty()
                    passkeys += login.optJSONArray("fido2Credentials")?.length() ?: 0
                    out += ImportItem(ImportKind.Login, EntryDraft(title, login.str("username"), login.str("password"), first, notes, login.str("totp").ifEmpty { null }))
                }
                2 -> out += ImportItem(ImportKind.Note, EntryDraft(title, "", "", "", notes, null))
                3 -> {
                    val card = item.optJSONObject("card") ?: JSONObject()
                    val date = card.str("expMonth").padStart(2, '0') + "/" + card.str("expYear").takeLast(2)
                    out += ImportItem(ImportKind.Card, EntryDraft(title, "", "", "", notes, null, CardData(card.str("number"), card.str("cardholderName"), date, card.str("code"), card.str("brand"))))
                }
                else -> skipped++
            }
        }
        return ImportOutcome.Ready(out, skipped, passkeys)
    }

    /** I QR di esportazione di Google Authenticator: un protobuf in base64 con tutti i codici dentro. */
    private fun googleMigration(uri: String): List<ImportItem> {
        val raw = uri.substringAfter("data=", "").substringBefore('&')
        val data = java.util.Base64.getDecoder().decode(decode(raw).replace('-', '+').replace('_', '/'))
        val out = ArrayList<ImportItem>()
        protoFields(data) { field, value ->
            if (field == 1 && value is ByteArray) {
                var secret = ByteArray(0); var name = ""; var issuer = ""; var algorithm = 1L; var digits = 1L; var type = 2L
                protoFields(value) { f, v ->
                    when {
                        f == 1 && v is ByteArray -> secret = v
                        f == 2 && v is ByteArray -> name = String(v)
                        f == 3 && v is ByteArray -> issuer = String(v)
                        f == 4 && v is Long -> algorithm = v
                        f == 5 && v is Long -> digits = v
                        f == 6 && v is Long -> type = v
                    }
                }
                if (type == 2L && secret.isNotEmpty()) {
                    val algo = when (algorithm) { 2L -> "SHA256"; 3L -> "SHA512"; else -> "SHA1" }
                    val account = name.substringAfter(':', name).trim()
                    val service = issuer.ifEmpty { name.substringBefore(':', "").trim() }.ifEmpty { account }
                    out += code(service, if (account == service) "" else account, uri(base32(secret), algo, if (digits == 2L) 8 else 6, 30))
                }
            }
        }
        return out
    }

    private fun protoFields(data: ByteArray, onField: (Int, Any) -> Unit) {
        var at = 0
        fun varint(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val b = data[at++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (at < data.size) {
            val tag = varint().toInt()
            when (tag and 7) {
                0 -> onField(tag ushr 3, varint())
                2 -> { val size = varint().toInt(); onField(tag ushr 3, data.copyOfRange(at, at + size)); at += size }
                1 -> at += 8
                5 -> at += 4
                else -> return
            }
        }
    }

    private fun base32(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { out.append(alphabet[(buffer shr (bits - 5)) and 31]); bits -= 5 }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
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
        val migrations = lines.filter { it.startsWith("otpauth-migration://", ignoreCase = true) }
        if (uris.isEmpty() && migrations.isEmpty()) return ImportOutcome.Unknown
        val items = uris.map { fromUri(it, "") } + migrations.flatMap { runCatching { googleMigration(it) }.getOrDefault(emptyList()) }
        return ImportOutcome.Ready(items, lines.size - uris.size - migrations.size, 0)
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
