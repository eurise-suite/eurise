package eu.eurise.password.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.service.autofill.Dataset
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import eu.eurise.password.R
import eu.eurise.password.kdbx.CardData
import eu.eurise.password.kdbx.VaultEntry

/** I campi riconosciuti in una schermata e chi la mostra. */
class Form(
    val ids: ArrayList<AutofillId>,
    val kinds: List<FieldKind>,
    val webDomain: String?,
    val packageName: String,
    private val values: List<String?> = emptyList(),
) {
    /** Il testo digitato nel primo campo di quel tipo: serve al salvataggio. */
    fun typed(vararg wanted: FieldKind): String? =
        kinds.indices.firstOrNull { kinds[it] in wanted && !values.getOrNull(it).isNullOrEmpty() }?.let { values[it] }

    fun idsOf(vararg wanted: FieldKind): Array<AutofillId> = ids.filterIndexed { i, _ -> kinds[i] in wanted }.toTypedArray()

    val hasLogin get() = kinds.any { it == FieldKind.Password || it == FieldKind.NewPassword || it == FieldKind.Username }
    val hasPassword get() = kinds.any { it == FieldKind.Password || it == FieldKind.NewPassword }
    val isSignUp get() = kinds.any { it == FieldKind.NewPassword }
    val hasCard get() = kinds.any { it == FieldKind.CardNumber }

    /** Solo il dominio mostrato dal browser o, per un'app, un collegamento fatto apposta: mai un'associazione indovinata. */
    fun matches(entry: VaultEntry): Boolean =
        entry.card == null && if (webDomain != null) Matching.domainMatches(entry.url, webDomain) else packageName in entry.apps

    val target: String get() = webDomain ?: packageName

    fun loginDataset(context: Context, username: String, password: String, label: String): Dataset? =
        build(presentation(context, label)) { kind ->
            when (kind) {
                FieldKind.Username -> username
                FieldKind.Password, FieldKind.NewPassword -> password
                else -> null
            }
        }

    fun cardDataset(context: Context, card: CardData, label: String): Dataset? {
        val digits = card.expiry.split('/', '-').map { it.trim() }
        return build(presentation(context, label)) { kind ->
            when (kind) {
                FieldKind.CardNumber -> card.number.filter { it.isDigit() }
                FieldKind.CardExpiry -> card.expiry
                FieldKind.CardMonth -> digits.getOrNull(0)
                FieldKind.CardYear -> digits.getOrNull(1)
                FieldKind.CardCvc -> card.cvv
                FieldKind.CardHolder -> card.holder
                else -> null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun build(view: RemoteViews, value: (FieldKind) -> String?): Dataset? {
        val builder = Dataset.Builder(view)
        var any = false
        ids.forEachIndexed { i, id ->
            val text = value(kinds[i])
            if (!text.isNullOrEmpty()) {
                builder.setValue(id, AutofillValue.forText(text))
                any = true
            }
        }
        return if (any) builder.build() else null
    }

    companion object {
        fun presentation(context: Context, label: String) = RemoteViews(context.packageName, R.layout.autofill_row).apply {
            setTextViewText(R.id.autofill_text, label)
        }

        /** Legge la schermata; null se non c'è nulla da compilare. */
        fun parse(structure: AssistStructure): Form? {
            val ids = ArrayList<AutofillId>()
            val found = ArrayList<FieldKind?>()
            val typed = ArrayList<String?>()
            var domain: String? = null
            fun visit(node: AssistStructure.ViewNode) {
                node.webDomain?.takeIf { it.isNotBlank() }?.let { if (domain == null) domain = it }
                val id = node.autofillId
                if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
                    val html = node.htmlInfo?.takeIf { it.tag.equals("input", ignoreCase = true) }
                        ?.attributes?.associate { it.first.lowercase() to it.second.orEmpty() }.orEmpty()
                    ids += id
                    typed += node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                    found += Matching.classify(node.autofillHints?.toList().orEmpty(), html, node.idEntry, node.inputType)
                }
                for (i in 0 until node.childCount) visit(node.getChildAt(i))
            }
            for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
            val kinds = Matching.fillUsername(found)
            val keep = kinds.indices.filter { kinds[it] != null }
            if (keep.isEmpty()) return null
            val pkg = structure.activityComponent.packageName
            return Form(ArrayList(keep.map { ids[it] }), keep.map { kinds[it]!! }, domain, pkg, keep.map { typed[it] })
        }
    }
}
