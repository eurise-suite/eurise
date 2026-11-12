package eu.eurise.feature.launcher.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.Category
import eu.eurise.feature.launcher.apps.inUserOrder

/**
 * I posti della dock (tavola ScrDock), da sinistra: Tutte le app, le tue app (stella), poi un'app di riferimento per
 * ognuno di questi compiti. Un tocco apre l'app scelta; tenendo premuto si vedono tutte le app di quel compito, con i
 * messaggi da leggere, e la stella sceglie quella della dock (tavola ScrDockTieni).
 */
enum class DockRole(val id: String, val label: Int, val icon: (Float) -> ImageVector) {
    Maps("maps", R.string.dock_maps, { EuriseIcons.map(it) }),
    Drive("drive", R.string.dock_drive, { EuriseIcons.folder(it) }),
    Sms("sms", R.string.dock_sms, { EuriseIcons.message(it) }),
    Email("email", R.string.dock_email, { EuriseIcons.mail(it) }),
    Chat("chat", R.string.dock_chat, { EuriseIcons.chat(it) }),
    Ai("ai", R.string.dock_ai, { EuriseIcons.spark(it) }),
    Browser("browser", R.string.dock_browser, { EuriseIcons.globe(it) }),
}

/** Le pillole sopra la dock: tutte le app, le tue app, le app di un compito, le app per comunicare. */
sealed interface DockPanel {
    data object All : DockPanel
    data object Star : DockPanel
    data class Role(val role: DockRole) : DockPanel
    data object Comms : DockPanel
}

/**
 * Le scelte della dock, sul telefono: l'app di riferimento di ogni compito e quante volte si è aperta ogni categoria
 * (al tocco su «Tutte le app» si apre per prima la più usata).
 */
class DockPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)
    private val refs = mutableStateMapOf<String, String>().apply {
        DockRole.entries.forEach { r -> prefs.getString("ref_" + r.id, null)?.let { put(r.id, it) } }
    }

    fun ref(role: DockRole): String? = refs[role.id]

    fun setRef(role: DockRole, key: String) {
        refs[role.id] = key
        prefs.edit().putString("ref_" + role.id, key).apply()
    }

    fun countOpen(category: String) {
        prefs.edit().putInt("open_$category", prefs.getInt("open_$category", 0) + 1).apply()
    }

    fun mostOpened(categories: List<BarCategory>): BarCategory? =
        categories.maxByOrNull { prefs.getInt("open_" + it.id, 0) }?.takeIf { prefs.getInt("open_" + it.id, 0) > 0 }
}

private val SUITE = mapOf(
    DockRole.Maps to "eu.eurise.maps", DockRole.Sms to "eu.eurise.messages", DockRole.Email to "eu.eurise.mail",
    DockRole.Chat to "eu.eurise.chat", DockRole.Ai to "eu.eurise.ai", DockRole.Browser to "eu.eurise.browser",
)

private val KNOWN = mapOf(
    DockRole.Maps to listOf("net.osmand", "app.organicmaps", "app.comaps", "com.google.android.apps.maps", "com.here.app.maps", "com.waze", "de.schildbach.oeffi"),
    DockRole.Drive to listOf("com.infomaniak.drive", "foundation.e.drive", "com.nextcloud", "me.proton.android.drive", "com.google.android.apps.docs", "com.dropbox",
        "org.fossify.filemanager", "me.zhanghai.android.files", "com.google.android.apps.nbu.files", "com.simplemobiletools.filemanager", "mega.privacy.android.app"),
    DockRole.Sms to listOf("com.google.android.apps.messaging", "org.fossify.messages", "com.simplemobiletools.smsmessenger", "com.android.messaging", "foundation.e.message"),
    DockRole.Email to listOf("net.thunderbird", "com.fsck.k9", "ch.protonmail.android", "com.infomaniak.mail", "de.tutao.tutanota", "com.google.android.gm",
        "com.microsoft.office.outlook", "foundation.e.mail", "eu.faircode.email"),
    DockRole.Chat to listOf("com.whatsapp", "org.telegram", "org.thunderdog.challegram", "org.thoughtcrime.securesms", "im.molly", "im.vector.app", "io.element",
        "chat.simplex", "ch.threema", "com.viber.voip", "com.facebook.orca", "com.discord", "chat.delta", "com.wire", "org.briarproject", "com.microsoft.teams",
        "com.Slack", "jp.naver.line"),
    DockRole.Ai to listOf("com.openai.chatgpt", "ai.perplexity", "com.anthropic.claude", "ai.mistral", "com.google.android.apps.bard", "com.microsoft.copilot"),
    DockRole.Browser to listOf("org.mozilla", "com.brave.browser", "com.vivaldi.browser", "org.chromium", "com.android.chrome", "com.duckduckgo", "com.opera", "com.microsoft.emmx"),
)

private fun handlers(context: Context, intent: Intent): Set<String> = runCatching {
    context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
}.getOrDefault(emptySet())

/** Le app del telefono che fanno questo compito, quella della suite per prima. */
fun roleApps(context: Context, role: DockRole, apps: List<AppEntry>): List<AppEntry> {
    val resolved = when (role) {
        DockRole.Maps -> handlers(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=roma")))
        DockRole.Sms -> handlers(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        DockRole.Email -> handlers(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        DockRole.Browser -> handlers(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://eurise.eu")).addCategory(Intent.CATEGORY_BROWSABLE))
        else -> emptySet()
    }
    val suite = SUITE[role]
    val known = KNOWN[role].orEmpty()
    val own = context.packageName
    val list = apps.filter { a ->
        val pkg = a.packageName
        when {
            role == DockRole.Drive && a.euriseSection == "drive" -> true
            role == DockRole.Ai && a.euriseSection == "alfred" -> true
            a.euriseSection != null || a.shortcut != null -> false
            pkg == own -> false
            suite != null && pkg.startsWith(suite) -> true
            pkg in resolved -> true
            known.any { pkg.startsWith(it) } -> true
            role == DockRole.Ai && a.category == Category.Ai -> true
            role == DockRole.Browser && a.category == Category.Browser -> true
            else -> false
        }
    }.distinctBy { it.key }
    return list.sortedWith(compareBy<AppEntry>({ !isSuite(it, role) }, { it.label.lowercase() }))
}

private fun isSuite(a: AppEntry, role: DockRole) =
    (role == DockRole.Drive && a.euriseSection == "drive") || (role == DockRole.Ai && a.euriseSection == "alfred") || SUITE[role]?.let { a.packageName.startsWith(it) } == true

/** L'app della dock per il compito: quella scelta con la stella, o la prima (la suite, se c'è). */
fun dockApp(prefs: DockPrefs, role: DockRole, candidates: List<AppEntry>): AppEntry? =
    candidates.firstOrNull { it.key == prefs.ref(role) } ?: candidates.firstOrNull()

/**
 * La dock del telefono (tavola ScrDock): una pillola di vetro con Tutte le app, le tue app e le app di riferimento, e
 * la lente della ricerca universale a parte, a destra.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneDock(
    roles: Map<DockRole, AppEntry?>,
    badges: Map<String, Int>,
    open: DockPanel?,
    hazeState: HazeState,
    onAll: () -> Unit,
    onStar: () -> Unit,
    onRole: (DockRole) -> Unit,
    onRoleHold: (DockRole) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).height(GlassDefaults.PillHeight).glass(hazeState, RoundedCornerShape(50), outline = false).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockSlot(EuriseIcons.grid(2.2f), stringResource(R.string.dock_all), 0, open == DockPanel.All, onAll, null)
            DockSlot(EuriseIcons.star(2.2f), stringResource(R.string.dock_star), 0, open == DockPanel.Star, onStar, null)
            DockRole.entries.forEach { r ->
                val app = roles[r]
                val label = app?.label ?: stringResource(r.label)
                DockSlot(r.icon(2.2f), label, app?.let { badges[it.packageName] } ?: 0, open == DockPanel.Role(r), { onRole(r) }, { onRoleHold(r) })
            }
        }
        val search = stringResource(R.string.search)
        Box(
            Modifier.size(GlassDefaults.PillHeight).glass(hazeState, CircleShape, outline = false)
                .clickable(role = Role.Button, onClickLabel = search, onClick = onSearch).semantics { contentDescription = search },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.search(2.4f), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.RowScope.DockSlot(
    icon: ImageVector, label: String, badge: Int, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp))
            .background(if (active) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = scheme.onSurface, modifier = Modifier.size(21.dp))
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 3.dp).heightIn(min = 16.dp).widthIn(min = 16.dp)
                .clip(RoundedCornerShape(8.dp)).background(scheme.primary).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (badge > 99) "99+" else "$badge", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary) }
    }
}

/** Una pillola di vetro sopra la dock; il fondo velato la chiude con un tocco. */
@Composable
fun DockSheet(hazeState: HazeState, bottom: Dp, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF141826).copy(alpha = 0.18f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = bottom).widthIn(max = 560.dp).fillMaxWidth()
                .glass(hazeState, RoundedCornerShape(28.dp), outline = false)
                .pointerInput(Unit) { detectTapGestures { } }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Tutte le app (tavola ScrDock, con la correzione del proprietario): le categorie stanno in colonna sul lato destro
 * (sinistro per i mancini); scorrendo il dito sulla colonna, a sinistra compaiono le app della categoria su cui si trova
 * il dito. Si apre sulla categoria aperta più spesso. «La tua» crea una categoria; «Apri» porta alla pagina intera.
 */
@Composable
fun AllAppsPanel(
    categories: List<BarCategory>,
    start: BarCategory?,
    appsOf: (BarCategory) -> List<AppEntry>,
    badges: Map<String, Int>,
    dark: Boolean,
    leftHanded: Boolean,
    hazeState: HazeState,
    bottom: Dp,
    onOpen: (AppEntry, BarCategory) -> Unit,
    onPage: (BarCategory) -> Unit,
    onAddCategory: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var current by remember { mutableIntStateOf(categories.indexOf(start).coerceAtLeast(0)) }
    val cat = categories.getOrNull(current) ?: return
    val height = (LocalConfiguration.current.screenHeightDp * 0.56f).dp.coerceIn(320.dp, 560.dp)
    DockSheet(hazeState, bottom, onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cat.label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.dock_open_page), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onPage(cat) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().height(height)) {
            val column: @Composable () -> Unit = {
                CategoryColumn(categories, current, onAddCategory, Modifier.width(78.dp).fillMaxHeight()) { current = it }
            }
            if (leftHanded) { column(); Spacer(Modifier.width(10.dp)) }
            val apps = remember(cat, appsOf) { appsOf(cat) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (apps.isEmpty()) Text(stringResource(R.string.dock_empty_category), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(apps, key = { it.key }) { app ->
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(app, cat) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, 48.dp, dark, badges[app.packageName] ?: 0)
                            Spacer(Modifier.height(5.dp))
                            Text(app.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (!leftHanded) { Spacer(Modifier.width(10.dp)); column() }
        }
        Text(stringResource(R.string.dock_all_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
    }
}

/** La colonna delle categorie: il dito che scorre sopra sceglie quella da mostrare, senza staccarsi. */
@Composable
private fun CategoryColumn(categories: List<BarCategory>, current: Int, onAdd: (() -> Unit)?, modifier: Modifier, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val count = categories.size + if (onAdd != null) 1 else 0
    BoxWithConstraints(modifier) {
        val item = (maxHeight / count).coerceAtMost(58.dp)
        val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { item.toPx() }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.onSurface.copy(alpha = 0.05f))
                .pointerInput(categories.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun pick(y: Float) { val i = (y / itemPx).toInt(); if (i in categories.indices) onPick(i) }
                        pick(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) { pick(change.position.y); change.consume() }
                        } while (change.pressed)
                    }
                },
        ) {
            categories.forEachIndexed { i, c ->
                val on = i == current
                Column(
                    Modifier.fillMaxWidth().height(item).clip(RoundedCornerShape(16.dp)).background(if (on) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .semantics { contentDescription = c.label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(c.iconVector(if (on) 2.6f else 2.1f), null, tint = if (on) scheme.primary else c.tint, modifier = Modifier.size(20.dp))
                    if (item >= 44.dp) Text(c.label, fontSize = 10.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) scheme.primary else scheme.onSurface)
                }
            }
            if (onAdd != null) {
                Column(
                    Modifier.fillMaxWidth().height(item).padding(4.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, scheme.onSurface.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(EuriseIcons.plus(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    if (item >= 44.dp) Text(stringResource(R.string.dock_your_category), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Le tue app (tavola ScrDockStella): le app con la stella, e «+» per aggiungerne. */
@Composable
fun StarPanel(favorites: List<AppEntry>, badges: Map<String, Int>, dark: Boolean, hazeState: HazeState, bottom: Dp, onOpen: (AppEntry) -> Unit, onAdd: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DockSheet(hazeState, bottom, onClose) {
        Text(stringResource(R.string.dock_star_title), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        AppGrid(favorites, badges, dark, onOpen, extra = {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onAdd).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, scheme.onSurface.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.plus(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.dock_add), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            }
        })
        Text(stringResource(R.string.dock_star_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
    }
}

/** Griglia di quattro app per riga, con i messaggi da leggere; [extra] in fondo (il «+»). */
@Composable
private fun AppGrid(apps: List<AppEntry>, badges: Map<String, Int>, dark: Boolean, onOpen: (AppEntry) -> Unit, extra: (@Composable () -> Unit)? = null) {
    val cells: List<AppEntry?> = apps + if (extra != null) listOf(null) else emptyList()
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (app == null) extra?.invoke() else Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(app) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, 48.dp, dark, badges[app.packageName] ?: 0)
                            Spacer(Modifier.height(5.dp))
                            Text(app.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Le app di un compito (tavola ScrDockTieni): ognuna con i messaggi da leggere; un tocco la apre, la stella la mette
 * nella dock. Le icone sono quelle del tema scelto.
 */
@Composable
fun RolePanel(role: DockRole, apps: List<AppEntry>, current: AppEntry?, badges: Map<String, Int>, dark: Boolean, hazeState: HazeState, bottom: Dp,
              onOpen: (AppEntry) -> Unit, onStar: (AppEntry) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = stringResource(role.label)
    DockSheet(hazeState, bottom, onClose) {
        Text(name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        if (apps.isEmpty()) Text(stringResource(R.string.dock_role_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            apps.forEachIndexed { i, app ->
                if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                val unread = badges[app.packageName] ?: 0
                val ref = app.key == current?.key
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(app) }, verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 38.dp, dark)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            if (ref) stringResource(R.string.dock_role_ref) else null,
                            if (unread > 0) stringResource(R.string.dock_unread, unread) else if (role in COMMS) stringResource(R.string.dock_nothing_new) else null,
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    if (unread > 0) Box(
                        Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(scheme.primary).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("$unread", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary) }
                    val starLabel = stringResource(if (ref) R.string.dock_role_ref else R.string.dock_make_ref)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onStar(app) }.semantics { contentDescription = starLabel },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.star(if (ref) 3.2f else 2.1f), null, tint = if (ref) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                }
            }
        }
        Text(stringResource(R.string.dock_role_hint, name), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
    }
}

private val COMMS = setOf(DockRole.Sms, DockRole.Email, DockRole.Chat)

/**
 * Le app per comunicare (tavola BolComunicaApp), dal menu in testa a «Ti hanno cercato»: telefono, SMS, chat e posta
 * trovati sul telefono, con le icone del tema e i messaggi da leggere.
 */
@Composable
fun CommsAppsPanel(apps: List<AppEntry>, badges: Map<String, Int>, dark: Boolean, hazeState: HazeState, bottom: Dp, onOpen: (AppEntry) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DockSheet(hazeState, bottom, onClose) {
        Text(stringResource(R.string.dock_comms_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        if (apps.isEmpty()) Text(stringResource(R.string.dock_role_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        AppGrid(apps, badges, dark, onOpen)
        Text(stringResource(R.string.dock_comms_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
    }
}

/** Telefono, SMS, chat e posta: le app per comunicare, senza doppioni, la suite per prima. */
fun commsApps(context: Context, apps: List<AppEntry>): List<AppEntry> {
    val dialer = runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
    val phone = apps.filter { it.euriseSection == null && (it.packageName == dialer || it.packageName.startsWith("eu.eurise.phone")) }
    return (phone + roleApps(context, DockRole.Chat, apps) + roleApps(context, DockRole.Sms, apps) + roleApps(context, DockRole.Email, apps))
        .distinctBy { it.key }
}

/** Le app di una categoria, come nella sua pagina: Preferiti, Tutte (in ordine alfabetico) o quelle che ci stanno. */
fun categoryApps(c: BarCategory, apps: List<AppEntry>, favorites: List<AppEntry>, categoryOf: (AppEntry) -> BarCategory?, order: List<String>?): List<AppEntry> = when {
    c.isFavorites -> favorites
    c.isAll -> apps
    else -> apps.filter { categoryOf(it)?.id == c.id }.inUserOrder(order)
}

