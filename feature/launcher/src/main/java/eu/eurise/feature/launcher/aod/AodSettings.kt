package eu.eurise.feature.launcher.aod

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.notifications.NotificationStore

private val BubbleRow = 52.dp

/** Impostazioni del display a riposo. Accenderlo mostra prima la spiegazione; spegnerlo non chiede nulla. */
@Composable
fun AodSettingsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val store = remember { AodStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var explain by remember { mutableStateOf(false) }
    var waitingOverlay by remember { mutableStateOf(false) }
    val listening by NotificationStore.connected.collectAsState()
    val hasLift = remember { AodService.findLiftSensor(context.getSystemService(android.hardware.SensorManager::class.java)) != null }
    val apply = { next: AodConfig -> config = next; store.save(next); AodService.sync(context) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { apply(config.copy(enabled = true)) }
    val turnOn = {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else apply(config.copy(enabled = true))
    }
    val latestTurnOn by rememberUpdatedState(turnOn)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingOverlay && Settings.canDrawOverlays(context)) {
                waitingOverlay = false
                latestTurnOn()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.chevronLeft(), stringResource(R.string.card_done), modifier = Modifier.size(20.dp))
            }
            Text(stringResource(R.string.aod_title), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(config.enabled, onCheckedChange = { on -> if (on) explain = true else apply(config.copy(enabled = false)) })
        }
        if (config.enabled && !listening) {
            Text(
                stringResource(R.string.aod_needs_notifications), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.padding(8.dp),
            )
        }
        Section(stringResource(R.string.aod_when)) {
            AodMode.entries.forEachIndexed { i, mode ->
                if (i > 0) Divider()
                val (title, hint) = when (mode) {
                    AodMode.Popup -> R.string.aod_mode_popup to if (hasLift) R.string.aod_mode_popup_hint_lift else R.string.aod_mode_popup_hint
                    AodMode.Always -> R.string.aod_mode_always to R.string.aod_mode_always_hint
                    AodMode.Charging -> R.string.aod_mode_charging to R.string.aod_mode_charging_hint
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 62.dp)
                        .selectable(selected = config.mode == mode, role = Role.RadioButton) { apply(config.copy(mode = mode)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(config.mode == mode, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(hint), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (config.mode == AodMode.Popup) {
            Choices(stringResource(R.string.aod_duration), listOf(5, 10, 15, 30), config.seconds, { "$it s" }) { apply(config.copy(seconds = it)) }
        }
        BubblesSection(config, apply)
        Section(stringResource(R.string.aod_off_when)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.aod_off_night), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(config.nightOff, onCheckedChange = { apply(config.copy(nightOff = it)) })
            }
            Divider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.aod_off_pocket), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Choices(stringResource(R.string.aod_min_battery), listOf(10, 20, 30), config.minBattery, { "$it%" }) { apply(config.copy(minBattery = it)) }
        Text(stringResource(R.string.aod_gestures), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(16.dp))
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.aod_explain_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        R.string.aod_explain_read_t to R.string.aod_explain_read,
                        R.string.aod_explain_where_t to R.string.aod_explain_where,
                        R.string.aod_explain_who_t to R.string.aod_explain_who,
                        R.string.aod_explain_cost_t to R.string.aod_explain_cost,
                        R.string.aod_explain_perm_t to R.string.aod_explain_perm,
                        R.string.aod_explain_back_t to R.string.aod_explain_back,
                    ).forEach { (t, d) ->
                        Column {
                            Text(stringResource(t), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(d), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    if (Settings.canDrawOverlays(context)) turnOn()
                    else {
                        waitingOverlay = true
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                }) { Text(stringResource(R.string.aod_explain_ok)) }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.aod_explain_no)) } },
        )
    }
}

@Composable
private fun BubblesSection(config: AodConfig, apply: (AodConfig) -> Unit) {
    val current by rememberUpdatedState(config)
    val rowPx = with(LocalDensity.current) { BubbleRow.toPx() }
    Section(stringResource(R.string.aod_bubbles_count, config.bubbles.size, AOD_MAX_BUBBLES)) {
        config.bubbles.forEachIndexed { i, bubble -> key(bubble.id) {
            var drag by remember { mutableFloatStateOf(0f) }
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().height(BubbleRow).zIndex(if (drag != 0f) 1f else 0f).graphicsLayer { translationY = drag },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).pointerInput(bubble.id) {
                        detectDragGestures(
                            onDragEnd = { drag = 0f },
                            onDragCancel = { drag = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                drag += amount.y
                                val list = current.bubbles
                                val at = list.indexOf(bubble)
                                if (drag > rowPx * 0.6f && at < list.lastIndex) {
                                    apply(current.copy(bubbles = list.toMutableList().apply { add(at + 1, removeAt(at)) })); drag -= rowPx
                                } else if (drag < -rowPx * 0.6f && at > 0) {
                                    apply(current.copy(bubbles = list.toMutableList().apply { add(at - 1, removeAt(at)) })); drag += rowPx
                                }
                            },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.drag(), stringResource(R.string.aod_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(stringResource(bubble.title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (bubble == AodBubble.Comms) Text(stringResource(R.string.aod_only_icons), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (bubble == AodBubble.Meds) Text(stringResource(R.string.aod_only_time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { apply(current.copy(bubbles = current.bubbles - bubble)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.minus(), stringResource(R.string.card_remove), modifier = Modifier.size(14.dp)) }
            }
        } }
        val others = AodBubble.entries - config.bubbles.toSet()
        if (config.bubbles.size < AOD_MAX_BUBBLES) others.forEach { bubble ->
            Divider()
            Row(
                Modifier.fillMaxWidth().height(BubbleRow).clickable(role = Role.Button) { apply(current.copy(bubbles = current.bubbles + bubble)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.plus(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 9.dp, end = 15.dp).size(18.dp))
                Text(stringResource(bubble.title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp)) { content() }
    }
}

@Composable
private fun Divider() = androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Choices(title: String, values: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    Section(title) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                        .selectable(selected = on, role = Role.RadioButton) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) { Text(label(value), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}
