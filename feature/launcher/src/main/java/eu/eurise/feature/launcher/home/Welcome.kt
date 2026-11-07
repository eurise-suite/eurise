package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.colors
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.contacts.ContactAction
import eu.eurise.feature.launcher.contacts.Person
import eu.eurise.feature.launcher.files.RecentFile
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NowPlaying
import eu.eurise.feature.launcher.route.Travel
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Il saluto cambia con l'ora: buongiorno, buon pomeriggio, buonasera, buonanotte. */
@Composable
fun greeting(now: LocalDateTime, name: String): String {
    val base = stringResource(
        when (now.hour) {
            in 5..12 -> R.string.greet_morning
            in 13..17 -> R.string.greet_afternoon
            in 18..22 -> R.string.greet_evening
            else -> R.string.greet_night
        },
    )
    return if (name.isBlank()) "$base!" else "$base, ${name.trim()}!"
}

/**
 * Pillola di vetro della home: stessa forma per Oggi, Comunicazioni, Lavori e Musica. La freccia in alto
 * a destra la riduce al solo titolo, per quando serve riservatezza; resta ridotta finché non la si riapre.
 */
@Composable
fun Pill(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var collapsed by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        modifier
            .glass(hazeState, RoundedCornerShape(22.dp), elevation = 8.dp, outline = false)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = if (collapsed) 9.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Label(title) }
            if (!collapsed) trailing?.invoke()
            val label = stringResource(if (collapsed) R.string.pill_expand else R.string.pill_collapse)
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label) { collapsed = !collapsed },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    EuriseIcons.chevronRight(2.2f), label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).rotate(if (collapsed) 90f else -90f),
                )
            }
        }
        if (!collapsed) Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun PillLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(text) }
        trailing?.invoke()
    }
}

/** Una riga toccabile con simbolo, testo principale e riga secondaria. */
@Composable
private fun PillLine(icon: ImageVector, main: String, sub: String?, onClick: () -> Unit, tint: Color? = null, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(main, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) {
                Text(sub, fontSize = 11.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (right != null) Text(right, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Prima pillola: appuntamenti, cose da fare e luoghi dove andare. Ogni sezione mostra il primo elemento e
 * si scorre di lato per vedere fino a otto successivi; toccando si apre l'app relativa. Se mancano le cose
 * da fare o i luoghi, gli appuntamenti si mostrano a due a due.
 */
@Composable
fun AgendaPill(
    now: LocalDateTime,
    events: List<CalendarEvent>,
    calendarConnected: Boolean,
    tasks: List<Task>,
    travel: Travel?,
    destinations: List<CalendarEvent>,
    hazeState: HazeState,
    compact: Boolean,
    onCalendar: () -> Unit,
    onConnectCalendar: () -> Unit,
    onTasks: () -> Unit,
    onRoute: (String) -> Unit,
) {
    val upcoming = events.filter { it.allDay || it.end.isAfter(now) }
    val next = upcoming.firstOrNull { !it.allDay } ?: upcoming.firstOrNull()
    val showRoute = destinations.isNotEmpty() && !compact
    val showTasks = tasks.isNotEmpty()
    val perPage = if (!compact && (!showTasks || !showRoute)) 2 else 1
    Pill(hazeState, Modifier.fillMaxWidth(), title = stringResource(R.string.welcome_events), trailing = {
        if (next != null && !next.allDay && next.start.isAfter(now)) {
            val minutes = Duration.between(now, next.start).toMinutes()
            val text = if (minutes < 60) stringResource(R.string.welcome_in_min, minutes) else stringResource(R.string.welcome_at, next.start.format(TimeFormat))
            Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }) {
        when {
            !calendarConnected -> PillLine(EuriseIcons.calendar(), stringResource(R.string.today_connect_calendar), null, onConnectCalendar)
            upcoming.isEmpty() -> PillLine(EuriseIcons.calendar(), stringResource(R.string.today_free), null, onCalendar)
            else -> SwipeSection(upcoming.take(9).chunked(perPage)) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    page.forEach { e ->
                        val time = if (e.allDay) stringResource(R.string.today_all_day) else e.start.format(TimeFormat)
                        PillLine(
                            EuriseIcons.calendar(), "$time  ${e.title}", e.location.takeIf { it.isNotBlank() }, onCalendar,
                            tint = CalendarTint.nearest(e.color).colors().dot,
                        )
                    }
                }
            }
        }
        if (showTasks) {
            Label(stringResource(R.string.welcome_tasks, tasks.size))
            SwipeSection(tasks.take(9)) { t ->
                PillLine(EuriseIcons.task(), t.title, t.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }, onTasks)
            }
        }
        if (showRoute) {
            Label(stringResource(R.string.welcome_places))
            SwipeSection(destinations.take(9)) { e ->
                val first = e == destinations.first()
                val minutes = travel?.takeIf { first }?.minutes
                PillLine(
                    EuriseIcons.map(),
                    e.location,
                    if (minutes != null) {
                        stringResource(R.string.welcome_route_minutes, minutes) + " · " +
                            stringResource(R.string.welcome_leave_by, e.start.minusMinutes((minutes + 5).toLong()).format(TimeFormat))
                    } else {
                        e.start.format(TimeFormat) + "  " + e.title
                    },
                    { onRoute(e.location) },
                    right = stringResource(R.string.welcome_route_open),
                )
            }
        }
    }
}

/** Mostra [lines] righe e fa scorrere in verticale le altre. */
@Composable
private fun ScrollBox(lines: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.heightIn(max = (lines * 37).dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

/** Una sezione che mostra un elemento alla volta e si scorre di lato; a destra il contatore "2/5". */
@Composable
private fun <T> SwipeSection(items: List<T>, content: @Composable (T) -> Unit) {
    if (items.size <= 1) {
        items.firstOrNull()?.let { content(it) }
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState { items.size }
    Box {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().padding(end = 30.dp), pageSpacing = 12.dp) { page ->
            content(items[page])
        }
        Text(
            "${pager.currentPage + 1}/${items.size}",
            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** Seconda pillola: chi ti ha cercato, con i pulsanti per i contatti recenti e preferiti. */
@Composable
fun CommsPill(
    messages: List<MessageItem>,
    enabled: Boolean,
    hazeState: HazeState,
    compact: Boolean,
    onOpen: (MessageItem) -> Unit,
    onRecent: () -> Unit,
    onStarred: () -> Unit,
    onGrant: () -> Unit,
    onHub: () -> Unit,
) {
    Pill(hazeState, Modifier.fillMaxWidth(), title = stringResource(R.string.welcome_comms), trailing = {
        SmallRound(EuriseIcons.clock(2f), stringResource(R.string.people_recent), onRecent)
        Spacer(Modifier.width(6.dp))
        SmallRound(EuriseIcons.star(2f), stringResource(R.string.people_starred), onStarred)
        Spacer(Modifier.width(2.dp))
    }) {
        when {
            !enabled -> PillLine(EuriseIcons.chat(), stringResource(R.string.setup_notifications), stringResource(R.string.grant), onGrant)
            messages.isEmpty() -> PillLine(EuriseIcons.chat(), stringResource(R.string.card_messages_empty), null, onHub)
            // Si vedono le prime due o tre; le altre (fino a dodici) scorrendo dentro la pillola.
            else -> ScrollBox(if (compact) 2 else 3) { messages.take(12).forEach { m ->
                PillLine(
                    if (m.packageName.contains("dialer") || m.packageName.contains("phone")) EuriseIcons.phone() else EuriseIcons.message(),
                    "${m.title} · ${m.appLabel}",
                    m.text.ifBlank { stringResource(R.string.message_no_preview) },
                    { onOpen(m) },
                    right = java.time.Instant.ofEpochMilli(m.postedAt).atZone(java.time.ZoneId.systemDefault()).format(TimeFormat),
                )
            } }
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) }
}

/** Terza pillola, a sinistra: gli ultimi lavori sul telefono. */
@Composable
fun WorkPill(
    files: List<RecentFile>,
    hasAccess: Boolean,
    hazeState: HazeState,
    modifier: Modifier,
    onOpen: (RecentFile) -> Unit,
    onGrant: () -> Unit,
    onScan: () -> Unit,
) {
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_work), trailing = {
        SmallRound(EuriseIcons.camera(2f), stringResource(R.string.welcome_scan), onScan)
        Spacer(Modifier.width(2.dp))
    }) {
        when {
            !hasAccess -> PillLine(EuriseIcons.folder(), stringResource(R.string.welcome_work_grant), null, onGrant)
            files.isEmpty() -> Text(stringResource(R.string.welcome_work_empty), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> ScrollBox(2) { files.take(10).forEach { f ->
                val icon = when {
                    f.mime.startsWith("audio/") -> EuriseIcons.mic()
                    f.mime == "application/pdf" -> EuriseIcons.note()
                    else -> EuriseIcons.folder()
                }
                PillLine(icon, f.name, f.folder, { onOpen(f) })
            } }
        }
    }
}

/** Terza pillola, a destra: un po' di musica, con i comandi del lettore. */
@Composable
fun MusicHalf(playing: NowPlaying?, enabled: Boolean, hazeState: HazeState, modifier: Modifier, onOpenPlayer: () -> Unit, onGrant: () -> Unit) {
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_music)) {
        when {
            !enabled -> Text(
                stringResource(R.string.grant), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGrant),
            )
            playing == null -> Text(
                stringResource(R.string.welcome_music_start), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenPlayer),
            )
            else -> {
                Text(playing.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenPlayer))
                val controls = playing.controller.transportControls
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    MiniButton(EuriseIcons.previous(), stringResource(R.string.music_previous), false) { controls.skipToPrevious() }
                    MiniButton(
                        if (playing.playing) EuriseIcons.pause() else EuriseIcons.play(),
                        stringResource(if (playing.playing) R.string.music_pause else R.string.music_play), true,
                    ) { if (playing.playing) controls.pause() else controls.play() }
                    MiniButton(EuriseIcons.next(), stringResource(R.string.music_next), false) { controls.skipToNext() }
                }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp)) }
}

/**
 * Elenco dei contatti (recenti o preferiti) che scorre: toccando un contatto compaiono i modi per
 * raggiungerlo (chiamata, SMS, chat, email).
 */
@Composable
fun PeopleSheet(
    title: String,
    people: List<Person>?,
    needsPermission: Boolean,
    onGrant: () -> Unit,
    loadActions: suspend (Person) -> List<ContactAction>,
    onAction: (ContactAction) -> Unit,
    onOpenContact: (Person) -> Unit,
    onClose: () -> Unit,
) {
    var open by remember { mutableStateOf<Person?>(null) }
    var actions by remember { mutableStateOf<List<ContactAction>?>(null) }
    LaunchedEffect(open) { actions = open?.let { loadActions(it) } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onClose() } }
            .closeOnSwipe(up = false, onClose = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
            )
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
            if (needsPermission) {
                Text(
                    stringResource(R.string.people_grant), fontSize = 13.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.grant), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGrant).padding(vertical = 8.dp),
                )
            }
            if (people != null && people.isEmpty() && !needsPermission) {
                Text(stringResource(R.string.people_empty), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(people.orEmpty(), key = { it.name + it.lastAt }) { person ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { open = if (open == person) null else person },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(person, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                person.via?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        if (open == person) {
                            val list = actions
                            Column(Modifier.padding(start = 52.dp, bottom = 6.dp)) {
                                when {
                                    list == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    list.isEmpty() -> Text(
                                        stringResource(R.string.people_no_actions), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { onOpenContact(person) },
                                    )
                                    else -> list.forEach { a ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onAction(a) }.padding(vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                when (a.kind) {
                                                    ContactAction.Kind.Call -> EuriseIcons.phone()
                                                    ContactAction.Kind.Sms -> EuriseIcons.message()
                                                    ContactAction.Kind.Chat -> EuriseIcons.chat()
                                                    ContactAction.Kind.Email -> EuriseIcons.mail()
                                                },
                                                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                Text(a.detail, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(person: Person, size: Int) {
    val initial = person.name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val photo = person.photo
        if (photo != null) {
            ContactPhoto(photo, Modifier.fillMaxSize())
        } else {
            Text(initial, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = (size * 0.4f).sp)
        }
    }
}

/** Foto del contatto letta dalla Rubrica (miniatura), senza librerie esterne. */
@Composable
private fun ContactPhoto(uri: android.net.Uri, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }?.let {
                    it.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(it, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

/**
 * Pillola Salute: battiti, passi, sonno e calorie da Health Connect. Se mancano i dati, un suggerimento su
 * come collegare orologio o band (Gadgetbridge da F-Droid, con la sincronizzazione verso Health Connect).
 */
@Composable
fun HealthPill(
    state: eu.eurise.feature.launcher.health.HealthState,
    today: eu.eurise.feature.launcher.health.HealthToday?,
    hazeState: HazeState,
    onGrant: () -> Unit,
    onGadgetbridge: () -> Unit,
) {
    val updated = today?.updatedAt?.atZone(java.time.ZoneId.systemDefault())?.format(TimeFormat)
    Pill(hazeState, Modifier.fillMaxWidth(), title = stringResource(R.string.welcome_health), trailing = {
        if (updated != null) Text(stringResource(R.string.health_updated, updated), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        when {
            state == eu.eurise.feature.launcher.health.HealthState.Unavailable -> {
                Text(stringResource(R.string.health_tip), fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.health_tip_action), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGadgetbridge).padding(vertical = 2.dp),
                )
            }
            state == eu.eurise.feature.launcher.health.HealthState.NeedsPermission -> PillLine(
                EuriseIcons.heart(), stringResource(R.string.health_grant), stringResource(R.string.health_grant_sub), onGrant,
            )
            today == null || (today.steps == null && today.heartRate == null && today.sleep == null) -> {
                Text(stringResource(R.string.health_empty), fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.health_tip_action), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGadgetbridge).padding(vertical = 2.dp),
                )
            }
            else -> Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthValue(EuriseIcons.heart(2f), today.heartRate?.toString() ?: "—", stringResource(R.string.health_bpm))
                HealthValue(EuriseIcons.task(2f), today.steps?.let { String.format(java.util.Locale.ITALIAN, "%,d", it) } ?: "—", stringResource(R.string.health_steps))
                HealthValue(
                    EuriseIcons.moon(2f),
                    today.sleep?.let { "${it.toHours()} h ${it.toMinutes() % 60}" } ?: "—",
                    stringResource(R.string.health_sleep),
                )
                HealthValue(EuriseIcons.sun(2f), today.kcal?.let { "%.0f".format(it) } ?: "—", stringResource(R.string.health_kcal))
            }
        }
    }
}

@Composable
private fun HealthValue(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Alba e tramonto accanto all'orario. */
@Composable
fun SunTimesColumn(sun: Pair<java.time.LocalTime, java.time.LocalTime>?) {
    if (sun == null) return
    val color = MaterialTheme.colorScheme.onBackground
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("↑ " + sun.first.format(TimeFormat), "↓ " + sun.second.format(TimeFormat)).forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.sun(2f), null, tint = color.copy(alpha = if (i == 0) 1f else 0.7f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}
