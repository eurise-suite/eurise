package eu.eurise.feature.launcher.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * I widget delle altre app nelle bolle della home. La Scrivania li ospita come fa ogni launcher (AppWidgetHost): li
 * disegna l'app che li offre, Eurise non ne legge il contenuto. Android chiede una volta il permesso di metterli.
 */
object AppWidgets {
    private const val HOST_ID = 0xE0E1
    @Volatile private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: synchronized(this) { host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it } }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    fun info(context: Context, id: Int): AppWidgetProviderInfo? = runCatching { manager(context).getAppWidgetInfo(id) }.getOrNull()

    fun delete(context: Context, id: Int) { runCatching { host(context).deleteAppWidgetId(id) } }

    /** I widget installati, raggruppati per app (nome dell'app → widget), in ordine alfabetico. */
    fun providers(context: Context): List<Pair<String, List<AppWidgetProviderInfo>>> {
        val pm = context.packageManager
        return manager(context).installedProviders
            .filter { it.provider.packageName != context.packageName }
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                app to list.sortedBy { it.loadLabel(pm) }
            }
            .sortedBy { it.first.lowercase() }
    }
}

/**
 * Aggiunta di un widget: numero nuovo, permesso di Android (la prima volta per ogni app), schermata di impostazione
 * se il widget ne ha una; alla fine [onAdded] col numero. Se qualcosa si annulla il numero si libera.
 */
@Composable
fun rememberWidgetAdder(onAdded: (Int) -> Unit): (AppWidgetProviderInfo) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (id, _) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode == Activity.RESULT_OK) onAdded(id) else AppWidgets.delete(context, id)
    }
    fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        val activity = info.configure
        if (activity == null) { pending = null; onAdded(id); return }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(activity).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Alcune schermate di impostazione non si aprono da fuori: allora il widget si mette così com'è.
        if (runCatching { configure.launch(intent) }.isFailure) { pending = null; onAdded(id) }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (id, info) = pending ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) configureOrAdd(id, info) else { pending = null; AppWidgets.delete(context, id) }
    }
    return { info ->
        val id = AppWidgets.host(context).allocateAppWidgetId()
        pending = id to info
        val allowed = runCatching { AppWidgets.manager(context).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (allowed) configureOrAdd(id, info)
        else {
            val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            if (runCatching { bind.launch(ask) }.isFailure) { pending = null; AppWidgets.delete(context, id) }
        }
    }
}

/** La scheda "Widget delle app" del catalogo: per ogni app i suoi widget con anteprima e misura. */
internal fun LazyListScope.widgetCatalog(onWidget: (AppWidgetProviderInfo) -> Unit) {
    item(key = "widgets") {
        val context = LocalContext.current
        val groups by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>?>(null) { value = withContext(Dispatchers.IO) { AppWidgets.providers(context) } }
        val scheme = MaterialTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val list = groups
            when {
                list == null -> Text(stringResource(R.string.home_widget_loading), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                list.isEmpty() -> Text(stringResource(R.string.home_widget_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                else -> list.forEach { (app, widgets) ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        widgets.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { info -> WidgetChoice(info, Modifier.weight(1f)) { onWidget(info) } }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(EuriseIcons.lock(), null, modifier = Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
                Text(stringResource(R.string.home_widget_privacy), fontSize = 12.sp, color = scheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val preview by produceState<android.graphics.Bitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching { (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.toBitmap(maxWidth = 360, maxHeight = 240) }.getOrNull()
        }
    }
    val cells = "${((info.minWidth / density + 30) / 70).toInt().coerceAtLeast(1)} × ${((info.minHeight / density + 30) / 70).toInt().coerceAtLeast(1)}"
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            preview?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)) }
        }
        Text(info.loadLabel(context.packageManager), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(cells, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Un widget dentro una bolla (anteprima WidBolla): titolo con il nome dell'app, angoli e fondo della bolla, dentro il
 * widget com'è. La misura della bolla passa al widget, che si adatta. Se l'app è stata tolta, lo dice e si può togliere.
 */
@Composable
fun WidgetBubble(id: Int, hazeState: HazeState, modifier: Modifier, onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    val info = remember(id) { AppWidgets.info(context, id) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val host = AppWidgets.host(context)
        runCatching { host.startListening() }
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) runCatching { host.startListening() }
            if (e == Lifecycle.Event.ON_STOP) runCatching { host.stopListening() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val title = info?.let { i ->
        runCatching { context.packageManager.let { pm -> pm.getApplicationLabel(pm.getApplicationInfo(i.provider.packageName, 0)).toString() } }.getOrNull()
    } ?: stringResource(R.string.home_widget)
    val openLabel = stringResource(R.string.home_widget_open)
    val trailing: (@Composable () -> Unit)? = if (info == null) null else {
        { SmallRound(EuriseIcons.chevronRight(2f), openLabel) { onOpenApp(info.provider.packageName) } }
    }
    Pill(hazeState, modifier, title = title, trailing = trailing) {
        if (info == null) {
            Text(stringResource(R.string.home_widget_gone), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            val density = LocalDensity.current
            var view by remember { mutableStateOf<AppWidgetHostView?>(null) }
            AndroidView(
                factory = { ctx -> AppWidgets.host(ctx).createView(ctx.applicationContext, id, info).also { view = it } },
                modifier = Modifier.fillMaxWidth().height(maxOf(info.minHeight / density.density, 72f).dp.coerceAtLeast(72.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .onSizeChanged { size ->
                        val w = size.width / density.density
                        val h = size.height / density.density
                        view?.let { v ->
                            if (android.os.Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                            else @Suppress("DEPRECATION") v.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
                        }
                    },
            )
        }
    }
}
