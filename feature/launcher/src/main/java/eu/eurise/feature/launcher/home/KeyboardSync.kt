package eu.eurise.feature.launcher.home

import android.content.Context
import android.content.Intent

/**
 * Dice a Eurise Tastiera se le bolle della Scrivania sono scure, così la tastiera prende lo stesso tema.
 * Va solo al pacchetto della tastiera e solo ad app con la stessa firma (permesso della suite).
 */
internal fun Context.tellKeyboardTheme(dark: Boolean) {
    val debug = packageName.endsWith(".debug")
    val keyboard = if (debug) "eu.eurise.keyboard.debug" else "eu.eurise.keyboard"
    val permission = if (debug) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"
    runCatching {
        sendBroadcast(
            Intent("eu.eurise.action.HOME_THEME").setPackage(keyboard).putExtra("dark", dark),
            permission,
        )
    }
}
