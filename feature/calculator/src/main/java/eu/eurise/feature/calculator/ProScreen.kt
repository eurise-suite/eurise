package eu.eurise.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.calc.Calc
import eu.eurise.core.data.calc.Finance
import eu.eurise.core.data.calc.Invoice
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Tool(val label: Int) {
    Invoice(R.string.calc_tool_invoice), Scorporo(R.string.calc_tool_scorporo), Discount(R.string.calc_tool_discount),
    Loan(R.string.calc_tool_loan), Days(R.string.calc_tool_days),
}

private fun euro(v: BigDecimal) = Calc.money(v) + " €"

@Composable
internal fun ProScreen(tape: Tape) {
    var tool by rememberSaveable { mutableStateOf(Tool.Invoice) }
    ScrollColumn {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Tool.entries.toList()) { t -> FilterPill(stringResource(t.label), null, t == tool) { tool = t } }
        }
        when (tool) {
            Tool.Invoice -> InvoiceTool(tape)
            Tool.Scorporo -> ScorporoTool(tape)
            Tool.Discount -> DiscountTool()
            Tool.Loan -> LoanTool()
            Tool.Days -> DaysTool()
        }
        Text(stringResource(R.string.calc_pro_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fattura del professionista: dal compenso o, al contrario, dal netto che si vuole incassare. */
@Composable
private fun InvoiceTool(tape: Tape) {
    val context = LocalContext.current
    var amount by rememberSaveable { mutableStateOf("1.000") }
    var fromNet by rememberSaveable { mutableStateOf(false) }
    var cassa by rememberSaveable { mutableStateOf(tape.rate("cassa", "4")) }
    var iva by rememberSaveable { mutableStateOf(tape.rate("iva", "22")) }
    var ritenuta by rememberSaveable { mutableStateOf(tape.rate("ritenuta", "20")) }
    var flat by rememberSaveable { mutableStateOf(tape.rate("forfettario", "no") == "si") }
    val rates = Invoice.Rates(
        cassa = parseAmount(cassa) ?: BigDecimal.ZERO,
        iva = parseAmount(iva) ?: BigDecimal.ZERO,
        ritenuta = parseAmount(ritenuta) ?: BigDecimal.ZERO,
        forfettario = flat,
    )
    val value = parseAmount(amount)
    val r = value?.let { if (fromNet) Invoice.fromNetto(it, rates) else Invoice.fromCompenso(it, rates) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill(stringResource(R.string.calc_from_fee), null, !fromNet) { fromNet = false }
        FilterPill(stringResource(R.string.calc_from_net), null, fromNet) { fromNet = true }
        FilterPill(stringResource(R.string.calc_flat_rate), null, flat) { flat = !flat; tape.setRate("forfettario", if (flat) "si" else "no") }
    }
    NumberField(stringResource(if (fromNet) R.string.calc_net_wanted else R.string.calc_fee), amount, { amount = it }, suffix = "€")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(stringResource(R.string.calc_cassa), cassa, { cassa = it; tape.setRate("cassa", it) }, Modifier.weight(1f), "%")
        if (!flat) {
            NumberField(stringResource(R.string.calc_iva), iva, { iva = it; tape.setRate("iva", it) }, Modifier.weight(1f), "%")
            NumberField(stringResource(R.string.calc_withholding), ritenuta, { ritenuta = it; tape.setRate("ritenuta", it) }, Modifier.weight(1f), "%")
        }
    }
    if (r != null) {
        val lines = buildList {
            add(context.getString(R.string.calc_fee) to euro(r.compenso))
            if (r.cassa.signum() != 0) add(context.getString(R.string.calc_cassa_line, cassa) to euro(r.cassa))
            add(context.getString(R.string.calc_taxable) to euro(r.imponibile))
            if (!flat) add(context.getString(R.string.calc_iva_line, iva) to euro(r.iva))
            if (r.bollo.signum() != 0) add(context.getString(R.string.calc_stamp) to euro(r.bollo))
        }
        Card {
            lines.forEachIndexed { i, (l, v) -> if (i > 0) CardDivider(); ResultRow(l, v) }
            CardDivider()
            ResultRow(stringResource(R.string.calc_total), euro(r.totale), strong = true)
            if (!flat) { CardDivider(); ResultRow(stringResource(R.string.calc_withholding_line, ritenuta), "− " + euro(r.ritenuta)) }
            CardDivider()
            ResultRow(stringResource(R.string.calc_net), euro(r.netto), strong = true)
        }
        val summary = (lines + listOf(context.getString(R.string.calc_total) to euro(r.totale)) +
            (if (!flat) listOf(context.getString(R.string.calc_withholding_line, ritenuta) to "− " + euro(r.ritenuta)) else emptyList()) +
            listOf(context.getString(R.string.calc_net) to euro(r.netto))).joinToString("\n") { "${it.first}: ${it.second}" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(stringResource(R.string.calc_copy_summary), null, false) { context.copyText(summary) }
        }
    }
}

@Composable
private fun ScorporoTool(tape: Tape) {
    var total by rememberSaveable { mutableStateOf("1.220") }
    var iva by rememberSaveable { mutableStateOf(tape.rate("iva", "22")) }
    NumberField(stringResource(R.string.calc_total_with_vat), total, { total = it }, suffix = "€")
    NumberField(stringResource(R.string.calc_iva), iva, { iva = it }, suffix = "%")
    val t = parseAmount(total)
    val i = parseAmount(iva)
    if (t != null && i != null) {
        val (imp, vat) = Finance.scorporo(t, i)
        Card {
            ResultRow(stringResource(R.string.calc_taxable), euro(imp), strong = true)
            CardDivider()
            ResultRow(stringResource(R.string.calc_iva_line, iva), euro(vat))
        }
    }
}

@Composable
private fun DiscountTool() {
    var price by rememberSaveable { mutableStateOf("100") }
    var percent by rememberSaveable { mutableStateOf("20") }
    NumberField(stringResource(R.string.calc_price_or_cost), price, { price = it }, suffix = "€")
    NumberField(stringResource(R.string.calc_percent), percent, { percent = it }, suffix = "%")
    val p = parseAmount(price)
    val q = parseAmount(percent)
    if (p != null && q != null) {
        val (sale, margin) = Finance.ricarico(p, q)
        Card {
            ResultRow(stringResource(R.string.calc_discounted), euro(Finance.sconto(p, q)), strong = true)
            CardDivider()
            ResultRow(stringResource(R.string.calc_markup_price), euro(sale))
            CardDivider()
            ResultRow(stringResource(R.string.calc_margin), Calc.format(margin.toDouble(), 2) + " %")
        }
    }
}

@Composable
private fun LoanTool() {
    var capital by rememberSaveable { mutableStateOf("10.000") }
    var rate by rememberSaveable { mutableStateOf("5") }
    var months by rememberSaveable { mutableStateOf("36") }
    NumberField(stringResource(R.string.calc_capital), capital, { capital = it }, suffix = "€")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(stringResource(R.string.calc_rate_year), rate, { rate = it }, Modifier.weight(1f), "%")
        NumberField(stringResource(R.string.calc_months), months, { months = it }, Modifier.weight(1f))
    }
    val c = parseAmount(capital)
    val r = parseAmount(rate)
    val n = months.trim().toIntOrNull()?.takeIf { it in 1..600 }
    if (c != null && r != null && n != null) {
        val (installment, interest) = Finance.rata(c, r, n)
        Card {
            ResultRow(stringResource(R.string.calc_installment), euro(installment), strong = true)
            CardDivider()
            ResultRow(stringResource(R.string.calc_interest), euro(interest))
            CardDivider()
            ResultRow(stringResource(R.string.calc_total_paid), euro(c + interest))
        }
    }
}

@Composable
private fun DaysTool() {
    val format = remember { DateTimeFormatter.ofPattern("d/M/yyyy") }
    val today = remember { LocalDate.now() }
    var from by rememberSaveable { mutableStateOf(today.format(format)) }
    var to by rememberSaveable { mutableStateOf(today.plusDays(30).format(format)) }
    var add by rememberSaveable { mutableStateOf("90") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(stringResource(R.string.calc_from_date), from, { from = it }, Modifier.weight(1f))
        NumberField(stringResource(R.string.calc_to_date), to, { to = it }, Modifier.weight(1f))
    }
    val a = runCatching { LocalDate.parse(from.trim(), format) }.getOrNull()
    val b = runCatching { LocalDate.parse(to.trim(), format) }.getOrNull()
    if (a != null && b != null) {
        val (all, working) = Finance.giorni(a, b)
        Card {
            ResultRow(stringResource(R.string.calc_days), "$all", strong = true)
            CardDivider()
            ResultRow(stringResource(R.string.calc_working_days), "$working")
        }
    }
    NumberField(stringResource(R.string.calc_add_days), add, { add = it })
    val plus = add.trim().toLongOrNull()
    if (a != null && plus != null) {
        val end = a.plusDays(plus)
        Card { ResultRow(stringResource(R.string.calc_due_date, plus), end.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.getDefault())), strong = true) }
    }
}
