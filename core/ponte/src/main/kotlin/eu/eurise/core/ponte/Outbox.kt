package eu.eurise.core.ponte

import java.io.File
import java.util.UUID

/**
 * La coda dei messaggi per un telefono che in quel momento non è collegato (il computer manda, il telefono è fuori casa
 * o spento). Un file per messaggio, nella cartella privata dell'utente; parte appena il telefono si ricollega.
 *
 * Ogni tipo ha una scadenza: degli appunti copiati un'ora fa non servono più, una nota sì. Il messaggio esce con
 * l'ora della consegna (il telefono scarta quelli troppo vecchi) ma con il suo id di sempre: se arrivasse due volte,
 * il telefono lo riconosce e lo scarta ([ReplayGuard]).
 */
class Outbox(private val dir: File) {

    /** Un messaggio in coda e il suo file, da togliere con [done] quando il telefono l'ha ricevuto. */
    class Item(val message: Message, internal val file: File)

    fun put(to: String, kind: Kind, fields: Map<String, String>, now: Long = System.currentTimeMillis()): Message {
        val m = Message(kind, UUID.randomUUID().toString(), now, fields)
        val folder = File(dir, Wire.safe(to)).apply { mkdirs() }
        val file = File(folder, "%013d".format(now) + "-" + Wire.safe(m.id) + ".msg")
        val tmp = File(folder, file.name + ".tmp")
        tmp.writeText(m.encode())
        private(tmp)
        if (!tmp.renameTo(file)) { tmp.delete(); error("coda non scrivibile") }
        return m
    }

    /** I messaggi ancora validi per [to], dal più vecchio, pronti a partire con l'ora [now]. Gli scaduti si cancellano. */
    fun pending(to: String, now: Long = System.currentTimeMillis()): List<Item> {
        val folder = File(dir, Wire.safe(to))
        val files = folder.listFiles { f -> f.isFile && f.name.endsWith(".msg") }?.sortedBy { it.name }.orEmpty()
        return files.mapNotNull { f ->
            val m = runCatching { Message.decode(f.readText()) }.getOrNull()
            if (m == null || now - m.time > maxAge(m.kind)) { f.delete(); null }
            else Item(m.copy(time = now), f)
        }
    }

    fun done(item: Item) { item.file.delete() }

    fun size(to: String) = File(dir, Wire.safe(to)).listFiles { f -> f.name.endsWith(".msg") }?.size ?: 0

    private fun private(f: File) {
        f.setReadable(false, false); f.setReadable(true, true); f.setWritable(false, false); f.setWritable(true, true)
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE

        /** Quanto resta valido in coda ciascun tipo. */
        fun maxAge(kind: Kind): Long = when (kind) {
            Kind.Clipboard -> 15 * MINUTE
            Kind.Dictation, Kind.PasswordAsk, Kind.PasswordGive, Kind.Ping, Kind.Hello -> 2 * MINUTE
            Kind.Notify, Kind.Reply -> 30 * MINUTE
            Kind.Handoff -> DAY
            Kind.Note, Kind.Task, Kind.File -> 7 * DAY
        }
    }
}
