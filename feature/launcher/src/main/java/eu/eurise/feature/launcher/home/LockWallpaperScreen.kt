package eu.eurise.feature.launcher.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Sfondo anche sulla schermata di blocco (tavola SfoBlocco). [homeScene] è la scena della Scrivania di adesso. */
@Composable
fun LockWallpaperScreen(homeScene: Scene, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf(LockWallpaper.load(context)) }
    val lockScene = remember(o) { LockWallpaper.scene(context, o) }
    val apply = { next: LockWallpaper.Options ->
        o = next
        LockWallpaper.save(context, next)
        LockWallpaper.schedule(context)
        scope.launch {
            if (next.mode == LockWallpaper.Mode.Off) LockWallpaper.clear(context)
            else if (!(next.mode == LockWallpaper.Mode.Other && next.other == LockWallpaper.PHOTO) && !LockWallpaper.refresh(context, force = true)) {
                Toast.makeText(context, R.string.lock_wp_failed, Toast.LENGTH_LONG).show()
            }
        }
        Unit
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val next = o.copy(mode = LockWallpaper.Mode.Other, other = LockWallpaper.PHOTO)
            o = next
            LockWallpaper.save(context, next)
            LockWallpaper.schedule(context)
            scope.launch { if (!LockWallpaper.setPhoto(context, uri)) Toast.makeText(context, R.string.lock_wp_failed, Toast.LENGTH_LONG).show() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.chevronLeft(), stringResource(R.string.card_done), modifier = Modifier.size(20.dp))
            }
            Column {
                Text(stringResource(R.string.lock_wp_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.appearance_title), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            Preview(stringResource(R.string.lock_wp_desk)) { SceneBackground(homeScene, Modifier.fillMaxSize()) }
            Preview(stringResource(R.string.lock_wp_lock)) {
                when {
                    o.mode == LockWallpaper.Mode.Off -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    o.mode == LockWallpaper.Mode.Other && o.other == LockWallpaper.PHOTO -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.image(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                    else -> SceneBackground(lockScene, Modifier.fillMaxSize())
                }
                if (o.mode != LockWallpaper.Mode.Off && o.dim) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Section(stringResource(R.string.lock_wp_where)) {
            listOf(
                Triple(LockWallpaper.Mode.Off, R.string.lock_wp_only_desk, null),
                Triple(LockWallpaper.Mode.Same, R.string.lock_wp_same, R.string.lock_wp_same_hint),
                Triple(LockWallpaper.Mode.Other, R.string.lock_wp_other, R.string.lock_wp_other_hint),
            ).forEachIndexed { i, (mode, title, hint) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(o.mode == mode, role = Role.RadioButton) { apply(o.copy(mode = mode)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(o.mode == mode, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(title), fontSize = 15.sp, fontWeight = if (o.mode == mode) FontWeight.Bold else FontWeight.SemiBold)
                        if (hint != null) Text(stringResource(hint), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (o.mode == LockWallpaper.Mode.Other) {
            val choices = listOf<Pair<String?, Int>>(
                null to R.string.wallpaper_auto,
                "season:Spring" to R.string.wp_spring, "season:Summer" to R.string.wp_summer,
                "season:Autumn" to R.string.wp_autumn, "season:Winter" to R.string.wp_winter,
                "fest:Christmas" to R.string.wp_christmas, "fest:NewYearsEve" to R.string.wp_newyear,
                "fest:Epiphany" to R.string.wp_epiphany, "fest:Easter" to R.string.wp_easter,
                "fest:Birthday" to R.string.wp_birthday, "fest:Anniversary" to R.string.wp_anniversary,
                LockWallpaper.PHOTO to R.string.lock_wp_photo,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (key, label) ->
                    val on = o.other == key
                    Text(
                        stringResource(label), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable(role = Role.RadioButton) {
                                if (key == LockWallpaper.PHOTO) photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else apply(o.copy(other = key))
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        if (o.mode != LockWallpaper.Mode.Off) {
            Section(stringResource(R.string.lock_wp_on_lock)) {
                SwitchRow(stringResource(R.string.lock_wp_dim), stringResource(R.string.lock_wp_dim_hint), o.dim) { apply(o.copy(dim = it)) }
                if (o.other != LockWallpaper.PHOTO || o.mode == LockWallpaper.Mode.Same) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchRow(stringResource(R.string.lock_wp_follow), stringResource(R.string.lock_wp_follow_hint), o.follow) { apply(o.copy(follow = it)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Preview(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.width(130.dp).height(250.dp).clip(RoundedCornerShape(22.dp))
                .border(4.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(22.dp)),
        ) { content() }
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}
