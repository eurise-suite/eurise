package eu.eurise.postazione.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

/** Un'app che si può aprire: [chiave] è `pacchetto/attività`, come la salvano i gruppi. */
data class App(val chiave: String, val nome: String) {
    val pacchetto get() = chiave.substringBefore('/')
    val attivita get() = chiave.substringAfter('/')
    val iniziale: Char get() = nome.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
}

/** Le app del telefono e le loro icone, solo con le API di Android (nessuna lettura di dati delle app). */
class Telefono(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val icone = LruCache<String, ImageBitmap>(160)

    fun app(): List<App> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.ITALIAN).apply { strength = Collator.PRIMARY }
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { App("${it.activityInfo.packageName}/${it.activityInfo.name}", it.loadLabel(pm).toString()) }
            .distinctBy { it.chiave }
            .sortedWith { a, b -> collator.compare(a.nome, b.nome) }
    }

    /** Il pacchetto che il telefono usa per ogni compito, per i gruppi quando l'app della suite manca. */
    fun ruoli(): Map<String, String> {
        val intenti = mapOf(
            "dialer" to Intent(Intent.ACTION_DIAL),
            "sms" to Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            "email" to Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
            "browser" to Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            "maps" to Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
            "camera" to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            "music" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
        )
        return intenti.mapNotNull { (ruolo, intent) ->
            val pkg = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
            // «android» è la finestra di scelta: nessuna app predefinita per quel compito.
            pkg?.takeIf { it != "android" }?.let { ruolo to it }
        }.toMap()
    }

    fun icona(chiave: String, px: Int): ImageBitmap? {
        icone.get("$chiave@$px")?.let { return it }
        val cn = ComponentName.unflattenFromString(chiave) ?: return null
        val bmp = runCatching { pm.getActivityIcon(cn).toBitmap(px, px).asImageBitmap() }.getOrNull() ?: return null
        icone.put("$chiave@$px", bmp)
        return bmp
    }

    fun apri(chiave: String): Boolean {
        val cn = ComponentName.unflattenFromString(chiave) ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun avvia(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    fun avviaPacchetto(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)

    /** Prima l'app della suite (attività esatta), se c'è; altrimenti [riserva]. */
    fun apriSuiteO(hub: String, attivita: String, riserva: Intent?): Boolean {
        val chiave = "$hub/$attivita"
        val c = ComponentName.unflattenFromString(chiave)
        val c2 = c?.let { runCatching { pm.getActivityInfo(it, 0) }.getOrNull() }
        if (c2 != null && apri(chiave)) return true
        return riserva != null && avvia(riserva)
    }
}
