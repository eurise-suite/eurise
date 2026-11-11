package eu.eurise.feature.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/** Un tema di icone installato sul telefono (pacchetto di icone per launcher). */
data class IconTheme(val packageName: String, val label: String)

/**
 * Temi delle icone: i tondi della suite, le icone originali di sistema oppure un pacchetto di icone installato.
 * I pacchetti seguono il formato comune ai launcher (ADW, Nova, Lawnchair…): un `appfilter.xml` che lega ogni
 * attività al nome di un disegno del pacchetto. Le app che il tema non copre tengono l'icona originale.
 */
object IconThemes {
    const val EURISE = "eurise"
    /** Stessi simboli della suite su un tondo chiaro nella tinta dell'app: per chi non vuole le icone scure. */
    const val EURISE_COLOR = "eurise_color"

    /** Vero per i due temi della suite (simboli nel tondo). */
    fun isSuite(theme: String) = theme == EURISE || theme == EURISE_COLOR
    const val SYSTEM = "system"

    /** Le azioni con cui i pacchetti di icone si fanno trovare dai launcher. */
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "app.lawnchair.icons.THEMED_ICON",
    )

    /** I pacchetti di icone installati, per nome. */
    fun installed(context: Context): List<IconTheme> {
        val pm = context.packageManager
        return actions.flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != context.packageName }
            .map { IconTheme(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    private class Pack(val resources: Resources, val packageName: String, val map: Map<String, String>)

    private val packs = ConcurrentHashMap<String, Pack>()

    private fun pack(context: Context, packageName: String): Pack? = packs[packageName] ?: runCatching {
        val res = context.packageManager.getResourcesForApplication(packageName)
        Pack(res, packageName, readFilter(res, packageName)).also { packs[packageName] = it }
    }.getOrNull()

    /** Legge `appfilter.xml` (tra le risorse xml o negli assets): componente → nome del disegno. */
    private fun readFilter(res: Resources, packageName: String): Map<String, String> {
        val id = res.getIdentifier("appfilter", "xml", packageName)
        val parser: XmlPullParser = if (id != 0) res.getXml(id) else {
            val stream = runCatching { res.assets.open("appfilter.xml") }.getOrNull() ?: return emptyMap()
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
        }
        return parseFilter(parser)
    }

    internal fun parseFilter(parser: XmlPullParser): Map<String, String> {
        val out = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                componentKey(component)?.let { key -> if (!drawable.isNullOrBlank()) out.putIfAbsent(key, drawable) }
            }
            event = parser.next()
        }
        return out
    }

    /** `ComponentInfo{pacchetto/classe}` → `pacchetto/classe`, con la classe sempre scritta per intero. */
    internal fun componentKey(raw: String?): String? {
        val inner = raw?.substringAfter('{', "")?.substringBefore('}')?.trim().orEmpty()
        val pkg = inner.substringBefore('/', "").takeIf { it.isNotEmpty() } ?: return null
        val cls = inner.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: return null
        return "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
    }

    /** Il disegno del tema [packageName] per [component]; nullo se il tema non lo copre. */
    fun drawable(context: Context, packageName: String, component: ComponentName): Drawable? {
        val pack = pack(context, packageName) ?: return null
        val name = pack.map["${component.packageName}/${component.className}"] ?: return null
        val id = pack.resources.getIdentifier(name, "drawable", pack.packageName).takeIf { it != 0 }
            ?: pack.resources.getIdentifier(name, "mipmap", pack.packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { pack.resources.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null) }.getOrNull()
    }

    /** Vero se il pacchetto del tema è ancora installato. */
    fun exists(context: Context, theme: String): Boolean = theme == EURISE || theme == SYSTEM ||
        runCatching { context.packageManager.getApplicationInfo(theme, 0); true }.getOrDefault(false)
}
