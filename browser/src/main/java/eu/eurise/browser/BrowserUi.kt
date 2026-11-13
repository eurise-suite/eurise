package eu.eurise.browser

import eu.eurise.core.designsystem.component.PrivacyMeter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

private enum class Sheet { None, Menu, Protection, Engine, Bookmarks, Pin, Address, History, Zoom, Translate }

internal val Safe = Color(0xFF3F6E52)
internal val Half = Color(0xFF8A6A1F)
internal val Low = Color(0xFF8C2F39)

@Composable
fun BrowserScreen(
    model: BrowserModel,
    actions: BrowserActions,
    installed: (List<String>) -> Boolean,
    onRunImport: (passwords: Boolean, bookmarks: Boolean, deleteFiles: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tab = model.current
    var sheet by remember { mutableStateOf(Sheet.None) }
    var editing by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var reader by remember { mutableStateOf<PageInfo?>(null) }
    /** La Lettura viene da «Leggi dopo» (testo salvato, senza la pagina). */
    var readerOffline by remember { mutableStateOf(false) }

    val noteDone = stringResource(R.string.brw_note_done)
    val taskDone = stringResource(R.string.brw_task_done)
    val noHub = stringResource(R.string.brw_no_hub)
    val pinned = stringResource(R.string.brw_pinned_done)
    val unpinned = stringResource(R.string.brw_unpinned_done)
    val translateErrors = mapOf(
        "language" to stringResource(R.string.brw_translate_no_language),
        "same" to stringResource(R.string.brw_translate_same),
        "engine" to stringResource(R.string.brw_translate_unavailable),
    )
    val translateFailed = stringResource(R.string.brw_translate_failed)
    val onImport = { importOpen = true }
    LaunchedEffect(model.importRequested) { if (model.importRequested) { model.importRequested = false; importOpen = true } }

    model.notice?.let { text ->
        LaunchedEffect(text) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            model.notice = null
        }
    }

    BackHandler {
        when {
            reader != null -> reader = null
            model.tabsOpen -> model.tabsOpen = false
            importOpen -> importOpen = false
            settingsOpen && section != null -> section = null
            settingsOpen -> settingsOpen = false
            sheet != Sheet.None -> sheet = Sheet.None
            finding -> { tab?.stopFind(); finding = false }
            editing -> editing = false
            tab?.fullscreen == true -> tab.session.exitFullScreen()
            tab?.canBack == true -> tab.session.goBack()
            else -> actions.finish()
        }
    }

    fun translate(t: Tab) {
        val to = java.util.Locale.getDefault().language.ifBlank { "it" }
        t.translate(to) { reason -> model.notice = translateErrors[reason] ?: translateFailed }
    }

    /** Le voci del menu (e quella fissata nella barra). */
    fun run(item: MenuItem) {
        val t = tab
        sheet = Sheet.None
        when (item) {
            MenuItem.Favorite -> t?.let { model.togglePin(it); model.notice = if (model.isPinned(it.url)) pinned else unpinned }
            MenuItem.Notes -> t?.let { model.toSuite(it, task = false) { ok -> model.notice = if (ok) noteDone else noHub } }
            MenuItem.Task -> t?.let { model.toSuite(it, task = true) { ok -> model.notice = if (ok) taskDone else noHub } }
            MenuItem.Reader -> t?.page { info -> readerOffline = false; reader = info ?: PageInfo(t.url, t.title, "", "", "") }
            MenuItem.Translate -> if (t != null) {
                if (model.settings.translateOk || t.translated) translate(t) else sheet = Sheet.Translate
            }
            MenuItem.Find -> if (t != null) { finding = true; editing = false }
            MenuItem.Capture -> t?.let(actions::capture)
            MenuItem.Zoom -> sheet = Sheet.Zoom
            MenuItem.Desktop -> t?.let { it.setDesktop(!it.desktop) }
            MenuItem.Dark -> model.setDarkPages(!model.settings.darkPages)
            MenuItem.Downloads -> actions.openDownloads()
            MenuItem.Bookmarks -> sheet = Sheet.Bookmarks
            MenuItem.Passwords -> actions.openPassword()
            MenuItem.Pdf -> t?.let(actions::savePdf)
            MenuItem.Home -> t?.let(actions::addToHome)
        }
    }

    // Barra delle schede sulla pagina (tavola BrwBarraSchede): in cima, sopra la barra in basso, o nessuna.
    val strip = if (tab?.fullscreen == true) StripPlace.None else stripPlace(model.settings.tabStrip)
    val openGroups = { model.current?.let(model::snapshot); model.tabsSearch = false; model.tabsOpen = true }
    Column(Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.ime)) {
        if (strip == StripPlace.Top) TabStrip(model, Modifier.statusBarsPadding(), onNewTab = { model.open(); model.focusNewTab = true }, onGroups = openGroups)
        Box(Modifier.weight(1f).fillMaxWidth().then(if (tab?.fullscreen == true || strip == StripPlace.Top) Modifier else Modifier.statusBarsPadding())) {
            if (tab != null) Panes(model, tab)
            if (tab == null || tab.url.isBlank() || tab.url == "about:blank") {
                NewTabPage(
                    model, onImport = onImport, onOpen = { model.go(it) }, onQr = actions::openQr, onPin = { sheet = Sheet.Pin },
                    onLater = { l -> readerOffline = true; reader = PageInfo(l.url, l.title, "", "", l.text) },
                    onTool = { tool ->
                        when (tool) {
                            NewTabTool.Downloads -> actions.openDownloads()
                            NewTabTool.Passwords -> actions.openPassword()
                            NewTabTool.Extensions -> { section = SettingsSection.Extensions; settingsOpen = true }
                            NewTabTool.History -> sheet = Sheet.History
                        }
                    },
                )
            }
            if (tab != null && tab.progress in 1..99) {
                LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
            }
        }
        if (tab != null && !editing && tab.fullscreen != true) LoginBubbles(tab, onUnlock = actions::unlockPassword)
        if (strip == StripPlace.Bottom && !editing && !finding) TabStrip(model, Modifier, onNewTab = { model.open(); model.focusNewTab = true }, onGroups = openGroups)
        if (tab?.fullscreen != true) {
            if (finding && tab != null) FindBar(tab, onClose = { finding = false })
            else if (editing) AddressEditor(tab, model, onDone = { editing = false })
            else BottomBar(
                tab, model.tabs.size,
                barAction = MenuItem.byId(model.settings.barAction)?.takeIf { !it.needsPage || tab?.url?.startsWith("http") == true },
                onBarAction = ::run,
                onSwipe = { step: Int -> model.neighbour(step); Unit }.takeIf { model.settings.swipeTabs },
                onShield = { sheet = Sheet.Protection },
                onAddress = { if (tab?.url?.startsWith("http") == true) sheet = Sheet.Address else editing = true },
                onEditAddress = { editing = true },
                onBookmarks = { sheet = Sheet.Bookmarks },
                onTabs = { model.current?.let(model::snapshot); model.tabsSearch = false; model.tabsOpen = true },
                onMenu = { sheet = Sheet.Menu },
            )
        }
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Menu -> MenuSheet(
            model, tab, model.downloads,
            onClose = { sheet = Sheet.None },
            onNewTab = { model.open(); sheet = Sheet.None; model.focusNewTab = true },
            onPrivateTab = { model.open(private = true); sheet = Sheet.None; model.focusNewTab = true },
            onHistory = { sheet = Sheet.History },
            onItem = ::run,
            onProtection = { sheet = Sheet.Protection },
            onSettings = { sheet = Sheet.None; section = null; settingsOpen = true },
        )
        Sheet.History -> HistorySheet(model, onOpen = { url -> sheet = Sheet.None; model.go(url) }, onClose = { sheet = Sheet.None })
        Sheet.Zoom -> ZoomSheet(model, onClose = { sheet = Sheet.None })
        Sheet.Translate -> TranslateExplain(
            onAccept = { model.settings.acceptTranslate(); sheet = Sheet.None; tab?.let { translate(it) } },
            onClose = { sheet = Sheet.None },
        )
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
            val net = rememberNetworkShield()
            Links.ENGINES.forEach { e ->
                val on = e.id == model.engine.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) scheme.primary.copy(alpha = 0.12f) else scheme.surface)
                        .clickable(role = Role.RadioButton) { model.chooseEngine(e); sheet = Sheet.None }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(e.sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    PrivacyMeter(e.level(net.bonus), e.where, boost = e.level(net.bonus) - e.level, boostLabel = net.label())
                }
            }
            Text(stringResource(R.string.brw_engine_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
        Sheet.Bookmarks, Sheet.Pin -> BookmarksPopup(
            model, tab, pinMode = sheet == Sheet.Pin, onImport = onImport,
            onLater = { l -> readerOffline = true; reader = PageInfo(l.url, l.title, "", "", l.text) },
            onClose = { sheet = Sheet.None },
        )
        Sheet.Address -> if (tab != null) AddressMenu(model, tab, actions, onEdit = { sheet = Sheet.None; editing = true }, onClose = { sheet = Sheet.None })
    }

    if (settingsOpen) SettingsScreen(
        model, actions, section,
        onSection = { section = it },
        onImport = { importOpen = true },
        onClose = { settingsOpen = false },
        onOpenUrl = { url -> settingsOpen = false; model.open(url) },
    )
    if (importOpen) ImportScreen(model, actions, installed, onRun = { p, b, d -> onRunImport(p, b, d); importOpen = false }, onClose = { importOpen = false })
    if (model.tabsOpen) TabsScreen(
        model, startSearch = model.tabsSearch,
        onOpenTab = { model.tabsOpen = false },
        onNewTab = { private -> model.open(private = private); model.tabsOpen = false; model.focusNewTab = true },
        onClose = { model.tabsOpen = false },
    )
    reader?.let { info ->
        ReaderScreen(
            model, tab = if (readerOffline) null else tab, start = info, offline = readerOffline, actions = actions,
            onTranslate = { t -> if (model.settings.translateOk || t.translated) translate(t) else { reader = null; sheet = Sheet.Translate } },
            onClose = { reader = null },
        )
    }
    Extensions.pending?.let { ExtensionPrompt(it) }
}

/**
 * La vista di Gecko per [tab]. Passa da una sessione all'altra quando si cambia scheda; una sessione sta in una vista
 * sola, quindi prima la si toglie dall'altra (schede affiancate). [focused]: la vista della scheda che usi (cattura,
 * stampa).
 */
@Composable
private fun GeckoHost(tab: Tab, modifier: Modifier, focused: Boolean) {
    AndroidView(
        factory = { ctx -> GeckoView(ctx).also { Gecko.views += it } },
        update = { view ->
            if (view.session !== tab.session) {
                Gecko.views.forEach { other -> if (other !== view && other.session === tab.session) other.releaseSession() }
                view.releaseSession()
                view.setSession(tab.session)
            }
            if (focused) Gecko.view = view
        },
        onRelease = { view -> view.releaseSession(); Gecko.views -= view },
        modifier = modifier,
    )
}

/**
 * La pagina, o due schede affiancate sul tablet (tavola BrwSessioni). Il primo riquadro resta sempre lo stesso, così la
 * vista non si rifà quando si affianca o si torna a una sola. Un tocco su un riquadro lo rende quello in uso (riga
 * colorata in cima); la ✕ sul riquadro di destra torna a una scheda sola.
 */
@Composable
private fun Panes(model: BrowserModel, tab: Tab) {
    val scheme = MaterialTheme.colorScheme
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val pair = model.split?.takeIf { wide && (tab === it.first || tab === it.second) }
    val shown = if (pair != null) listOf(pair.first, pair.second) else listOf(tab)
    Row(Modifier.fillMaxSize()) {
        shown.forEachIndexed { i, t ->
            val focused = model.current === t
            if (i == 1) Box(Modifier.fillMaxHeight().width(1.dp).background(scheme.onSurface.copy(alpha = 0.12f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().pointerInput(t, pair != null) {
                    if (pair == null) return@pointerInput
                    // Si guarda il tocco prima della pagina, senza toglierlo: la pagina lo riceve comunque.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (model.current !== t) model.show(t)
                    }
                },
            ) {
                GeckoHost(t, Modifier.fillMaxSize(), focused = focused)
                if (pair != null) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (focused) scheme.primary else Color.Transparent).align(Alignment.TopCenter))
                }
                if (i == 1) {
                    val close = stringResource(R.string.brw_split_close)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape)
                            .background(scheme.surface.copy(alpha = 0.92f)).clickable(role = Role.Button) { model.unsplit() }
                            .semantics { contentDescription = close },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.close(2.2f), null, tint = scheme.onSurface, modifier = Modifier.size(15.dp)) }
                }
            }
        }
    }
}

/**
 * La barra del browser (tavole BrwIndirizzo, BrwSegnalibri): una pillola galleggiante con protezione, indirizzo (tocco: il
 * menu dell'indirizzo; tieni premuto: scrivi subito un altro indirizzo), segnalibri, schede e menu.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(
    tab: Tab?,
    count: Int,
    barAction: MenuItem?,
    onBarAction: (MenuItem) -> Unit,
    onSwipe: ((Int) -> Unit)?,
    onShield: () -> Unit,
    onAddress: () -> Unit,
    onEditAddress: () -> Unit,
    onBookmarks: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp).height(60.dp)
            .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(RoundedCornerShape(30.dp)).background(scheme.surface.copy(alpha = 0.94f)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shield = stringResource(R.string.brw_protection)
        BarButton(shield, onShield) { Icon(EuriseIcons.shield(2.2f), null, tint = if (tab?.protectionOn != false) Safe else Half, modifier = Modifier.size(20.dp)) }
        val url = tab?.url.orEmpty()
        val blank = url.isBlank() || url == "about:blank"
        val shown = if (blank) stringResource(R.string.brw_address_hint) else Links.queryOf(url) ?: Links.host(url)
        // Scorrere a destra o a sinistra sull'indirizzo passa alla scheda accanto (Impostazioni › Gesti).
        val swipe = if (onSwipe == null) Modifier else Modifier.pointerInput(onSwipe) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (kotlin.math.abs(total) > 80.dp.toPx()) onSwipe(if (total < 0) 1 else -1) },
                onHorizontalDrag = { change, amount -> change.consume(); total += amount },
            )
        }
        val addressLabel = stringResource(R.string.brw_addr_label)
        Row(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).then(swipe)
                .combinedClickable(onClick = onAddress, onLongClick = onEditAddress).semantics { contentDescription = addressLabel }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab?.private == true) { Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
            if (url.startsWith("https")) { Icon(EuriseIcons.lock(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp)) }
            Text(shown, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (blank) scheme.onSurfaceVariant else scheme.onSurface)
        }
        if (barAction != null && barAction != MenuItem.Bookmarks) {
            val label = stringResource(barAction.label)
            BarButton(label, { onBarAction(barAction) }) { Icon(barAction.icon(), null, tint = scheme.primary, modifier = Modifier.size(20.dp)) }
        }
        val bookmarks = stringResource(R.string.brw_bookmarks)
        BarButton(bookmarks, onBookmarks) { Icon(EuriseIcons.bookmark(2.2f), null, tint = scheme.primary, modifier = Modifier.size(20.dp)) }
        val tabsLabel = stringResource(R.string.brw_tabs_title, count)
        BarButton(tabsLabel, onTabs) {
            Box(Modifier.size(22.dp).border(2.dp, scheme.onSurface, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Text(if (count > 99) "∞" else "$count", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        val more = stringResource(R.string.brw_menu)
        BarButton(more, onMenu) { Icon(EuriseIcons.more(2.2f), null, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(width = 42.dp, height = 44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
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
internal fun BottomSheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
internal fun MenuRow(icon: ImageVector, title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
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
internal fun RoundAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
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
internal fun Pill(label: String, icon: ImageVector, modifier: Modifier, strong: Boolean = false, onClick: () -> Unit) {
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

