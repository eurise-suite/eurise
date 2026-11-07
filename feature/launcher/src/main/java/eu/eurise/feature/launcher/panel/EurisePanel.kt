package eu.eurise.feature.launcher.panel

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.home.scrimColor
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NotificationStore
import eu.eurise.feature.launcher.notifications.NowPlaying
import eu.eurise.feature.launcher.summary.MusicBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Italian = Locale.ITALIAN
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Italian)

/**
 * Pannello Eurise: si apre scorrendo in giù dalla home. Interruttori rapidi, musica e notifiche nello stile
 * della suite. Wi-Fi e Bluetooth aprono il pannello di sistema: Android non permette alle app di accenderli.
 */
@Composable
fun EurisePanel(
    now: LocalDateTime,
    notifications: List<MessageItem>,
    nowPlaying: NowPlaying?,
    notificationsOn: Boolean,
    hazeState: HazeState,
    onOpen: (MessageItem) -> Unit,
    onGrant: () -> Unit,
    onSystemShade: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val messages = notifications.filter { it.isMessage }
    val others = notifications.filterNot { it.isMessage }

    Box(
        Modifier
            .fillMaxSize()
            .glass(hazeState, RectangleShape, elevation = 0.dp, outline = false)
            .background(scrimColor()),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -threshold) onClose() },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(now.format(TimeFormat), fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            now.format(DateFormat).replaceFirstChar { it.titlecase(Italian) },
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    RoundButton(EuriseIcons.sun(), stringResource(R.string.panel_display), hazeState) {
                        context.startSafely(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                    }
                }
            }
            item { QuickTiles(context, refresh, hazeState, onSystemShade) { refresh++ } }
            item {
                Text(
                    stringResource(R.string.panel_note),
                    fontSize = 12.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (nowPlaying != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(24.dp), elevation = 6.dp, outline = false).padding(14.dp),
                    ) { MusicBody(nowPlaying, compact = false) }
                }
            }
            if (!notificationsOn) {
                item {
                    Column(
                        Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(24.dp), elevation = 6.dp, outline = false)
                            .clickable(onClick = onGrant).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.card_messages_grant), fontSize = 13.sp, lineHeight = 17.sp)
                        Text(stringResource(R.string.grant), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (messages.isNotEmpty()) {
                item { SectionHead(stringResource(R.string.card_messages)) }
                items(messages, key = { it.key }) { n -> NotificationRow(n, hazeState, onOpen) }
            }
            if (others.isNotEmpty()) {
                item { SectionHead(stringResource(R.string.panel_other)) }
                items(others, key = { it.key }) { n -> NotificationRow(n, hazeState, onOpen) }
            }
            if (notificationsOn && notifications.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.panel_empty), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHead(text: String) {
    Box(Modifier.padding(start = 4.dp, top = 8.dp)) { Label(text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)) }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, hazeState: HazeState, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(hazeState, CircleShape, elevation = 4.dp, outline = false)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
}

/** Stato di un interruttore rapido letto dal sistema. */
private data class TileState(val on: Boolean, val sub: String)

@Composable
private fun QuickTiles(context: Context, refresh: Int, hazeState: HazeState, onSystemShade: () -> Unit, onChanged: () -> Unit) {
    var torchOn by remember { mutableStateOf(false) }
    val camera = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember {
        runCatching {
            camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    DisposableEffect(camera) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        runCatching { camera?.registerTorchCallback(callback, null) }
        onDispose { runCatching { camera?.unregisterTorchCallback(callback) } }
    }

    val on = stringResource(R.string.state_on)
    val off = stringResource(R.string.state_off)
    val wifi = remember(refresh) {
        val enabled = runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true }.getOrDefault(false)
        TileState(enabled, if (enabled) on else off)
    }
    val bluetooth = remember(refresh) {
        val enabled = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
        TileState(enabled, if (enabled) on else off)
    }
    val nm = remember { context.getSystemService(NotificationManager::class.java) }
    val dnd = remember(refresh) {
        val active = nm?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } == true
        TileState(active, if (active) on else off)
    }
    val rotation = remember(refresh) {
        val auto = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)
        TileState(auto, context.getString(if (auto) R.string.rotation_auto else R.string.rotation_locked))
    }

    val tiles = listOf(
        Triple(EuriseIcons.wifi(), stringResource(R.string.tile_wifi), wifi) to {
            val panel = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)
            context.startSafely(panel)
        },
        Triple(EuriseIcons.bluetooth(), stringResource(R.string.tile_bluetooth), bluetooth) to {
            context.startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        },
        Triple(EuriseIcons.torch(), stringResource(R.string.tile_torch), TileState(torchOn, if (torchOn) on else off)) to {
            torchId?.let { id -> runCatching { camera?.setTorchMode(id, !torchOn) } }
            Unit
        },
        Triple(EuriseIcons.moon(), stringResource(R.string.tile_dnd), dnd) to {
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(if (dnd.on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                onChanged()
            } else {
                context.startSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        },
        Triple(EuriseIcons.rotate(), stringResource(R.string.tile_rotation), rotation) to {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotation.on) 0 else 1)
                onChanged()
            } else {
                context.startSafely(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")),
                )
            }
        },
        Triple(EuriseIcons.grid(), stringResource(R.string.tile_system), TileState(false, stringResource(R.string.tile_system_sub))) to onSystemShade,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (info, action) ->
                    val (icon, label, state) = info
                    Tile(icon, label, state, hazeState, action, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, state: TileState, hazeState: HazeState, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val bg = if (state.on) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color.Transparent
    Column(
        modifier
            .height(68.dp)
            .glass(hazeState, shape, elevation = 4.dp, outline = false)
            .background(bg, shape)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = if (state.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Column {
            Text(label, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(state.sub, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(n: MessageItem, hazeState: HazeState, onOpen: (MessageItem) -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value != SwipeToDismissBoxValue.Settled && n.clearable) {
            NotificationStore.dismiss(n.key); true
        } else false
    })
    SwipeToDismissBox(state = dismiss, backgroundContent = {}, enableDismissFromStartToEnd = n.clearable, enableDismissFromEndToStart = n.clearable) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(hazeState, RoundedCornerShape(22.dp), elevation = 4.dp, outline = false)
                .clickable { onOpen(n) }
                .padding(14.dp),
        ) {
            Box(
                Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (n.isMessage) EuriseIcons.message(2f) else EuriseIcons.grid(2f), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${n.appLabel} · ${java.time.Instant.ofEpochMilli(n.postedAt).atZone(java.time.ZoneId.systemDefault()).format(TimeFormat)}",
                    fontSize = 11.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
                Text(n.title, fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) {
                    Text(n.text, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
                if (n.actions.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        n.actions.forEach { a ->
                            Text(
                                a.title,
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { runCatching { a.intent.send() } }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
