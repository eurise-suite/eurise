package eu.eurise.feature.assistant

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Il riquadro di Alfred nella tendina (tavola AlfAvvio): il servizio sta nell'app Eurise, accanto ad AlfredActivity;
 * qui si accende, si spegne e, da Android 13, si propone di aggiungerlo con un tocco.
 */
internal object AlfredTile {
    private const val SERVICE = "eu.eurise.hub.alfred.AlfredTileService"

    private fun component(context: Context) = ComponentName(context.packageName, SERVICE)

    fun enabled(context: Context): Boolean = runCatching {
        context.packageManager.getComponentEnabledSetting(component(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(true)

    fun setEnabled(context: Context, on: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Chiede ad Android di mettere il riquadro tra quelli visibili (una volta; prima di Android 13 lo si sposta a mano). */
    fun offer(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                component(context), context.getString(R.string.alfred_name), Icon.createWithResource(context, R.drawable.ic_alfred_tile),
                context.mainExecutor,
            ) {}
        }
    }
}
