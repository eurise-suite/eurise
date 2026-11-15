package eu.eurise.feature.ponte

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import eu.eurise.core.ponte.FileParts
import java.io.File

/**
 * Ciò che arriva dal computer e va oltre gli appunti (Firefox con l'estensione Eurise, 15 novembre): documenti,
 * appuntamenti e richieste di password. Ognuno mostra una notifica che dice cosa è arrivato.
 */
internal object PonteInbox {
    private const val CHANNEL = "ponte-arrivi"
    /** Lo stesso ricevitore della Tastiera nell'app Eurise (KeyboardBridgeReceiver): l'evento va nel Calendario. */
    const val ACTION_EVENT = "eu.eurise.action.PONTE_EVENT"

    private fun channel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ponte_channel_arrivals), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Un pezzo di file; a file completo lo salva in Download/Eurise e avvisa. Restituisce il nome quando è completo. */
    fun filePart(context: Context, fields: Map<String, String>): String? {
        val done = FileParts.Inbox(File(context.cacheDir, "ponte-file")).add(fields) ?: return null
        val uri = save(context, done) ?: return null
        channel(context)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, done.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(context, uri.hashCode(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ponte_file_title))
            .setContentText(done.name)
            .setSubText(context.getString(R.string.ponte_file_where))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.ponte_file_open), open).build())
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(done.name.hashCode(), n) }
        return done.name
    }

    /** In Download/Eurise (da Android 10 senza permessi); prima, nella cartella dell'app nei file esterni. */
    private fun save(context: Context, done: FileParts.Done): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, done.name)
                put(MediaStore.Downloads.MIME_TYPE, done.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Eurise")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)!!.use { it.write(done.bytes) }
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Eurise").apply { mkdirs() }
            val f = File(dir, done.name).apply { writeBytes(done.bytes) }
            Uri.fromFile(f)
        }
    }.getOrNull()

    /** Un appuntamento: lo crea l'app Eurise nel Calendario (KeyboardBridgeReceiver), che poi lo dice con un avviso. */
    fun event(context: Context, fields: Map<String, String>): String? {
        val title = fields["title"]?.takeIf { it.isNotBlank() } ?: return null
        val start = fields["start"]?.takeIf { it.isNotBlank() } ?: return null
        val i = Intent(ACTION_EVENT).setPackage(context.packageName)
            .putExtra("title", title.take(200)).putExtra("start", start)
        listOf("end", "place", "notes", "url").forEach { k -> fields[k]?.takeIf { it.isNotBlank() }?.let { i.putExtra(k, it.take(2000)) } }
        context.sendBroadcast(i)
        return title
    }

    /** «Detta dal telefono» da Firefox: una notifica in evidenza; toccandola [PonteDictate] ascolta subito. */
    fun dictationAsk(context: Context, computerId: String, computerName: String, ask: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NOW, context.getString(R.string.ponte_channel_now), NotificationManager.IMPORTANCE_HIGH))
        val i = Intent(context, PonteDictate::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PonteDictate.EXTRA_ASK, ask).putExtra(PonteDictate.EXTRA_COMPUTER, computerId)
        val tap = PendingIntent.getActivity(context, ("d" + ask).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL_NOW)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ponte_dict_title))
            .setContentText(context.getString(R.string.ponte_dict_text, computerName))
            .setContentIntent(tap)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.ponte_dict_speak), tap).build())
            .setTimeoutAfter(120_000)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(DICT_ID, n) }
    }

    private const val CHANNEL_NOW = "ponte-subito"
    private const val DICT_ID = 4711

    /**
     * Il computer chiede un accesso per [site]: una notifica, e toccandola [PonteApprove] mostra gli accessi di Eurise
     * Password (sbloccata con l'impronta) da mandare al computer. Niente parte senza il tocco sul telefono.
     */
    fun passwordAsk(context: Context, computerId: String, computerName: String, fields: Map<String, String>): String? {
        val site = fields["site"]?.takeIf { it.isNotBlank() }?.take(200) ?: return null
        val ask = fields["ask"]?.takeIf { it.isNotBlank() } ?: return null
        channel(context)
        val i = Intent(context, PonteApprove::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PonteApprove.EXTRA_SITE, site).putExtra(PonteApprove.EXTRA_ASK, ask).putExtra(PonteApprove.EXTRA_COMPUTER, computerId)
        val tap = PendingIntent.getActivity(context, ask.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ponte_pwd_title, site))
            .setContentText(context.getString(R.string.ponte_pwd_text, computerName))
            .setContentIntent(tap)
            .setTimeoutAfter(120_000)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(ask.hashCode(), n) }
        return site
    }
}
