package eu.eurise.feature.notes.doc

/** Un pezzo del risultato di un'unione: blocchi su cui non c'è discussione, oppure due versioni da scegliere. */
sealed interface MergePart {
    data class Settled(val blocks: List<Block>) : MergePart
    data class Conflict(val local: List<Block>, val remote: List<Block>) : MergePart
}

/** Scelta per una parte in conflitto. */
enum class MergeChoice { Local, Remote, Both }

/**
 * Esito dell'unione a tre vie. [titleConflict] contiene i due titoli quando sono stati cambiati entrambi
 * in modo diverso; altrimenti [title] è già quello giusto.
 */
data class MergeResult(
    val title: String,
    val titleConflict: Pair<String, String>?,
    val parts: List<MergePart>,
) {
    val conflicts: List<MergePart.Conflict> get() = parts.filterIsInstance<MergePart.Conflict>()
    val clean: Boolean get() = titleConflict == null && conflicts.isEmpty()

    /** I blocchi finali con le scelte fatte (una per conflitto, nell'ordine); dove manca vale la versione del telefono. */
    fun blocks(choices: List<MergeChoice> = emptyList()): List<Block> {
        var k = 0
        return parts.flatMap { part ->
            when (part) {
                is MergePart.Settled -> part.blocks
                is MergePart.Conflict -> when (choices.getOrNull(k++) ?: MergeChoice.Local) {
                    MergeChoice.Local -> part.local
                    MergeChoice.Remote -> part.remote
                    MergeChoice.Both -> part.local + part.remote
                }
            }
        }
    }

    fun title(choice: MergeChoice?): String = titleConflict?.let { (l, r) ->
        when (choice ?: MergeChoice.Local) {
            MergeChoice.Local -> l
            MergeChoice.Remote -> r
            MergeChoice.Both -> "$l / $r"
        }
    } ?: title
}

/**
 * Unione a tre vie dei blocchi di una nota (come `diff3`): partendo dalla versione comune [base], i cambi fatti
 * solo sul telefono o solo sul cloud si prendono da soli; restano da scegliere solo i punti cambiati in modo
 * diverso da entrambe le parti. Due blocchi sono uguali se scrivono lo stesso HTML.
 */
object NoteMerge {

    fun merge(baseTitle: String, base: List<Block>, localTitle: String, local: List<Block>, remoteTitle: String, remote: List<Block>): MergeResult {
        val title = when {
            localTitle == remoteTitle -> localTitle
            localTitle == baseTitle -> remoteTitle
            remoteTitle == baseTitle -> localTitle
            else -> null
        }
        val b = base.map(::key)
        val l = local.map(::key)
        val r = remote.map(::key)
        val toLocal = lcs(b, l)
        val toRemote = lcs(b, r)

        val parts = mutableListOf<MergePart>()
        fun settle(blocks: List<Block>) {
            if (blocks.isEmpty()) return
            val last = parts.lastOrNull()
            if (last is MergePart.Settled) parts[parts.lastIndex] = MergePart.Settled(last.blocks + blocks)
            else parts += MergePart.Settled(blocks)
        }
        fun chunk(bi: IntRange, li: IntRange, ri: IntRange) {
            val bk = b.slice(bi)
            val lk = l.slice(li)
            val rk = r.slice(ri)
            when {
                lk == bk -> settle(remote.slice(ri))
                rk == bk -> settle(local.slice(li))
                lk == rk -> settle(local.slice(li))
                else -> parts += MergePart.Conflict(local.slice(li), remote.slice(ri))
            }
        }

        var i = 0
        var li = 0
        var ri = 0
        for (k in b.indices) {
            val lk = toLocal[k] ?: continue
            val rk = toRemote[k] ?: continue
            if (lk < li || rk < ri) continue
            chunk(i until k, li until lk, ri until rk)
            settle(listOf(local[lk]))
            i = k + 1
            li = lk + 1
            ri = rk + 1
        }
        chunk(i until b.size, li until l.size, ri until r.size)
        return MergeResult(title ?: localTitle, if (title == null) localTitle to remoteTitle else null, parts)
    }

    private fun key(block: Block): String = NoteHtml.write(listOf(block))

    /** Per ogni elemento di [a], la posizione corrispondente in [b] secondo la più lunga sottosequenza comune. */
    private fun lcs(a: List<String>, b: List<String>): Map<Int, Int> {
        val n = a.size
        val m = b.size
        val len = Array(n + 1) { IntArray(m + 1) }
        for (x in n - 1 downTo 0) for (y in m - 1 downTo 0) {
            len[x][y] = if (a[x] == b[y]) len[x + 1][y + 1] + 1 else maxOf(len[x + 1][y], len[x][y + 1])
        }
        val out = HashMap<Int, Int>()
        var x = 0
        var y = 0
        while (x < n && y < m) {
            when {
                a[x] == b[y] -> { out[x] = y; x++; y++ }
                len[x + 1][y] >= len[x][y + 1] -> x++
                else -> y++
            }
        }
        return out
    }
}
