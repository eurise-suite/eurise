package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.settings.Accent
import eu.eurise.feature.launcher.settings.ExtraTint
import eu.eurise.core.suite.AppearanceContract as C

/**
 * L'aspetto della suite per le altre app ([C]): l'accento scelto sulla Scrivania e le tinte tra cui scegliere (le
 * sette della suite senza Grafite e quelle in più della Scrivania). Lo usa Eurise Office per le sue icone. Solo app con
 * la stessa firma: il controllo è qui, `call` non guarda il manifesto.
 */
class AppearanceProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        return when (method) {
            C.METHOD_ACCENT -> accent(context)
            C.METHOD_PALETTE -> palette()
            C.METHOD_HAND -> Bundle().apply {
                putBoolean(C.KEY_LEFT, context.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE).getBoolean("left_handed", false))
            }
            else -> null
        }
    }

    private fun accent(context: Context): Bundle {
        val raw = context.getSharedPreferences("eurise_launcher", Context.MODE_PRIVATE).getString("accent", null)
        val accent = Accent.decode(raw)
        return Bundle().apply {
            putInt(C.KEY_LIGHT, accent.color(false).toArgb())
            putInt(C.KEY_DARK, accent.color(true).toArgb())
            putString(C.KEY_NAME, accent.encode())
        }
    }

    private fun palette(): Bundle {
        val suite = CalendarTint.entries.filter { it != CalendarTint.Grafite }.map { Accent.Suite(it) to it.name }
        val extra = ExtraTint.entries.map { Accent.Extra(it) to it.name }
        val all = suite + extra
        return Bundle().apply {
            putStringArray(C.KEY_NAMES, all.map { it.first.encode() }.toTypedArray())
            putStringArray(C.KEY_LABELS, all.map { label(it.second) }.toTypedArray())
            putIntArray(C.KEY_LIGHTS, all.map { it.first.color(false).toArgb() }.toIntArray())
            putIntArray(C.KEY_DARKS, all.map { it.first.color(true).toArgb() }.toIntArray())
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        val PERMISSIONS = listOf("eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG")

        /** «BluNotte» → «Blu notte», «AzzurroCostiera» → «Azzurro costiera». */
        fun label(name: String): String =
            name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}
