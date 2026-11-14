package eu.eurise.feature.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.CategoryIcons
import eu.eurise.feature.launcher.apps.DefaultBarCategories
import eu.eurise.feature.launcher.apps.MAX_CATEGORIES
import eu.eurise.feature.launcher.apps.MIN_CATEGORIES
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.home.scrimColor

private val RowHeight = 60.dp

/**
 * Barra delle categorie: da 2 a 9. Si trascinano dalla maniglia per riordinarle, il meno le toglie
 * (Preferiti resta sempre), toccandone una si cambiano nome e simbolo, in fondo se ne aggiunge una nuova.
 */
@Composable
fun CategoryEditor(
    categories: List<BarCategory>,
    appCount: (BarCategory) -> Int,
    hazeState: HazeState,
    onChange: (List<BarCategory>) -> Unit,
    onClose: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(categories)
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .glass(hazeState, RectangleShape, elevation = 0.dp, outline = false)
            .background(scrimColor())
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .windowInsetsPadding(WindowInsets.ime)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.categories_title), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onClose).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.card_done), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        }
        Text(
            stringResource(R.string.categories_hint), fontSize = 13.sp, lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )

        Column(
            Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(24.dp), elevation = 6.dp, outline = false).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            categories.forEach { category -> key(category.id) {
                var drag by remember(category.id) { mutableFloatStateOf(0f) }
                val selected = selectedId == category.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .zIndex(if (drag != 0f) 1f else 0f)
                        .graphicsLayer { translationY = drag }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { selectedId = if (selected) null else category.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Maniglia: trascinando di una riga si scambia la categoria con la vicina.
                    Box(
                        Modifier.size(36.dp).pointerInput(category.id) {
                            detectDragGestures(
                                onDragEnd = { drag = 0f },
                                onDragCancel = { drag = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount.y
                                    val list = current
                                    val i = list.indexOfFirst { it.id == category.id }
                                    if (drag > rowPx * 0.6f && i < list.lastIndex) {
                                        onChange(list.toMutableList().apply { add(i + 1, removeAt(i)) }); drag -= rowPx
                                    } else if (drag < -rowPx * 0.6f && i > 0) {
                                        onChange(list.toMutableList().apply { add(i - 1, removeAt(i)) }); drag += rowPx
                                    }
                                },
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.drag(), stringResource(R.string.categories_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    Box(
                        Modifier.size(40.dp).background(eu.eurise.feature.launcher.apps.IconBackgroundLight, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(category.iconVector(2f), null, tint = category.tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (selected && !category.isFavorites) {
                            BasicTextField(
                                value = category.label,
                                onValueChange = { text -> onChange(current.map { if (it.id == category.id) it.copy(label = text.take(24)) else it }) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            )
                        } else {
                            Text(category.label, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            if (category.isFavorites) stringResource(R.string.categories_home) else stringResource(R.string.categories_apps, appCount(category)),
                            fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val canRemove = categories.size > MIN_CATEGORIES
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (canRemove) 0.08f else 0.03f))
                            .clickable(enabled = canRemove, role = Role.Button) {
                                onChange(current.filterNot { it.id == category.id })
                                if (selected) selectedId = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            EuriseIcons.minus(), stringResource(R.string.categories_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canRemove) 1f else 0.3f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } }
            if (categories.size < MAX_CATEGORIES) {
                // Un tocco aggiunge la categoria e la apre: nome e simbolo si cambiano subito sotto.
                val defaultName = stringResource(R.string.categories_new)
                Row(
                    Modifier.fillMaxWidth().height(RowHeight).clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) {
                            val added = BarCategory("custom-${System.currentTimeMillis()}", defaultName, "home")
                            onChange(current + added); selectedId = added.id
                        }
                        .padding(start = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.plus(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.categories_add_new), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.categories_count, categories.size, MAX_CATEGORIES),
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        val selected = categories.firstOrNull { it.id == selectedId }
        if (selected != null) {
            Column(
                Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(24.dp), elevation = 6.dp, outline = false).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Label(stringResource(R.string.categories_symbol, selected.label))
                CategoryIcons.keys.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { key ->
                            val on = key == selected.icon
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                    .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable(role = Role.RadioButton) {
                                        onChange(current.map { if (it.id == selected.id) it.copy(icon = key) else it })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    CategoryIcons.getValue(key)(2.1f), null,
                                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.size(46.dp)) }
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.categories_select_hint), fontSize = 12.5.sp, lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }
        Text(
            stringResource(R.string.categories_reset), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { confirmReset = true }.padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.categories_reset_title)) },
            text = { Text(stringResource(R.string.categories_reset_message)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; selectedId = null; onChange(DefaultBarCategories) }) { Text(stringResource(R.string.categories_reset_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.categories_reset_cancel)) } },
        )
    }
}
