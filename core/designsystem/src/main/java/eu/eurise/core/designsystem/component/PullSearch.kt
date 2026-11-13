package eu.eurise.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/**
 * Ricerca che si tira giù (regola sull'ergonomia, tavole RicTira, RicTiraDestra, RicTiraSinistra, 13 novembre):
 * con la lista già in cima, uno swipe verso il basso porta il campo di ricerca appena sopra la tastiera, sotto il
 * pollice; avviata la ricerca, torna al suo posto. Dove non c'è il campo il gesto resta quello di prima.
 */
@Stable
class PullSearchState {
    /** Il campo è giù, sopra la tastiera. */
    var lowered by mutableStateOf(false)
    /** Quanto si è tirato (0..1), per l'accenno del campo in alto mentre si tira. */
    var pull by mutableFloatStateOf(0f)
        internal set

    fun lower() { lowered = true; pull = 0f }
    fun raise() { lowered = false; pull = 0f }
}

@Composable
fun rememberPullSearchState(): PullSearchState = remember { PullSearchState() }

/**
 * Da mettere sul contenitore della lista: legge lo scorrimento che la lista non usa più (è già in cima) e, oltre una
 * soglia, abbassa il campo. Non consuma nulla finché la lista scorre: il gesto normale resta com'è.
 */
@Composable
fun Modifier.pullToSearch(list: LazyListState, state: PullSearchState, enabled: Boolean = true): Modifier {
    val threshold = with(LocalDensity.current) { 84.dp.toPx() }
    val connection = remember(list, state, enabled, threshold) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || state.lowered || source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && atTop) {
                    pulled += available.y
                    state.pull = (pulled / threshold).coerceIn(0f, 1f)
                    if (pulled >= threshold) {
                        pulled = 0f
                        state.lower()
                    }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f || consumed.y != 0f) {
                    pulled = 0f
                    state.pull = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                pulled = 0f
                state.pull = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/**
 * Il campo abbassato: appoggiato sulla tastiera, con la lente dal lato della mano ([leftHanded]) e, se la schermata
 * aggiorna qualcosa, «Aggiorna» sopra il campo dallo stesso lato ([onRefresh]).
 */
@Composable
fun LoweredSearchField(
    state: PullSearchState,
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    searchLabel: String,
    leftHanded: Boolean,
    modifier: Modifier = Modifier,
    refreshLabel: String? = null,
    refreshDetail: String? = null,
    onRefresh: (() -> Unit)? = null,
    onSearch: (String) -> Unit = {},
) {
    AnimatedVisibility(
        visible = state.lowered,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onRefresh != null && refreshLabel != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, if (leftHanded) Alignment.Start else Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val detail: @Composable () -> Unit = {
                        if (refreshDetail != null) Text(refreshDetail, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!leftHanded) detail()
                    Row(
                        Modifier.height(38.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onRefresh).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(EuriseIcons.refresh(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(refreshLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (leftHanded) detail()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lens: @Composable () -> Unit = {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .clickable { onSearch(query); state.raise() }
                            .semantics { contentDescription = searchLabel },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.search(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                }
                if (leftHanded) lens()
                Box(
                    Modifier.weight(1f).height(52.dp).shadow(6.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text(hint, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query); state.raise() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (!leftHanded) lens()
            }
        }
    }
}
