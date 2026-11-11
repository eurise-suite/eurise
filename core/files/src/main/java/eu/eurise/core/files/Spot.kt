package eu.eurise.core.files

/**
 * Una cartella di Eurise File: [account] nullo è il telefono (percorso dalla radice della memoria, per esempio
 * «Documents/Casa»), altrimenti un account cloud collegato (percorso WebDAV dalla sua radice).
 */
data class Spot(val account: String?, val path: String) {
    val isPhone get() = account == null
    val name get() = path.trimEnd('/').substringAfterLast('/')
    fun child(name: String) = Spot(account, join(path, name))
    val parent: Spot? get() = if (path.isEmpty()) null else Spot(account, path.substringBeforeLast('/', ""))

    /** Le cartelle del percorso, per la riga «kDrive › Documenti › Casa». */
    val crumbs: List<Spot> get() = path.split('/').filter { it.isNotEmpty() }.runningFold("") { acc, s -> join(acc, s) }.map { Spot(account, it) }

    fun key() = (account ?: PHONE) + ":" + path

    companion object {
        const val PHONE = "telefono"
        fun join(parent: String, name: String) = if (parent.isEmpty()) name else parent.trimEnd('/') + "/" + name.trim('/')
        fun fromKey(key: String): Spot? {
            val i = key.indexOf(':').takeIf { it > 0 } ?: return null
            val acc = key.substring(0, i)
            return Spot(acc.takeIf { it != PHONE }, key.substring(i + 1))
        }
    }
}

/** Un elemento in una cartella. */
data class Entry(val spot: Spot, val name: String, val folder: Boolean, val size: Long?, val modified: Long?) {
    val extension get() = name.substringAfterLast('.', "").lowercase()
}

/** Nomi dei file: niente caratteri che i cloud o Android rifiutano, e un numero se il nome c'è già. */
object Names {
    private val bad = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun clean(name: String, extension: String? = null): String {
        val base = name.trim().replace(bad, "-").trim('.', ' ').ifEmpty { "Documento" }.take(120)
        val ext = extension?.trimStart('.')?.lowercase()
        return if (ext == null || base.lowercase().endsWith(".$ext")) base else "$base.$ext"
    }

    /** «Contratto.pdf» → «Contratto (2).pdf» se il primo c'è già, e così via. */
    fun free(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val ext = name.substring(dot)
        var n = 2
        while ("$stem ($n)$ext" in taken) n++
        return "$stem ($n)$ext"
    }
}
