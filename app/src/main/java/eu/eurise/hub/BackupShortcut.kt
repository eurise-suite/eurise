package eu.eurise.hub

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * «Backup» tra le scorciatoie dell'icona di Eurise (pressione lunga sull'icona, in qualsiasi launcher): apre
 * Cloud › Backup. Dinamica perché il nome del pacchetto cambia tra la versione di prova e quella ufficiale.
 * Si pubblica fuori dal thread principale; se il launcher non le gestisce, non succede nulla.
 */
internal object BackupShortcut {
    private const val ID = "backup"

    fun publish(context: Context) {
        runCatching {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (manager.dynamicShortcuts.any { it.id == ID }) return
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_SECTION, "backup")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val shortcut = ShortcutInfo.Builder(context, ID)
                .setShortLabel(context.getString(R.string.app_backup))
                .setLongLabel(context.getString(R.string.shortcut_backup_long))
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_backup_app))
                .setIntent(intent)
                .build()
            manager.addDynamicShortcuts(listOf(shortcut))
        }
    }
}
