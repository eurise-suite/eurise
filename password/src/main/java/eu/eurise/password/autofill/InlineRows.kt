package eu.eurise.password.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import eu.eurise.password.R

/**
 * Le righe in linea per la tastiera (tavola TastPassword): un account con la chiave accanto, nella fila sopra i tasti.
 * Il disegno lo fa la tastiera con il suo stile; qui si dice solo cosa scrivere. Serve `androidx.autofill`, che dà il
 * formato che il sistema sa disegnare.
 */
@RequiresApi(Build.VERSION_CODES.R)
object InlineRows {
    fun presentation(context: Context, spec: InlinePresentationSpec, title: String, subtitle: String?): InlinePresentation? {
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        // Pressione lunga sulla proposta: apre Password.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val attribution = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_tile_key))
            .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
            .build()
        return InlinePresentation(content.slice, spec, false)
    }
}
