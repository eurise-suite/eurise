package eu.eurise.feature.launcher.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.markets.MarketsRepository
import eu.eurise.feature.launcher.news.BuiltInFeeds
import eu.eurise.feature.launcher.news.Feed
import eu.eurise.feature.launcher.news.FeedGroup
import eu.eurise.feature.launcher.news.NewsRepository
import eu.eurise.feature.launcher.news.Regions

/** Foglio dal basso comune alle impostazioni delle schede. */
@Composable
private fun BottomSheet(title: String, onClose: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
            )
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.card_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(6.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), content = content)
        }
    }
}

@Composable
private fun AddField(hint: String, keyboard: KeyboardType, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button) { if (text.isNotBlank()) { onAdd(text); text = "" } },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.plus(), stringResource(R.string.card_add), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Fonti delle notizie: agenzie, testate nazionali ed estere, feed della propria regione con le province da
 * seguire, e feed aggiunti a mano (per esempio il giornale della propria città).
 */
@Composable
fun NewsSourcesSheet(repository: NewsRepository, onClose: () -> Unit) {
    val enabled by repository.enabled.collectAsState()
    val custom by repository.custom.collectAsState()
    val provinces by repository.provinces.collectAsState()
    var openRegion by remember { mutableStateOf<String?>(null) }
    val groups = listOf(
        FeedGroup.Agencies to R.string.news_group_agencies,
        FeedGroup.National to R.string.news_group_national,
        FeedGroup.Foreign to R.string.news_group_foreign,
    )
    BottomSheet(stringResource(R.string.news_sources_title), onClose) {
        groups.forEach { (group, label) ->
            item { Box(Modifier.padding(top = 12.dp, bottom = 2.dp)) { Label(stringResource(label)) } }
            items(BuiltInFeeds.filter { it.group == group }, key = { it.id }) { feed ->
                SwitchRow(feed.name, feed.id in enabled, { repository.setEnabled(feed.id, it) })
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(stringResource(R.string.news_group_local))
                Text(stringResource(R.string.news_local_hint), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(Regions, key = { it.id }) { region ->
            Column {
                val followed = region.provinces.count { it in provinces }
                SwitchRow(
                    region.name, region.feed.id in enabled, { repository.setEnabled(region.feed.id, it) },
                    trailing = {
                        Text(
                            if (followed > 0) stringResource(R.string.news_provinces_count, followed) else stringResource(R.string.news_provinces),
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { openRegion = if (openRegion == region.id) null else region.id }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    },
                )
                if (openRegion == region.id) {
                    Column(Modifier.padding(start = 16.dp)) {
                        region.provinces.forEach { p -> SwitchRow(p, p in provinces, { repository.setProvince(p, it) }) }
                    }
                }
            }
        }
        item { Box(Modifier.padding(top = 12.dp, bottom = 6.dp)) { Label(stringResource(R.string.news_group_custom)) } }
        items(custom, key = { it.id }) { feed: Feed ->
            SwitchRow(feed.name, feed.id in enabled, { repository.setEnabled(feed.id, it) }, trailing = {
                Text(
                    stringResource(R.string.news_remove), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { repository.removeCustom(feed) }.padding(8.dp),
                )
            })
        }
        item { AddField(stringResource(R.string.news_add_hint), KeyboardType.Uri) { runCatching { repository.addCustom(it) } } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Titoli della scheda Borsa: si tolgono con il meno e si aggiungono con il simbolo (es. ENI.MI, ^GDAXI). */
@Composable
fun StocksSheet(repository: MarketsRepository, onClose: () -> Unit) {
    val symbols by repository.symbols.collectAsState()
    BottomSheet(stringResource(R.string.stocks_title), onClose) {
        item {
            Text(stringResource(R.string.stocks_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(symbols, key = { it }) { symbol ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) { repository.remove(symbol) },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.minus(), stringResource(R.string.news_remove), modifier = Modifier.size(14.dp)) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { AddField(stringResource(R.string.stocks_add_hint), KeyboardType.Ascii) { repository.add(it) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
