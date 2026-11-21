package eu.eurise.chat

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * La posizione dal vivo che si sta condividendo (tavola ChatPosizione): una alla volta, ricordata sul telefono finché
 * dura. Con la Chat aperta su quell'account i punti li manda lei ([sink], che tiene le chiavi); se no il servizio.
 */
object LiveShare {
    data class Active(
        val account: String, val roomId: String, val roomName: String, val infoId: String,
        val endsAt: Long, val arrival: Boolean, val encrypted: Boolean,
    )

    /** La Chat aperta: manda un punto per la stanza dell'account, e dice se l'ha preso lei. */
    @Volatile var sink: ((account: String, roomId: String, content: JSONObject) -> Boolean)? = null

    private fun prefs(c: Context) = c.getSharedPreferences("chat_live", Context.MODE_PRIVATE)

    fun active(c: Context): Active? = runCatching {
        val o = JSONObject(prefs(c).getString("active", null) ?: return null)
        Active(o.getString("account"), o.getString("room"), o.optString("name"), o.getString("info"), o.getLong("ends"),
            o.optBoolean("arrival"), o.optBoolean("enc"))
    }.getOrNull()

    fun start(c: Context, a: Active) {
        prefs(c).edit().putString("active", JSONObject().put("account", a.account).put("room", a.roomId).put("name", a.roomName)
            .put("info", a.infoId).put("ends", a.endsAt).put("arrival", a.arrival).put("enc", a.encrypted).toString()).apply()
        ContextCompat.startForegroundService(c, Intent(c, LiveLocationService::class.java))
    }

    fun clear(c: Context) { prefs(c).edit().remove("active").apply() }

    /** Smetti: il servizio manda «non più dal vivo» e si ferma. */
    fun stop(c: Context) { c.startService(Intent(c, LiveLocationService::class.java).setAction(LiveLocationService.ACTION_STOP)) }
}

/**
 * Il servizio in primo piano della posizione dal vivo: l'avviso fisso «Stai condividendo la posizione con Giulia · fino
 * alle 23:00 · Smetti», il GPS del telefono (nessun servizio di Google), un punto ogni 20 secondi al massimo, la fine a
 * tempo o all'arrivo.
 */
class LiveLocationService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var active: LiveShare.Active? = null
    private var lastSent = 0L
    private var arrival = Posizione.Arrival()
    private var crypto: ChatCrypto? = null
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = LiveShare.active(this)
        if (a == null) { stopSelf(); return START_NOT_STICKY }
        val n = notification(a)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(ID, n)
        if (intent?.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        if (active?.infoId != a.infoId) {
            active = a
            arrival = Posizione.Arrival()
            val lm = getSystemService(LocationManager::class.java)
            runCatching { lm.removeUpdates(this) }
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                .forEach { p -> runCatching { lm.requestLocationUpdates(p, 15_000L, 10f, this, Looper.getMainLooper()) } }
            main.removeCallbacksAndMessages(null)
            tick()
        }
        return START_STICKY
    }

    /** Ogni 30 secondi: è finito il tempo? */
    private fun tick() {
        val a = active ?: return
        if (System.currentTimeMillis() >= a.endsAt) { finish(); return }
        main.postDelayed({ tick() }, 30_000)
    }

    override fun onLocationChanged(location: Location) {
        val a = active ?: return
        val p = Posizione.Pos(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toDouble() else null, System.currentTimeMillis())
        if (a.arrival && arrival.feed(p)) { send(a, p); finish(); return }
        if (p.ts - lastSent < 20_000) return
        lastSent = p.ts
        send(a, p)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun send(a: LiveShare.Active, p: Posizione.Pos) {
        val content = Posizione.beaconContent(a.infoId, p)
        if (LiveShare.sink?.invoke(a.account, a.roomId, content) == true) return
        scope.launch {
            runCatching {
                val account = Account.valueOf(a.account)
                val s = SessionStore(this@LiveLocationService, account).load() ?: return@runCatching
                val c = ChatClient(s)
                if (a.encrypted) {
                    val cr = crypto ?: ChatCrypto(this@LiveLocationService, s).also { crypto = it }
                    c.sendEncrypted(a.roomId, cr.encrypt(c, a.roomId, c.joinedMembers(a.roomId), Posizione.BEACON, content))
                } else c.sendEvent(a.roomId, Posizione.BEACON, content)
            }
        }
    }

    /** Fine: «non più dal vivo» nella stanza (lo stato non è cifrato e non porta posizioni), poi via l'avviso. */
    private fun finish() {
        if (finishing) return
        finishing = true
        val a = active ?: LiveShare.active(this)
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        main.removeCallbacksAndMessages(null)
        LiveShare.clear(this)
        active = null
        scope.launch {
            if (a != null) runCatching {
                val s = SessionStore(this@LiveLocationService, Account.valueOf(a.account)).load() ?: return@runCatching
                val c = ChatClient(s)
                val now = System.currentTimeMillis()
                c.setState(a.roomId, Posizione.INFO, Posizione.infoContent(a.roomName, now, 0, false), s.userId)
            }
            main.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(a: LiveShare.Active): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.lo_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(this, 1, Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val until = if (a.arrival) getString(R.string.lo_until_arrival) else getString(R.string.lo_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(a.endsAt)))
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(getString(R.string.lo_sharing, a.roomName))
            .setContentText(until)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.lo_stop), stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "eu.eurise.chat.LIVE_STOP"
        private const val CHANNEL = "chat_live"
        private const val ID = 4711
    }
}
