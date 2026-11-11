package eu.eurise.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.mozilla.geckoview.GeckoView

private enum class Sheet { None, Menu, Tabs, Protection, Engine, Bookmarks }

private val Safe = Color(0xFF3F6E52)
private val Half = Color(0xFF8A6A1F)
private val Low = Color(0xFF8C2F39)

@Composable
fun BrowserScreen(model: BrowserModel, onImport: () -> Unit, onListen: (String) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tab = model.current
    var sheet by remember { mutableStateOf(Sheet.None) }
    var editing by remember { mutableStateOf(false) }
    val noteDone = stringResource(R.string.brw_note_done)
    val taskDone = stringResource(R.string.brw_task_done)
    val noHub = stringResource(R.string.brw_no_hub)

    BackHandler {
        when {
            sheet != Sheet.None -> sheet = Sheet.None
            editing -> editing = false
            tab?.fullscreen == true -> tab.session.exitFullScreen()
            tab?.canBack == true -> tab.session.goBack()
            else -> onFinish()
        }
    }

    Column(Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.ime)) {
        Box(Modifier.weight(1f).fillMaxWidth().then(if (tab?.fullscreen == true) Modifier else Modifier.statusBarsPadding())) {
            if (tab != null) GeckoHost(tab)
            if (tab == null || tab.url.isBlank() || tab.url == "about:blank") {
                NewTabPage(model, onImport = onImport, onOpen = { model.go(it) })
            }
            if (tab != null && tab.progress in 1..99) {
                LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
            }
        }
        if (tab?.fullscreen != true) {
            if (editing) AddressEditor(tab, model, onDone = { editing = false })
            else BottomBar(
                tab, model.tabs.size,
                onShield = { sheet = Sheet.Protection },
                onAddress = { editing = true },
                onTabs = { sheet = Sheet.Tabs },
                onMenu = { sheet = Sheet.Menu },
            )
        }
    }

    fun suite(task: Boolean) {
        val t = tab ?: return
        sheet = Sheet.None
        model.toSuite(t, task) { ok -> Toast.makeText(context, if (!ok) noHub else if (task) taskDone else noteDone, Toast.LENGTH_SHORT).show() }
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Menu -> BottomSheet(onClose = { sheet = Sheet.None }) {
            if (tab != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(EuriseIcons.chevronLeft(2f), stringResource(R.string.brw_back), tab.canBack) { tab.session.goBack(); sheet = Sheet.None }
                    RoundAction(EuriseIcons.chevronRight(2f), stringResource(R.string.brw_forward), tab.canForward) { tab.session.goForward(); sheet = Sheet.None }
                    RoundAction(EuriseIcons.refresh(2f), stringResource(R.string.brw_reload)) { tab.session.reload(); sheet = Sheet.None }
                    RoundAction(EuriseIcons.star(2f), stringResource(if (model.isPinned(tab.url)) R.string.brw_unpin else R.string.brw_pin), tab.url.startsWith("http")) {
                        model.togglePin(tab); sheet = Sheet.None
                    }
                }
                Spacer(Modifier.height(4.dp))
                val page = tab.url.startsWith("http")
                MenuRow(EuriseIcons.note(2f), stringResource(R.string.brw_to_notes), stringResource(R.string.brw_to_notes_sub), page) { suite(task = false) }
                MenuRow(EuriseIcons.task(2f), stringResource(R.string.brw_to_tasks), stringResource(R.string.brw_to_tasks_sub), page) { suite(task = true) }
                MenuRow(EuriseIcons.volume(2f), stringResource(R.string.brw_listen), stringResource(R.string.brw_listen_sub), page) {
                    sheet = Sheet.None
                    tab.page { info -> onListen(info?.selection?.ifBlank { null } ?: info?.text.orEmpty()) }
                }
                MenuRow(EuriseIcons.share(2f), stringResource(R.string.brw_share), stringResource(R.string.brw_share_sub), page) { sheet = Sheet.None; model.share(tab) }
            }
            HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.08f))
            MenuRow(EuriseIcons.star(2f), stringResource(R.string.brw_bookmarks), stringResource(R.string.brw_bookmarks_sub, model.bookmarks.size)) { sheet = Sheet.Bookmarks }
            MenuRow(EuriseIcons.mask(1.8f), stringResource(R.string.brw_new_private), stringResource(R.string.brw_new_private_sub)) { model.open(private = true); sheet = Sheet.None; editing = true }
            MenuRow(EuriseIcons.search(2f), stringResource(R.string.brw_engine), model.engine.label) { sheet = Sheet.Engine }
            MenuRow(EuriseIcons.download(2f), stringResource(R.string.brw_import), stringResource(R.string.brw_import_sub)) { sheet = Sheet.None; onImport() }
        }
        Sheet.Tabs -> BottomSheet(onClose = { sheet = Sheet.None }) {
            Text(stringResource(R.string.brw_tabs_title, model.tabs.size), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.tabs.forEach { t ->
                    val on = t === tab
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (on) scheme.primary.copy(alpha = 0.12f) else scheme.surface)
                            .clickable { model.show(t); sheet = Sheet.None }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (t.private) EuriseIcons.mask(1.8f) else EuriseIcons.globe(2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title.ifBlank { stringResource(R.string.brw_new_tab) }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (t.url.startsWith("http")) Links.host(t.url) else stringResource(R.string.brw_new_tab_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        val closeLabel = stringResource(R.string.brw_close_tab)
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { model.close(t) }.semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.brw_new_tab), EuriseIcons.plus(2f), Modifier.weight(1f), strong = true) { model.open(); sheet = Sheet.None; editing = true }
                Pill(stringResource(R.string.brw_new_private_short), EuriseIcons.mask(1.8f), Modifier.weight(1f)) { model.open(private = true); sheet = Sheet.None; editing = true }
            }
            if (model.tabs.any { it.private }) {
                Pill(stringResource(R.string.brw_close_private), EuriseIcons.trash(2f), Modifier.fillMaxWidth()) { model.closePrivate(); sheet = Sheet.None }
            }
        }
        Sheet.Protection -> BottomSheet(onClose = { sheet = Sheet.None }) {
            val t = tab
            val host = t?.url?.takeIf { it.startsWith("http") }?.let(Links::host) ?: stringResource(R.string.brw_new_tab)
            Text(host, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(if (t?.secure != false) R.string.brw_secure else R.string.brw_not_secure),
                fontSize = 13.sp, color = if (t?.secure != false) Safe else Low,
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.shield(2f), null, tint = if (t?.protectionOn != false) Safe else Half, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.brw_protection), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.brw_blocked_here, t?.blocked ?: 0), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = t?.protectionOn != false, enabled = t?.trackingPermission != null, onCheckedChange = { on -> if (t != null) model.setProtection(t, on) })
                }
                Text(stringResource(R.string.brw_unblock_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.brw_what_leaves), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        }
        Sheet.Engine -> BottomSheet(onClose = { sheet = Sheet.None }) {
            Text(stringResource(R.string.brw_engine), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Links.ENGINES.forEach { e ->
                val on = e.id == model.engine.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) scheme.primary.copy(alpha = 0.12f) else scheme.surface)
                        .clickable(role = Role.RadioButton) { model.setEngine(e); sheet = Sheet.None }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(e.sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Levels(e.level)
                }
            }
            Text(stringResource(R.string.brw_engine_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
        Sheet.Bookmarks -> BottomSheet(onClose = { sheet = Sheet.None }) {
            Text(stringResource(R.string.brw_bookmarks), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (model.bookmarks.isEmpty()) {
                Text(stringResource(R.string.brw_bookmarks_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Pill(stringResource(R.string.brw_import), EuriseIcons.download(2f), Modifier.fillMaxWidth(), strong = true) { sheet = Sheet.None; onImport() }
            }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                model.bookmarks.forEach { b ->
                    Row(Modifier.fillMaxWidth().clickable { model.go(b.url); sheet = Sheet.None }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (b.pinned) EuriseIcons.pin(2f) else EuriseIcons.star(2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(b.folder, Links.host(b.url)).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        val remove = stringResource(R.string.brw_remove)
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { model.removeBookmark(b) }.semantics { contentDescription = remove }, contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** La vista di Gecko: una sola, che passa da una sessione all'altra quando si cambia scheda. */
@Composable
private fun GeckoHost(tab: Tab) {
    AndroidView(
        factory = { ctx -> GeckoView(ctx) },
        update = { view ->
            if (view.session !== tab.session) {
                view.releaseSession()
                view.setSession(tab.session)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun NewTabPage(model: BrowserModel, onImport: () -> Unit, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(if (model.current?.private == true) R.string.brw_private_title else R.string.brw_new_tab), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (model.current?.private == true) {
            Text(stringResource(R.string.brw_private_note), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        }
        val pins = model.bookmarks.filter { it.pinned }
        if (pins.isNotEmpty()) {
            Text(stringResource(R.string.brw_pinned).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = scheme.onSurfaceVariant)
            pins.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onOpen(b.url) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(Links.host(b.url).take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = scheme.primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(b.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.brw_pinned_empty), fontSize = 13.5.sp, lineHeight = 19.sp)
                Pill(stringResource(R.string.brw_import), EuriseIcons.download(2f), Modifier.fillMaxWidth()) { onImport() }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.shield(2f), null, tint = Safe, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.brw_blocked_total, Gecko.blockedTotal), fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBar(tab: Tab?, count: Int, onShield: () -> Unit, onAddress: () -> Unit, onTabs: () -> Unit, onMenu: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shield = stringResource(R.string.brw_protection)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onShield).semantics { contentDescription = shield }, contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.shield(2f), null, tint = if (tab?.protectionOn != false) Safe else Half, modifier = Modifier.size(20.dp))
        }
        val url = tab?.url.orEmpty()
        val shown = when {
            url.isBlank() || url == "about:blank" -> stringResource(R.string.brw_address_hint)
            else -> Links.queryOf(url) ?: Links.host(url)
        }
        Row(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(scheme.surface).clickable(onClick = onAddress).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab?.private == true) { Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
            if (url.startsWith("https")) { Icon(EuriseIcons.lock(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp)) }
            Text(shown, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (url.isBlank()) scheme.onSurfaceVariant else scheme.onSurface)
        }
        val tabsLabel = stringResource(R.string.brw_tabs_title, count)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onTabs).semantics { contentDescription = tabsLabel }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(scheme.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(if (count > 99) "∞" else "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        val more = stringResource(R.string.brw_menu)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMenu).semantics { contentDescription = more }, contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.more(2f), null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddressEditor(tab: Tab?, model: BrowserModel, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val start = tab?.url?.takeIf { it.startsWith("http") }?.let { Links.queryOf(it) ?: it }.orEmpty()
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) Text(stringResource(R.string.brw_address_hint_engine, model.engine.label), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { if (value.text.isNotBlank()) model.go(value.text); onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        val cancel = stringResource(R.string.brw_cancel)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDone).semantics { contentDescription = cancel }, contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.close(2f), null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BottomSheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.2f)))
            content()
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
            Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(scheme.surface).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, modifier: Modifier, strong: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (strong) scheme.primary else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (strong) scheme.onPrimary else scheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (strong) scheme.onPrimary else scheme.onSurface)
    }
}

/** Tre tacche crescenti: il livello di privacy di un motore. */
@Composable
private fun Levels(level: Int) {
    val color = when (level) { 3 -> Safe; 2 -> Half; else -> Low }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 3) {
            Box(Modifier.width(5.dp).height((7 + 4 * i).dp).clip(RoundedCornerShape(2.dp)).background(if (i < level) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
        }
    }
}
