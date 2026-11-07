package eu.eurise.feature.launcher.feeds

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.settings.ExtraTint
import eu.eurise.feature.launcher.summary.CardSize
import eu.eurise.feature.launcher.summary.SummaryCard

/** I dodici segni, con il colore della suite secondo l'elemento (fuoco, terra, aria, acqua). */
enum class Sign(@StringRes val label: Int, val light: Color, val dark: Color) {
    Ariete(R.string.sign_ariete, CalendarTint.Terracotta.light, CalendarTint.Terracotta.dark),
    Toro(R.string.sign_toro, CalendarTint.Salvia.light, CalendarTint.Salvia.dark),
    Gemelli(R.string.sign_gemelli, ExtraTint.AzzurroCostiera.light, ExtraTint.AzzurroCostiera.dark),
    Cancro(R.string.sign_cancro, CalendarTint.Petrolio.light, CalendarTint.Petrolio.dark),
    Leone(R.string.sign_leone, ExtraTint.Arancio.light, ExtraTint.Arancio.dark),
    Vergine(R.string.sign_vergine, ExtraTint.Cuoio.light, ExtraTint.Cuoio.dark),
    Bilancia(R.string.sign_bilancia, ExtraTint.Viola.light, ExtraTint.Viola.dark),
    Scorpione(R.string.sign_scorpione, CalendarTint.Prugna.light, CalendarTint.Prugna.dark),
    Sagittario(R.string.sign_sagittario, CalendarTint.Borgogna.light, CalendarTint.Borgogna.dark),
    Capricorno(R.string.sign_capricorno, ExtraTint.VerdeInglese.light, ExtraTint.VerdeInglese.dark),
    Acquario(R.string.sign_acquario, CalendarTint.BluNotte.light, CalendarTint.BluNotte.dark),
    Pesci(R.string.sign_pesci, CalendarTint.Petrolio.light, CalendarTint.Petrolio.dark);

    fun color(dark: Boolean) = if (dark) this.dark else light
}

/** Oroscopo: Paolo Fox, l'astrologo più seguito in Italia, dalle testate che pubblicano il suo oroscopo del giorno. */
val HoroscopeSources = listOf(
    FeedSource("sussidiario", "Paolo Fox · Il Sussidiario", "https://www.ilsussidiario.net/feed/"),
    FeedSource("leggo", "Oroscopo · Leggo", "https://www.leggo.it/rss/oroscopo.xml"),
    FeedSource("ilmessaggero", "Oroscopo · Il Messaggero", "https://www.ilmessaggero.it/rss/oroscopo.xml"),
)

/** Ricette: i siti di cucina italiani più seguiti. */
val RecipeSources = listOf(
    FeedSource("giallozafferano", "GialloZafferano", "https://www.giallozafferano.it/feed"),
    FeedSource("cucchiaio", "Il Cucchiaio d'Argento", "https://www.cucchiaio.it/rss/"),
    FeedSource("cookist", "Cookist", "https://www.cookist.it/feed/"),
    FeedSource("misya", "Misya", "https://www.misya.info/feed"),
)

class DailyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_daily", Context.MODE_PRIVATE)
    val horoscope = FeedSources(context, "horoscope", HoroscopeSources, setOf("sussidiario", "leggo", "ilmessaggero"))
    val recipes = FeedSources(context, "recipes", RecipeSources, setOf("giallozafferano", "cucchiaio", "cookist"))

    var sign: Sign
        get() = Sign.entries.getOrNull(prefs.getInt("sign", Sign.Leone.ordinal)) ?: Sign.Leone
        set(value) { prefs.edit().putInt("sign", value.ordinal).apply() }

    /** L'oroscopo di oggi: la voce più recente che parla di oroscopo, con il passaggio sul segno scelto. */
    suspend fun horoscopeFor(sign: Sign, name: String, force: Boolean = false): FeedEntry? {
        val entries = horoscope.entries(force).filter { it.title.contains("oroscopo", ignoreCase = true) || it.title.contains("Paolo Fox", ignoreCase = true) }
        val entry = entries.firstOrNull { it.title.contains(name, ignoreCase = true) } ?: entries.firstOrNull() ?: return null
        val at = entry.text.indexOf(name, ignoreCase = true)
        val excerpt = if (at >= 0) entry.text.substring(at).take(420) else entry.text.take(420)
        return entry.copy(text = excerpt)
    }

    /** Una ricetta per sito, la più recente di ciascuno. */
    suspend fun recipesOfDay(force: Boolean = false): List<FeedEntry> =
        recipes.entries(force).groupBy { it.source }.mapNotNull { it.value.firstOrNull() }.take(3)
}

@Composable
fun HoroscopeCard(
    entry: FeedEntry?,
    loaded: Boolean,
    sign: Sign,
    dark: Boolean,
    hazeState: HazeState,
    size: CardSize,
    modifier: Modifier,
    onSign: () -> Unit,
    onSources: () -> Unit,
    onOpen: (FeedEntry) -> Unit,
) {
    SummaryCard(stringResource(R.string.card_horoscope), hazeState, modifier, action = stringResource(R.string.news_sources), onAction = onSources) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onSign).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignBadge(sign, dark, 30)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(sign.label), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = sign.color(dark))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.horoscope_change), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            !loaded -> Text(stringResource(R.string.news_loading), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry == null -> Text(stringResource(R.string.horoscope_empty), fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpen(entry) }) {
                Text(entry.text, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = if (size.tall) 12 else 3, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.source + " · " + stringResource(R.string.horoscope_read), fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun SignBadge(sign: Sign, dark: Boolean, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (dark) Color(0xFF262B33) else Color(0xFF1F2A44)),
        contentAlignment = Alignment.Center,
    ) { Icon(EuriseIcons.zodiac(sign.ordinal, 2f), null, tint = sign.dark, modifier = Modifier.size((size * 0.55f).dp)) }
}

/** Scelta del segno: i dodici simboli della suite nei loro colori. */
@Composable
fun SignPicker(current: Sign, dark: Boolean, onPick: (Sign) -> Unit, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.horoscope_pick), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Sign.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { sign ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) { onPick(sign) }
                                .then(if (sign == current) Modifier.border(2.dp, sign.color(dark), RoundedCornerShape(14.dp)) else Modifier)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SignBadge(sign, dark, 44)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(sign.label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Ricetta del giorno: una per sito, si scorre di lato. Foto, titolo e inizio della descrizione. */
@Composable
fun RecipeCard(
    recipes: List<FeedEntry>?,
    hazeState: HazeState,
    size: CardSize,
    modifier: Modifier,
    onSources: () -> Unit,
    onOpen: (FeedEntry) -> Unit,
) {
    SummaryCard(stringResource(R.string.card_recipe), hazeState, modifier, action = stringResource(R.string.news_sources), onAction = onSources) {
        when {
            recipes == null -> Text(stringResource(R.string.news_loading), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recipes.isEmpty() -> Text(stringResource(R.string.recipe_empty), fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val pager = rememberPagerState { recipes.size }
                HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { page ->
                    val r = recipes[page]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(r) }) {
                        if (r.image != null && (size.tall || !size.half)) {
                            var bitmap by remember(r.image) { mutableStateOf<ImageBitmap?>(null) }
                            LaunchedEffect(r.image) { bitmap = FeedReader.image(r.image) }
                            Box(
                                Modifier.size(if (size.tall) 92.dp else 64.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            ) { bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(r.source, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (size.tall) {
                                Text(r.text, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (recipes.size > 1) {
                    Text(
                        "${pager.currentPage + 1}/${recipes.size}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
