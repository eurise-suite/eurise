package eu.eurise.browser

import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyMeter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** Le sezioni delle Impostazioni (tavola BrwImpostazioni): ogni voce dice cosa cambia. */
enum class SettingsSection(val title: Int) {
    Search(R.string.brw_s_search), Privacy(R.string.brw_s_privacy), Passwords(R.string.brw_s_passwords),
    Tabs(R.string.brw_s_tabs), Look(R.string.brw_s_look), Gestures(R.string.brw_s_gestures), Sites(R.string.brw_s_sites),
    Extensions(R.string.brw_s_extensions),
    Downloads(R.string.brw_s_downloads), Import(R.string.brw_s_import), Sync(R.string.brw_s_sync), Clear(R.string.brw_s_clear);

    fun icon(): ImageVector = when (this) {
        Search -> EuriseIcons.globe(2.2f)
        Privacy -> EuriseIcons.shield(2.2f)
        Passwords -> EuriseIcons.key(2.2f)
        Tabs -> EuriseIcons.copy(2.2f)
        Look -> EuriseIcons.palette(2.2f)
        Gestures -> EuriseIcons.drag(2.2f)
        Sites -> EuriseIcons.lists(2.2f)
        Extensions -> EuriseIcons.puzzle(2.2f)
        Downloads -> EuriseIcons.download(2.2f)
        Import -> EuriseIcons.upload(2.2f)
        Sync -> EuriseIcons.monitor(2.2f)
        Clear -> EuriseIcons.trash(2.2f)
    }
}

/** La riga di riepilogo di una sezione, con i valori attuali. */
@Composable
private fun summary(section: SettingsSection, model: BrowserModel): String {
    val s = model.settings
    val on = stringResource(R.string.brw_on)
    val off = stringResource(R.string.brw_off)
    return when (section) {
        SettingsSection.Search -> model.engine.label + " · AI: " + (s.aiService?.label ?: off)
        SettingsSection.Privacy -> listOf(
            stringResource(if (s.strictProtection) R.string.brw_level_strict else R.string.brw_level_standard),
            stringResource(R.string.brw_cookies_isolated),
            if (s.httpsOnly) stringResource(R.string.brw_https_always) else null,
            s.dnsService?.let { stringResource(R.string.brw_dns_short, it.label) },
        ).filterNotNull().joinToString(" · ")
        SettingsSection.Passwords -> stringResource(R.string.brw_s_passwords_sub)
        SettingsSection.Tabs -> (if (s.restoreTabs) stringResource(R.string.brw_restore_short) else stringResource(R.string.brw_restore_off)) + " · " +
            (if (s.closeAfterDays > 0) stringResource(R.string.brw_close_after_short, s.closeAfterDays) else stringResource(R.string.brw_close_never))
        SettingsSection.Look -> stringResource(R.string.brw_s_look_sub, (s.textScale * 100).toInt()) + " · " +
            stringResource(R.string.brw_m_dark) + " " + (if (s.darkPages) on else off)
        SettingsSection.Gestures -> stringResource(R.string.brw_s_gestures_sub)
        SettingsSection.Sites -> stringResource(R.string.brw_s_sites_sub)
        SettingsSection.Downloads -> stringResource(if (s.askWhereToSave) R.string.brw_s_downloads_ask else R.string.brw_s_downloads_plain)
        SettingsSection.Import -> stringResource(R.string.brw_s_import_sub)
        SettingsSection.Extensions -> if (Extensions.installed.isEmpty()) stringResource(R.string.brw_s_extensions_sub)
            else Extensions.installed.joinToString(", ") { it.metaData.name ?: it.id }
        SettingsSection.Sync -> stringResource(R.string.brw_s_sync_sub)
        SettingsSection.Clear -> stringResource(R.string.brw_s_clear_sub)
    }
}

@Composable
internal fun SettingsScreen(
    model: BrowserModel,
    actions: BrowserActions,
    section: SettingsSection?,
    onSection: (SettingsSection?) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.brw_back)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { if (section != null) onSection(null) else onClose() }
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.chevronLeft(2.2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(6.dp))
            Text(
                if (section == null) stringResource(R.string.brw_settings_title) else stringResource(section.title),
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (section) {
                null -> SettingsList(model, onSection)
                SettingsSection.Search -> SearchPage(model)
                SettingsSection.Privacy -> PrivacyPage(model)
                SettingsSection.Passwords -> PasswordsPage(actions, onImport)
                SettingsSection.Tabs -> TabsPage(model)
                SettingsSection.Look -> LookPage(model)
                SettingsSection.Gestures -> GesturesPage(model)
                SettingsSection.Sites -> SitesPage(model)
                SettingsSection.Downloads -> DownloadsPage(model, actions)
                SettingsSection.Import -> ImportExportPage(onImport, actions)
                SettingsSection.Extensions -> ExtensionsPage(onOpenUrl)
                SettingsSection.Sync -> Note(stringResource(R.string.brw_sync_note))
                SettingsSection.Clear -> ClearPage(model, actions)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsList(model: BrowserModel, onSection: (SettingsSection) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.search(2.3f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(stringResource(R.string.brw_settings_search), fontSize = 14.sp, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val q = query.trim().lowercase()
    val rows = SettingsSection.entries.map { it to (stringResource(it.title) to summary(it, model)) }
        .filter { (_, t) -> q.isEmpty() || t.first.lowercase().contains(q) || t.second.lowercase().contains(q) }
    Card {
        rows.forEachIndexed { i, (section, t) ->
            if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { onSection(section) }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(scheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                    Icon(section.icon(), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.first, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(t.second, fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
                }
                Text("›", color = scheme.onSurfaceVariant, fontSize = 18.sp)
            }
        }
    }
}

// --- Pezzi comuni delle pagine ---

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp), content = content)
}

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, sub: String?, selected: Boolean, level: Int? = null, where: Jurisdiction? = null, boost: Int = 0, boostLabel: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.onPrimary)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
        if (level != null) PrivacyMeter(level, where, boost = boost, boostLabel = boostLabel)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
        Text("›", color = scheme.onSurfaceVariant, fontSize = 18.sp)
    }
}

// --- Le pagine ---

@Composable
private fun SearchPage(model: BrowserModel) {
    val net = rememberNetworkShield()
    Title(stringResource(R.string.brw_engine))
    Card {
        Links.ENGINES.forEach { e ->
            ChoiceRow(e.label, e.sub, e.id == model.engine.id, e.level(net.bonus), e.where, e.level(net.bonus) - e.level, net.label()) { model.chooseEngine(e) }
        }
    }
    Note(stringResource(R.string.brw_engine_note))
    Title(stringResource(R.string.brw_ai_title))
    Card {
        ChoiceRow(stringResource(R.string.brw_ai_none), null, model.settings.aiService == null) { model.settings.setAi("none") }
        BrowserSettings.AIS.forEach { a ->
            ChoiceRow(a.label, a.where, model.settings.ai == a.id, a.level(net.bonus), a.country, a.level(net.bonus) - a.level(), net.label()) { model.settings.setAi(a.id) }
        }
    }
    Note(stringResource(R.string.brw_ai_note))
}

@Composable
private fun PrivacyPage(model: BrowserModel) {
    val net = rememberNetworkShield()
    val s = model.settings
    var explainDns by remember { mutableStateOf<DnsService?>(null) }
    Title(stringResource(R.string.brw_protection))
    Card {
        ChoiceRow(stringResource(R.string.brw_level_strict), stringResource(R.string.brw_level_strict_sub), s.strictProtection, 9) {
            s.setStrict(true); model.applySettings()
        }
        ChoiceRow(stringResource(R.string.brw_level_standard), stringResource(R.string.brw_level_standard_sub), !s.strictProtection, 6) {
            s.setStrict(false); model.applySettings()
        }
    }
    Card {
        SwitchRow(stringResource(R.string.brw_https_title), stringResource(R.string.brw_https_sub), s.httpsOnly) { s.setHttpsOnly(it); model.applySettings() }
    }
    Title(stringResource(R.string.brw_dns_title))
    Card {
        ChoiceRow(stringResource(R.string.brw_dns_phone), stringResource(R.string.brw_dns_phone_sub), s.dnsService == null) { s.setDns("off"); model.applySettings() }
        BrowserSettings.DNS.forEach { d ->
            ChoiceRow(d.label, d.where, s.dns == d.id, d.level(net.bonus), d.country, d.level(net.bonus) - d.level(), net.label()) {
                if (s.dnsExplained) { s.setDns(d.id); model.applySettings() } else explainDns = d
            }
        }
    }
    explainDns?.let { d ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.brw_dns_explain, d.label, d.where), fontSize = 13.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.brw_dns_use, d.label), strong = true) { s.setDns(d.id); model.applySettings(); explainDns = null }
                Chip(stringResource(R.string.brw_cancel)) { explainDns = null }
            }
        }
    }
    Note(stringResource(R.string.brw_what_leaves))
}

@Composable
private fun PasswordsPage(actions: BrowserActions, onImport: () -> Unit) {
    Card {
        LinkRow(EuriseIcons.key(2f), stringResource(R.string.brw_pwd_open), stringResource(R.string.brw_pwd_open_sub)) { actions.openPassword() }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        LinkRow(EuriseIcons.text(2f), stringResource(R.string.brw_pwd_fill), stringResource(R.string.brw_pwd_fill_sub)) { actions.autofillSettings() }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        LinkRow(EuriseIcons.upload(2f), stringResource(R.string.brw_pwd_import), stringResource(R.string.brw_pwd_import_sub), onImport)
    }
    Note(stringResource(R.string.brw_pwd_note))
}

@Composable
private fun TabsPage(model: BrowserModel) {
    val s = model.settings
    Card { SwitchRow(stringResource(R.string.brw_restore_title), stringResource(R.string.brw_restore_sub), s.restoreTabs) { s.setRestoreTabs(it) } }
    Title(stringResource(R.string.brw_close_after_title))
    Card {
        listOf(0, 1, 7, 30).forEach { d ->
            ChoiceRow(
                if (d == 0) stringResource(R.string.brw_close_never) else stringResource(R.string.brw_close_after_days, d),
                null, s.closeAfterDays == d,
            ) { s.setCloseAfter(d) }
        }
    }
    // Barra delle schede sulla pagina (tavola BrwBarraSchede).
    Title(stringResource(R.string.brw_strip_title))
    Card {
        listOf(
            "auto" to R.string.brw_strip_auto, "off" to R.string.brw_strip_off,
            "bottom" to R.string.brw_strip_bottom, "top" to R.string.brw_strip_top,
        ).forEach { (id, label) ->
            ChoiceRow(stringResource(label), if (id == "auto") stringResource(R.string.brw_strip_auto_sub) else null, s.tabStrip == id) { s.setTabStrip(id) }
        }
    }
    Note(stringResource(R.string.brw_strip_note))
    Note(stringResource(R.string.brw_private_note))
}

@Composable
private fun LookPage(model: BrowserModel) {
    val s = model.settings
    val scheme = MaterialTheme.colorScheme
    Title(stringResource(R.string.brw_zoom_title))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrowserSettings.SCALES.forEach { v ->
            val on = kotlin.math.abs(s.textScale - v) < 0.01f
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) scheme.primary else scheme.surface)
                    .clickable(role = Role.RadioButton) { model.setTextScale(v) },
                contentAlignment = Alignment.Center,
            ) { Text("${(v * 100).toInt()}%", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurface) }
        }
    }
    Card { SwitchRow(stringResource(R.string.brw_m_dark), stringResource(R.string.brw_dark_sub), s.darkPages) { model.setDarkPages(it) } }
    Note(stringResource(R.string.brw_look_note))
}

@Composable
private fun GesturesPage(model: BrowserModel) {
    val s = model.settings
    Card { SwitchRow(stringResource(R.string.brw_swipe_title), stringResource(R.string.brw_swipe_sub), s.swipeTabs) { s.setSwipeTabs(it) } }
    Title(stringResource(R.string.brw_bar_action_title))
    Card {
        ChoiceRow(stringResource(R.string.brw_bar_action_none), null, MenuItem.byId(s.barAction) == null) { s.setBarAction("") }
        MenuItem.entries.forEach { m ->
            ChoiceRow(stringResource(m.label), null, s.barAction == m.id) { s.setBarAction(m.id) }
        }
    }
    Note(stringResource(R.string.brw_menu_hint))
}

@Composable
private fun SitesPage(model: BrowserModel) {
    var list by remember { mutableStateOf<List<ContentPermission>?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(version) { model.sitePermissions { list = it } }
    val names = mapOf(
        PermissionDelegate.PERMISSION_GEOLOCATION to stringResource(R.string.brw_perm_location),
        PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION to stringResource(R.string.brw_perm_notifications),
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE to stringResource(R.string.brw_perm_storage),
        PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE to stringResource(R.string.brw_perm_autoplay),
        PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE to stringResource(R.string.brw_perm_autoplay),
        PermissionDelegate.PERMISSION_TRACKING to stringResource(R.string.brw_perm_tracking),
        PermissionDelegate.PERMISSION_STORAGE_ACCESS to stringResource(R.string.brw_perm_storage_access),
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS to stringResource(R.string.brw_perm_drm),
    )
    val allowed = stringResource(R.string.brw_perm_allowed)
    val denied = stringResource(R.string.brw_perm_denied)
    val current = list
    when {
        current == null -> Note(stringResource(R.string.brw_loading))
        current.isEmpty() -> Note(stringResource(R.string.brw_sites_empty))
        else -> current.groupBy { Links.host(it.uri) }.forEach { (host, perms) ->
            Title(host)
            Card {
                perms.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    val name = names[p.permission] ?: stringResource(R.string.brw_perm_other)
                    val state = if (p.permission == PermissionDelegate.PERMISSION_TRACKING) {
                        if (p.value == ContentPermission.VALUE_ALLOW) stringResource(R.string.brw_perm_tracking_off) else allowed
                    } else if (p.value == ContentPermission.VALUE_ALLOW) allowed else denied
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(state, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Chip(stringResource(R.string.brw_perm_forget)) { model.forget(p); version++ }
                    }
                }
            }
        }
    }
    Note(stringResource(R.string.brw_sites_note))
}

@Composable
private fun DownloadsPage(model: BrowserModel, actions: BrowserActions) {
    val s = model.settings
    Card { SwitchRow(stringResource(R.string.brw_ask_save_title), stringResource(R.string.brw_ask_save_sub), s.askWhereToSave) { s.setAskWhereToSave(it) } }
    Card { LinkRow(EuriseIcons.download(2f), stringResource(R.string.brw_m_downloads), stringResource(R.string.brw_downloads_open_sub)) { actions.openDownloads() } }
}

@Composable
private fun ImportExportPage(onImport: () -> Unit, actions: BrowserActions) {
    Card {
        LinkRow(EuriseIcons.upload(2f), stringResource(R.string.brw_import_title), stringResource(R.string.brw_s_import_sub), onImport)
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        LinkRow(EuriseIcons.download(2f), stringResource(R.string.brw_export_bookmarks), stringResource(R.string.brw_export_bookmarks_sub)) { actions.exportBookmarks() }
    }
    Note(stringResource(R.string.brw_export_note))
}

@Composable
private fun ClearPage(model: BrowserModel, actions: BrowserActions) {
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf(true) }
    var permissions by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(1) }
    val done = stringResource(R.string.brw_cleared)
    Title(stringResource(R.string.brw_clear_what))
    Card {
        SwitchRow(stringResource(R.string.brw_history), null, history) { history = it }
        SwitchRow(stringResource(R.string.brw_clear_cookies), stringResource(R.string.brw_clear_cookies_sub), cookies) { cookies = it }
        SwitchRow(stringResource(R.string.brw_clear_cache), null, cache) { cache = it }
        SwitchRow(stringResource(R.string.brw_clear_permissions), null, permissions) { permissions = it }
    }
    Title(stringResource(R.string.brw_clear_period))
    Card {
        listOf(R.string.brw_period_hour, R.string.brw_period_day, R.string.brw_period_all).forEachIndexed { i, label ->
            ChoiceRow(stringResource(label), null, period == i) { period = i }
        }
    }
    Note(stringResource(R.string.brw_clear_note))
    Pill(stringResource(R.string.brw_clear_now), EuriseIcons.trash(2f), Modifier.fillMaxWidth(), strong = true) {
        val now = System.currentTimeMillis()
        val since = when (period) { 0 -> now - 3_600_000L; 1 -> now - 86_400_000L; else -> 0L }
        model.clearData(history, cookies, cache, permissions, since) { actions.toast(done) }
    }
}

/**
 * Estensioni (tavola BrwEstensioni): le stesse di Firefox, da addons.mozilla.org. Le attive con l'interruttore (e ciò che
 * leggono), le consigliate per lavorare con «Aggiungi»: prima di installare compare la scheda dei permessi.
 */
@Composable
private fun ExtensionsPage(onOpenUrl: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Extensions.refresh() }
    Text(stringResource(R.string.brw_ext_intro), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.search(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(stringResource(R.string.brw_ext_search), fontSize = 14.5.sp, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 14.5.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    if (query.isNotBlank()) onOpenUrl("https://addons.mozilla.org/android/search/?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"))
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (Extensions.failed) Text(stringResource(R.string.brw_ext_failed), fontSize = 12.5.sp, color = Low)
    if (Extensions.installed.isNotEmpty()) {
        Title(stringResource(R.string.brw_ext_active))
        Card {
            Extensions.installed.forEachIndexed { i, ext ->
                if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                val meta = ext.metaData
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExtDot(meta.name ?: ext.id)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meta.name ?: ext.id, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        meta.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        val reads = extensionReads(meta.requiredPermissions.toList(), meta.requiredOrigins.toList())
                        if (reads.isNotEmpty()) Text(reads.first(), fontSize = 11.sp, color = Half)
                        Text(stringResource(R.string.brw_ext_remove), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Low,
                            modifier = Modifier.padding(top = 2.dp).clickable(role = Role.Button) { Extensions.uninstall(ext) })
                    }
                    Switch(checked = meta.enabled, onCheckedChange = { on -> Extensions.setEnabled(ext, on) })
                }
            }
        }
    }
    Title(stringResource(R.string.brw_ext_suggested))
    Card {
        Extensions.SUGGESTED.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                ExtDot(s.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(s.about), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
                    Text(stringResource(s.reads), fontSize = 11.sp, color = Half)
                }
                Spacer(Modifier.width(8.dp))
                when {
                    Extensions.has(s.name) -> Text(stringResource(R.string.brw_ext_added), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Safe)
                    Extensions.busy == s.slug -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    else -> Box(
                        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(scheme.primary)
                            .clickable(role = Role.Button) { Extensions.install(s) }.padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.brw_ext_add), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary) }
                }
            }
        }
    }
    Note(stringResource(R.string.brw_ext_note))
}

@Composable
private fun ExtDot(name: String) {
    val (bg, fg) = siteTint(name)
    Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}
