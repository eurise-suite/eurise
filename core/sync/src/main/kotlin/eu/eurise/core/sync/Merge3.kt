package eu.eurise.core.sync

/** Un pezzo dell'unione: righe su cui tutti sono d'accordo, oppure le due versioni diverse da scegliere. */
sealed interface Merged<T> {
    data class Settled<T>(val items: List<T>) : Merged<T>
    data class Conflict<T>(val local: List<T>, val remote: List<T>) : Merged<T>
}

/**
 * Unione a tre vie (come `diff3`), la stessa regola delle Note sul telefono: partendo dalla versione comune, ciò che è
 * cambiato solo da una parte si prende da solo; resta da scegliere solo il punto cambiato in modo diverso da entrambe.
 * Generica: per le note le unità sono i blocchi (una riga HTML ciascuno), ma va bene per qualsiasi elenco.
 */
object Merge3 {

    fun <T> merge(base: List<T>, local: List<T>, remote: List<T>): List<Merged<T>> {
        val toLocal = lcs(base, local)
        val toRemote = lcs(base, remote)
        val parts = ArrayList<Merged<T>>()
        fun settle(items: List<T>) {
            if (items.isEmpty()) return
            val last = parts.lastOrNull()
            if (last is Merged.Settled) parts[parts.lastIndex] = Merged.Settled(last.items + items) else parts += Merged.Settled(items)
        }
        fun chunk(bi: IntRange, li: IntRange, ri: IntRange) {
            val b = base.slice(bi)
            val l = local.slice(li)
            val r = remote.slice(ri)
            when {
                l == b -> settle(r)
                r == b -> settle(l)
                l == r -> settle(l)
                else -> parts += Merged.Conflict(l, r)
            }
        }
        var i = 0
        var li = 0
        var ri = 0
        for (k in base.indices) {
            val lk = toLocal[k] ?: continue
            val rk = toRemote[k] ?: continue
            if (lk < li || rk < ri) continue
            chunk(i until k, li until lk, ri until rk)
            settle(listOf(local[lk]))
            i = k + 1
            li = lk + 1
            ri = rk + 1
        }
        chunk(i until base.size, li until local.size, ri until remote.size)
        return parts
    }

    /** Il risultato se non ci sono conflitti, altrimenti null. */
    fun <T> clean(parts: List<Merged<T>>): List<T>? =
        if (parts.any { it is Merged.Conflict }) null else parts.flatMap { p -> if (p is Merged.Settled) p.items else emptyList() }

    /** Per ogni elemento di [a], la posizione corrispondente in [b] secondo la più lunga sottosequenza comune. */
    private fun <T> lcs(a: List<T>, b: List<T>): Map<Int, Int> {
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
