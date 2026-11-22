package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.util.UUID

private val GREEN = Color(0xFF3F7A4C)
private val RED = Color(0xFFA4502E)
private val BAR = Color(0xFF8DA2C9)

/** «te» per sé, altrimenti il nome della persona. */
@Composable
private fun who(model: ChatModel, id: String): String =
    if (id == model.session?.userId) stringResource(R.string.ex_you) else model.nameOf(id)

// --- Scheda «Spese» del gruppo (tavola SpeGruppo) ---

@Composable
internal fun ExpensesPane(model: ChatModel, actions: ChatActions, roomId: String) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    LaunchedEffect(roomId) { model.loadExpenses(roomId) }
    val expenses = model.expenses(roomId)
    val settlements = model.settlements(roomId)
    // Chi partecipa senza la Chat (tavola SpeEmail): persone con la sola email, ricevono il riepilogo e rispondono «ho pagato».
    val mailPeople = model.expenseMailPeople(roomId)
    val members = model.members[roomId].orEmpty() + mailPeople
    val people = Expenses.people(expenses, settlements, members)
    var mailing by remember { mutableStateOf<String?>(null) }
    val failedMail = stringResource(R.string.af_mail_failed)
    val total = expenses.sumOf { it.cents }
    val mine = people.firstOrNull { it.id == me }
    // Una spesa arrivata dall'Email: «Nuova spesa» già compilata.
    var adding by remember { mutableStateOf(model.pendingExpense != null) }
    var settling by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (model.loadingHistory[roomId] == true) item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.primary).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ex_total), fontSize = 12.sp, color = scheme.onPrimary.copy(alpha = 0.75f))
                            Text(Expenses.money(total), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.ex_your_share), fontSize = 12.sp, color = scheme.onPrimary.copy(alpha = 0.75f))
                            Text(Expenses.money(mine?.share ?: 0), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val b = mine?.balance ?: 0
                    Text(
                        when {
                            b > 0 -> stringResource(R.string.ex_owed_you, Expenses.money(mine?.paid ?: 0), Expenses.money(b))
                            b < 0 -> stringResource(R.string.ex_you_owe, Expenses.money(mine?.paid ?: 0), Expenses.money(-b))
                            else -> stringResource(R.string.ex_even)
                        },
                        fontSize = 13.sp, color = scheme.onPrimary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (people.isNotEmpty()) item {
                Card {
                    Text(stringResource(R.string.ex_who_spent), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    val top = people.maxOf { it.paid }.coerceAtLeast(1)
                    people.forEachIndexed { i, p ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(who(model, p.id), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text((if (p.balance > 0) "+" else if (p.balance < 0) "−" else "") + Expenses.money(kotlin.math.abs(p.balance)),
                                        fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (p.balance > 0) GREEN else if (p.balance < 0) RED else scheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.08f))) {
                                    Box(Modifier.fillMaxWidth(p.paid.toFloat() / top).height(6.dp).clip(RoundedCornerShape(3.dp)).background(BAR))
                                }
                                Text(stringResource(R.string.ex_spent_share, Expenses.money(p.paid), Expenses.money(p.share)), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.se_mail_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (mailPeople.isNotEmpty()) Text(mailPeople.joinToString(", ") { eu.eurise.core.insieme.Scheda.mailAddress(it).orEmpty() }, fontSize = 12.sp,
                        color = scheme.onSurfaceVariant)
                    val m = mailing
                    if (m != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SmallField(m, stringResource(R.string.fa_mail_hint)) { mailing = it.trim().take(80) } }
                        Spacer(Modifier.width(6.dp))
                        Chip(stringResource(R.string.el_add), true) {
                            if (m.contains('@') && m.contains('.')) model.addExpenseMailPerson(roomId, m) { actions.toast(actions.text(R.string.ch_send_failed)) }
                            mailing = null
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("+ " + stringResource(R.string.se_mail_add), false) { mailing = "" }
                        if (mailPeople.isNotEmpty()) Chip(stringResource(R.string.se_mail_send), false) {
                            model.mailExpenseSummary(roomId) { actions.toast(failedMail) }
                            actions.toast(actions.text(R.string.se_mail_sent))
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.ex_latest), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            if (expenses.isEmpty() && model.loadingHistory[roomId] != true) item {
                Text(stringResource(R.string.ex_empty), fontSize = 13.5.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            items(expenses, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.euro(2.2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { stringResource(R.string.ex_untitled) }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.ex_paid_by_in, who(model, e.payer), e.owed.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Text(Expenses.money(e.cents), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
        // I due comandi in basso, il principale sotto il pollice (a destra).
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.ex_settle), EuriseIcons.check(2.2f), Modifier, strong = false) { settling = true }
            Pill(stringResource(R.string.ex_add), EuriseIcons.plus(2.4f), Modifier, strong = true) { adding = true }
        }
    }
    if (adding) AddExpenseSheet(model, actions, roomId, members.ifEmpty { listOf(me) }, start = model.pendingExpense, onClose = { adding = false; model.pendingExpense = null })
    if (settling) SettleSheet(model, actions, roomId, Expenses.settle(people), onClose = { settling = false })
}

// --- Nuova spesa (tavola SpeAggiungi) ---

@Composable
private fun AddExpenseSheet(model: ChatModel, actions: ChatActions, roomId: String, members: List<String>, start: Pair<Long, String>? = null, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    var amount by remember { mutableStateOf(start?.first?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()) }
    var title by remember { mutableStateOf(start?.second.orEmpty()) }
    var payer by remember { mutableStateOf(me) }
    var mode by remember { mutableStateOf(Expenses.Mode.Equal) }
    val included = remember { mutableStateMapOf<String, Boolean>().apply { members.forEach { put(it, true) } } }
    val values = remember { mutableStateMapOf<String, String>() }
    val cents = Expenses.parse(amount)
    val owed: Map<String, Long>? = cents?.let { c ->
        when (mode) {
            Expenses.Mode.Equal -> Expenses.equal(c, members.filter { included[it] == true }).takeIf { it.isNotEmpty() }
            Expenses.Mode.Shares -> Expenses.proportional(c, members.associateWith { values[it]?.replace(',', '.')?.toDoubleOrNull() ?: 1.0 }).takeIf { it.isNotEmpty() }
            Expenses.Mode.Percent -> members.associateWith { values[it]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0 }
                .takeIf { Expenses.percentOk(it) }?.let { Expenses.proportional(c, it) }
            Expenses.Mode.Amounts -> Expenses.amounts(c, members.associateWith { values[it]?.let(Expenses::parse) ?: 0L })
        }
    }
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.ex_body)
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ex_new), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Field(stringResource(R.string.ex_amount), amount, KeyboardType.Decimal) { amount = it }
            Field(stringResource(R.string.ex_what), title, KeyboardType.Text) { title = it }
            Text(stringResource(R.string.ex_paid_by), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                members.forEach { m -> Chip(who(model, m), selected = payer == m) { payer = m } }
            }
            Text(stringResource(R.string.ex_how), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Expenses.Mode.Equal to R.string.ex_equal, Expenses.Mode.Shares to R.string.ex_shares,
                    Expenses.Mode.Amounts to R.string.ex_amounts, Expenses.Mode.Percent to R.string.ex_percent).forEach { (m, label) ->
                    Chip(stringResource(label), selected = mode == m) { mode = m }
                }
            }
            Card {
                members.forEachIndexed { i, m ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mode == Expenses.Mode.Equal) {
                            val on = included[m] == true
                            Box(
                                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.1f))
                                    .clickable(role = Role.Checkbox) { included[m] = !on },
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(EuriseIcons.check(2.6f), null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp)) }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(who(model, m), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mode == Expenses.Mode.Equal) {
                            Text(owed?.get(m)?.let(Expenses::money) ?: "—", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            val hint = when (mode) { Expenses.Mode.Shares -> "1"; Expenses.Mode.Percent -> "%"; else -> "€" }
                            Box(Modifier.width(92.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.background).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                                if (values[m].isNullOrEmpty()) Text(hint, fontSize = 14.sp, color = scheme.onSurfaceVariant)
                                BasicTextField(value = values[m].orEmpty(), onValueChange = { values[m] = it }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            if (cents != null && owed == null) Text(stringResource(when (mode) {
                Expenses.Mode.Amounts -> R.string.ex_err_amounts
                Expenses.Mode.Percent -> R.string.ex_err_percent
                else -> R.string.ex_err_people
            }), fontSize = 12.5.sp, color = scheme.error)
            Pill(stringResource(R.string.ex_add_go), EuriseIcons.plus(2.4f), Modifier.fillMaxWidth(), strong = owed != null) {
                val c = cents ?: return@Pill
                val o = owed ?: return@Pill
                val e = Expenses.Expense(UUID.randomUUID().toString(), title.trim(), c, payer, o, mode)
                val payerName = if (payer == me) model.nameOf(me) else model.nameOf(payer)
                val body = String.format(bodyFmt, e.title.ifBlank { "—" }, Expenses.money(c), payerName, o.size)
                model.sendExpense(roomId, e, body) { actions.toast(failed) }
                onClose()
            }
        }
    }
}

// --- Chi deve a chi (tavola SpeSaldi) ---

@Composable
private fun SettleSheet(model: ChatModel, actions: ChatActions, roomId: String, transfers: List<Expenses.Transfer>, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val failed = stringResource(R.string.ch_send_failed)
    val paidFmt = stringResource(R.string.ex_paid_body)
    val remindFmt = stringResource(R.string.ex_remind_body)
    Sheet(onClose) {
        Text(stringResource(R.string.ex_settle_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        if (transfers.isEmpty()) {
            Text(stringResource(R.string.ex_settle_none), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            return@Sheet
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x334F6B5A)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.check(2.2f), null, tint = Color(0xFF4F6B5A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ex_settle_count, transfers.size), fontSize = 13.sp, lineHeight = 18.sp)
        }
        Card {
            transfers.forEachIndexed { i, t ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ex_arrow, who(model, t.from), who(model, t.to)), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Expenses.money(t.cents), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        // Ricordare ha senso a chi deve; «Pagato» lo segna chi paga o chi riceve.
                        if (t.from != me) Chip(stringResource(R.string.ex_remind), selected = false) {
                            model.send(roomId, String.format(remindFmt, model.nameOf(t.from), Expenses.money(t.cents), model.nameOf(t.to))) { actions.toast(failed) }
                            actions.toast(actions.text(R.string.ex_reminded))
                        }
                        if (t.from == me || t.to == me) Chip(stringResource(R.string.ex_mark_paid), selected = true) {
                            model.sendSettlement(roomId, Expenses.Settlement(t.from, t.to, t.cents),
                                String.format(paidFmt, model.nameOf(t.from), Expenses.money(t.cents), model.nameOf(t.to))) { actions.toast(failed) }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.ex_settle_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
}

// --- Nella conversazione: la spesa come scheda, il pagamento come riga al centro ---

@Composable
internal fun ExpenseBubble(model: ChatModel, e: Event, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val json = e.data?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
    if (e.kind == Event.Kind.Settlement) {
        val s = json?.let { Expenses.settlement(it, e.time) }
        Text(
            if (s != null) stringResource(R.string.ex_settled_line, who(model, s.from), Expenses.money(s.cents), who(model, s.to)) else e.text,
            fontSize = 12.sp, color = Color(0xFF4F6B5A), fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }
    val x = json?.let { Expenses.expense(it, e.time) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Row(
            Modifier.width(260.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.euro(2.2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (x == null) e.text else if (x.deleted) stringResource(R.string.ex_removed) else x.title.ifBlank { stringResource(R.string.ex_untitled) },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (x != null && !x.deleted) Text(stringResource(R.string.ex_paid_by_in, who(model, x.payer), x.owed.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            if (x != null && !x.deleted) Text(Expenses.money(x.cents), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
