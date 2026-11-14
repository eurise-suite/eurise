package eu.eurise.core.data.alfred

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore

/**
 * La musica di Alfred: il brano va all'app scelta dall'utente con l'intento standard «riproduci da ricerca» (Spotify,
 * YouTube Music, Deezer, VLC… e i lettori di musica sul telefono). Riceve solo titolo e autore.
 */
object AlfredMusic {
    private fun intent() = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)

    /** Le app che sanno riprodurre da una ricerca: pacchetto e nome. */
    fun apps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return runCatching { pm.queryIntentActivities(intent(), 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    fun play(context: Context, pearl: Pearl, pkg: String): Boolean {
        val i = intent()
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, pearl.title)
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, pearl.artist)
            .putExtra(SearchManager.QUERY, pearl.query)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }
}
