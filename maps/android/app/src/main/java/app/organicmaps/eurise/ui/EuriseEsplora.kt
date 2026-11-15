package app.organicmaps.eurise.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.search.SearchActivity
import app.organicmaps.util.ThemeUtils

/**
 * Le scorciatoie in alto mentre si esplora (tavola MapEsplora): Benzinai (col prezzo, [EuriseFuelActivity]), Farmacie,
 * Mangiare, Negozi (ricerca per categoria del motore, sul telefono) e Professionisti (presto con QuiAccanto). Stanno sopra
 * l'orizzonte, nella schermata normale della mappa (layout `map_buttons_layout_regular`, che CoMaps usa solo esplorando).
 */
object EuriseEsplora {
    @JvmStatic
    fun attach(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Chips(ThemeUtils.isNightTheme()) }
    }

    internal fun search(context: Context, res: Int) {
        val activity = context as? Activity ?: return
        runCatching { SearchActivity.start(activity, categoryQuery(context, res)) }
    }
}

private class Shortcut(val label: Int, val action: (Context) -> Unit, val soon: Boolean = false)

private val SHORTCUTS = listOf(
    Shortcut(R.string.eurise_ex_fuel, { EuriseFuelActivity.open(it) }),
    Shortcut(R.string.eurise_ex_pharmacy, { EuriseEsplora.search(it, R.string.category_pharmacy) }),
    Shortcut(R.string.eurise_ex_eat, { EuriseEsplora.search(it, R.string.category_eat) }),
    Shortcut(R.string.eurise_ex_shops, { EuriseEsplora.search(it, R.string.category_shopping) }),
    Shortcut(R.string.eurise_ex_pros, {
        Toast.makeText(it, R.string.eurise_ex_pros_soon, Toast.LENGTH_SHORT).show()
    }, soon = true),
)

@Composable
private fun Chips(night: Boolean) {
    val context = LocalContext.current
    val ink = if (night) Color(0xFFE6E8EE) else Color(0xFF1F2A44)
    val glass = if (night) Color(0xF01C2333) else Color(0xF0FFFFFF)
    val soon = if (night) Color(0xFFB9A8E0) else Color(0xFF4B3A73)
    val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LazyRow(
        Modifier.fillMaxWidth(),
        // In orizzontale la barra sta in colonna a sinistra: le scorciatoie partono dopo.
        contentPadding = PaddingValues(start = if (land) 96.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(SHORTCUTS, key = { it.label }) { s ->
            Box(
                Modifier.height(34.dp).shadow(6.dp, RoundedCornerShape(17.dp), clip = false).clip(RoundedCornerShape(17.dp))
                    .background(glass).clickable { s.action(context) }.semantics { role = Role.Button }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(s.label),
                    style = TextStyle(color = if (s.soon) soon else ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
