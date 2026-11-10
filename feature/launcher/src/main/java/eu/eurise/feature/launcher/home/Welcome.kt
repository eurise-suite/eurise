package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var widthPx by remember { mutableIntStateOf(0) }
    val bubbleWidth = with(LocalDensity.current) { if (widthPx > 0) widthPx.toDp() else 400.dp }
    val report = LocalPillMeasure.current
    // Fuori il vetro (che può essere più alto, per allinearsi alle bolle accanto), dentro il titolo fisso e il
    // contenuto, che scorre con la barra se non entra e riferisce comunque la sua altezza naturale.
    val verticalPadding = with(LocalDensity.current) { (20.dp).roundToPx() }
    var titleHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    if (report != null) {
        val natural = verticalPadding + titleHeight + if (collapsed) 0 else contentHeight
        SideEffect { report(natural) }
    }
    Column(modifier.glass(hazeState, RoundedCornerShape(22.dp), elevation = 8.dp, outline = false)) {
    androidx.compose.runtime.CompositionLocalProvider(LocalBubbleWidth provides bubbleWidth) {
    TitleAboveContent(
        Modifier.onSizeChanged { widthPx = it.width }.padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = if (collapsed) 9.dp else 11.dp),
    ) {
        // Titolo più vicino al contenuto: la riga del titolo è bassa quanto basta per la freccia.
        Row(Modifier.fillMaxWidth().onSizeChanged { titleHeight = it.height }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Label(title) }
            if (!collapsed) trailing?.invoke()
            val label = stringResource(if (collapsed) R.string.pill_expand else R.string.pill_collapse)
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label) { collapsed = !collapsed },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    EuriseIcons.chevronRight(2.2f), label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).rotate(if (collapsed) 90f else -90f),
                )
            }
        }
        if (!collapsed) {
            val scroll = rememberScrollState()
            val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
            Box(
                Modifier.padding(top = 1.dp).fillMaxWidth().drawWithContent {
                    drawContent()
                    val max = scroll.maxValue
                    if (max in 1 until Int.MAX_VALUE) {
                        val h = size.height
                        val thumb = (h * h / (h + max)).coerceAtLeast(18.dp.toPx())
                        val w = 3.dp.toPx()
                        drawRoundRect(bar, Offset(size.width - w, (h - thumb) * scroll.value / max), Size(w, thumb), CornerRadius(w / 2))
                    }
                },
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                    Column(
                        Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height }.padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        content = content,
                    )
                }
            }
        }
    }
    }
    }
}

private const val UNBOUNDED_BUBBLE_PX = 65_000

/** Il titolo prende l'altezza che gli serve; il contenuto quella che resta nella bolla, senza mai spingerlo fuori. */
@Composable
private fun TitleAboveContent(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val title = measurables[0].measure(loose)
        val rest = measurables.getOrNull(1)?.measure(
            // Dentro la home, che scorre, l'altezza è illimitata: un'area scorrevole va misurata con un limite o l'app si chiude.
            loose.copy(maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - title.height).coerceAtLeast(0) else UNBOUNDED_BUBBLE_PX),
        )
        val width = constraints.constrainWidth(maxOf(title.width, rest?.width ?: 0))
        layout(width, constraints.constrainHeight(title.height + (rest?.height ?: 0))) {
            title.placeRelative(0, 0)
            rest?.placeRelative(0, title.height)
        }
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
internal fun PillLine(icon: ImageVector, main: String, sub: String?, onClick: () -> Unit, tint: Color? = null, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        // Un po' d'aria tra la riga principale e quella secondaria.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(main, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) {
                Text(sub, fontSize = 11.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (right != null) Text(right, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Seconda pillola: chi ti ha cercato. È la stessa bolla della scheda del Riepilogo: stesse righe, stessi gesti
 * (vedi [CommsBody]) e gli stessi tondi per i contatti recenti e preferiti.
 */
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
    onAppInfo: () -> Unit,
    onHub: () -> Unit,
    onBubble: (MessageItem) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_comms), trailing = { CommsButtons(onRecent, onStarred) }) {
        CommsBody(messages, enabled, if (compact) 2 else 3, onOpen, onBubble, onGrant, onAppInfo, onHub)
    }
}

@Composable
internal fun SmallRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) }
}

/** Una scorciatoia in cima a Documenti. */
class DocShortcut(val icon: ImageVector, val label: String, val onClick: () -> Unit)

private val DocIconsBeside = 310.dp

/** Terza pillola, a sinistra: gli ultimi lavori sul telefono. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkPill(
    files: List<RecentFile>,
    hasAccess: Boolean,
    hazeState: HazeState,
    modifier: Modifier,
    onOpen: (RecentFile) -> Unit,
    onGrant: () -> Unit,
    shortcuts: List<DocShortcut>,
) {
    val icons: @Composable () -> Unit = {
        shortcuts.forEach { s ->
            SmallRound(s.icon, s.label, s.onClick)
            Spacer(Modifier.width(4.dp))
        }
    }
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_work), trailing = {
        if (LocalBubbleWidth.current >= DocIconsBeside) icons()
    }) {
        // Nelle bolle strette le icone non entrano accanto al titolo: stanno in una riga sotto, e vanno a capo.
        if (LocalBubbleWidth.current < DocIconsBeside) FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) { icons() }
        when {
            !hasAccess -> PillLine(EuriseIcons.folder(), stringResource(R.string.welcome_work_grant), null, onGrant)
            files.isEmpty() -> Text(stringResource(R.string.welcome_work_empty), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> ScrollWithBar(RowHeight * 2) { files.take(10).forEachIndexed { i, f ->
                if (i > 0) PillDivider()
                val icon = when {
                    f.mime.startsWith("audio/") -> EuriseIcons.mic()
                    f.mime == "application/pdf" -> EuriseIcons.note()
                    else -> EuriseIcons.folder()
                }
                Box(Modifier.padding(vertical = 5.dp)) { PillLine(icon, f.name, f.folder, { onOpen(f) }) }
            } }
        }
    }
}

/** Musica: con più lettori aperti si passa dall'uno all'altro scorrendo di lato. */
@Composable
fun MusicHalf(
    players: List<NowPlaying>,
    enabled: Boolean,
    hazeState: HazeState,
    modifier: Modifier,
    onOpenPlayer: () -> Unit,
    onGrant: () -> Unit,
    services: List<eu.eurise.feature.launcher.apps.AppEntry> = emptyList(),
    onOpenService: (eu.eurise.feature.launcher.apps.AppEntry) -> Unit = {},
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_music), trailing = {
        services.forEach { app ->
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = app.label) { onOpenService(app) },
                contentAlignment = Alignment.Center,
            ) { eu.eurise.feature.launcher.apps.AppIcon(app, 24.dp, dark) }
            Spacer(Modifier.width(3.dp))
        }
    }) {
        when {
            !enabled -> Text(
                stringResource(R.string.grant), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onGrant),
            )
            players.isEmpty() -> Text(
                stringResource(R.string.welcome_music_start), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenPlayer),
            )
            players.size == 1 -> PlayerPage(players.first(), false, onOpenPlayer)
            else -> {
                val start = players.indexOfFirst { it.playing }.coerceAtLeast(0)
                val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = start) { players.size }
                androidx.compose.foundation.pager.HorizontalPager(
                    pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp, key = { players[it].controller.packageName },
                ) { page -> PlayerPage(players[page], true, onOpenPlayer) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                    players.indices.forEach { i ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == pager.currentPage) 0.8f else 0.25f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPage(playing: NowPlaying, showSource: Boolean, onOpenPlayer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenPlayer), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlbumArt(playing, if (LocalBubbleWidth.current >= 240.dp) 52.dp else 40.dp)
            Column {
                if (showSource) Text(playing.appLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text(playing.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playing.artist.isNotBlank()) Text(playing.artist, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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

/** La copertina del brano; se il lettore non la fornisce, una nota. */
@Composable
internal fun AlbumArt(playing: NowPlaying, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(10.dp)
    val art = playing.art
    val image = remember(art) { art?.asImageBitmap() }
    Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        if (image != null) {
            androidx.compose.foundation.Image(image, stringResource(R.string.music_cover), contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(EuriseIcons.music(2f), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.45f))
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
    initial: Person? = null,
) {
    var open by remember { mutableStateOf(initial) }
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
internal fun Avatar(person: Person, size: Int) {
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
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val updated = today?.updatedAt?.atZone(java.time.ZoneId.systemDefault())?.format(TimeFormat)
    Pill(hazeState, modifier, title = stringResource(R.string.welcome_health), trailing = {
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

/**
 * Alba e tramonto accanto all'orario e, sotto, la riga della sveglia: il conto alla rovescia che corre, il cronometro
 * acceso, la prossima sveglia oppure "No sveglia". Toccandola si sceglie tra sveglia, cronometro e conto alla rovescia.
 */
@Composable
fun SunTimesColumn(
    sun: Pair<java.time.LocalTime, java.time.LocalTime>?,
    alarm: LocalDateTime? = null,
    now: LocalDateTime = LocalDateTime.now(),
    onClock: ((tab: Int) -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    // Stato dell'Orologio: letto una volta fuori dal thread principale, poi aggiornato a ogni cambiamento.
    val clock by produceState(eu.eurise.core.suite.ClockStatus.Status()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { eu.eurise.core.suite.ClockStatus.read(context) }
        val prefs = eu.eurise.core.suite.ClockStatus.prefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = eu.eurise.core.suite.ClockStatus.read(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Timer e cronometro cambiano ogni secondo: solo allora si ridisegna più spesso.
    val ticking = (clock.timerEndsAt?.let { it > System.currentTimeMillis() } == true) || clock.stopwatchStart != null
    val millis by produceState(System.currentTimeMillis(), ticking) {
        while (ticking) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) }
    }
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        sun?.let {
            listOf("↑ " + it.first.format(TimeFormat), "↓ " + it.second.format(TimeFormat)).forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.sun(2f), null, tint = color.copy(alpha = if (i == 0) 1f else 0.7f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
        }
        val timerLeft = clock.timerEndsAt?.let { it - millis }?.takeIf { it > 0 }
        val stopwatch = clock.stopwatchStart?.let { clock.stopwatchAccumulated + (millis - it) }
            ?: clock.stopwatchAccumulated.takeIf { it > 0 }
        val label = when {
            timerLeft != null -> "⏳ " + duration(timerLeft)
            stopwatch != null -> "⏱ " + duration(stopwatch)
            alarm != null -> alarmLabel(alarm, now)
            else -> stringResource(R.string.home_no_alarm)
        }
        val description = if (alarm != null && timerLeft == null && stopwatch == null) stringResource(R.string.home_next_alarm, label) else label
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (onClock != null) Modifier.clickable { menu = true } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Icon(EuriseIcons.alarm(2f), null, tint = color, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(R.string.home_clock_alarm to 0, R.string.home_clock_stopwatch to 2, R.string.home_clock_timer to 1).forEach { (text, tab) ->
                    DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { menu = false; onClock?.invoke(tab) })
                }
            }
        }
    }
}

/** 1:05:09 o 4:07. */
private fun duration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
