package app.organicmaps.eurise;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.app.AlarmManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import app.organicmaps.R;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cosa suona e chi chiama, per la bolla in guida (tavole MapBollaAuto, MapBollaAutoChiamata, MapBollaTelefono, approvate
 * il 16 novembre). Tutto arriva dall'accesso alle notifiche ({@link EuriseNowPlayingService}): le sessioni multimediali
 * delle app (Spotify, Musica, radio, podcast) e la notifica della chiamata del telefono. Nulla esce dal telefono; il testo
 * delle canzoni è un servizio a parte ({@link EuriseLyrics}).
 *
 * <p>Interruttore «Musica e chiamate in guida» in Livelli, spento di base: senza, anche con l'accesso dato, non si legge
 * nulla. Stato sul thread principale; chi mostra la bolla si registra con {@link #listen}.
 */
public final class EuriseNowPlaying
{
  public static final String PREF = "eurise_media_bubble";

  /** La canzone (o il podcast) che suona. Le stringhe possono mancare, tranne il titolo. */
  public static final class Media
  {
    public final String pkg;
    public final String title;
    @Nullable public final String artist, album, year, appName;
    public final long durationMs;
    public final long positionMs, updatedAt;
    public final float speed;
    public final boolean playing;
    @Nullable public final Bitmap art;
    @Nullable public final Drawable appIcon;
    /** Quando è andata in pausa (orologio di {@link SystemClock#elapsedRealtime}), 0 se suona. */
    public final long pausedAt;
    /** «Titolo · Autore» del brano dopo, dalla coda che l'app condivide (null se non la condivide). */
    @Nullable public String next;
    /** Numero della traccia e quante sono (capitoli di un audiolibro, puntate), 0 se l'app non li dà. */
    public long track, tracks;
    /** Podcast o audiolibro (riga d'arrivo); radio o diretta (senza durata). */
    public boolean spoken, live;

    Media(String pkg, String title, @Nullable String artist, @Nullable String album, @Nullable String year,
          @Nullable String appName, long durationMs, long positionMs, long updatedAt, float speed, boolean playing,
          @Nullable Bitmap art, @Nullable Drawable appIcon, long pausedAt)
    {
      this.pkg = pkg;
      this.title = title;
      this.artist = artist;
      this.album = album;
      this.year = year;
      this.appName = appName;
      this.durationMs = durationMs;
      this.positionMs = positionMs;
      this.updatedAt = updatedAt;
      this.speed = speed;
      this.playing = playing;
      this.art = art;
      this.appIcon = appIcon;
      this.pausedAt = pausedAt;
    }

    /** Dove è la canzone adesso. */
    public long position()
    {
      return NowPlayingText.positionAt(positionMs, updatedAt, speed, playing, durationMs, SystemClock.elapsedRealtime());
    }

    /** La chiave per il testo: la stessa canzone ha lo stesso testo anche se cambia app o posizione. */
    public String key()
    {
      return (artist == null ? "" : artist) + "\u0000" + title + "\u0000" + (album == null ? "" : album);
    }
  }

  /** La chiamata in corso o in arrivo. */
  public static final class Call
  {
    public final String name;
    @Nullable public final String detail;
    /** Inizio (orologio di {@link System#currentTimeMillis}), 0 se in arrivo. */
    public final long start;
    public final boolean incoming;
    @Nullable public final Bitmap photo;
    @Nullable public final PendingIntent open;
    /** «Pranzo, oggi alle 13:00»: il prossimo impegno con questa persona nel Calendario della suite. */
    @Nullable public volatile String together;
    /** Il numero, quando la notifica lo dà (persona della chiamata con «tel:»). */
    @Nullable public String number;
    /** Già risposta almeno una volta (per distinguere la chiamata persa da quella finita). */
    boolean answered;

    Call(String name, @Nullable String detail, long start, boolean incoming, @Nullable Bitmap photo,
         @Nullable PendingIntent open)
    {
      this.name = name;
      this.detail = detail;
      this.start = start;
      this.incoming = incoming;
      this.photo = photo;
      this.open = open;
    }
  }

  /** Un messaggio arrivato in guida (tavola MapBollaMessaggio): resta solo in memoria, fino alla fine del viaggio. */
  public static final class Msg
  {
    public final String key, pkg, sender;
    @Nullable public final String text, app, group;
    public final long at;
    /** La risposta rapida della notifica (azione con campo di testo), null se l'app non la offre. */
    @Nullable public final Notification.Action reply;
    @Nullable public final PendingIntent open;

    Msg(String key, String pkg, String sender, @Nullable String text, @Nullable String app, @Nullable String group,
        long at, @Nullable Notification.Action reply, @Nullable PendingIntent open)
    {
      this.key = key;
      this.pkg = pkg;
      this.sender = sender;
      this.text = text;
      this.app = app;
      this.group = group;
      this.at = at;
      this.reply = reply;
      this.open = open;
    }
  }

  /** Un timer che scorre (notifica con il conto alla rovescia, per esempio dell'Orologio di Eurise). */
  public static final class Timer
  {
    public final String title;
    /** Quando suona (orologio di {@link System#currentTimeMillis}). */
    public final long endsAt;

    Timer(String title, long endsAt)
    {
      this.title = title;
      this.endsAt = endsAt;
    }
  }

  /** Un impegno dei prossimi minuti dall'app Eurise. */
  public static final class Event
  {
    public final String title;
    public final long start;

    Event(String title, long start)
    {
      this.title = title;
      this.start = start;
    }
  }

  /** Interruttore «Messaggi in guida» in Livelli, spento di base (legge chi scrive e il testo, solo in guida). */
  public static final String PREF_MESSAGES = "eurise_bubble_messages";

  /** App di podcast e audiolibri: la riga d'arrivo anche per le puntate brevi. */
  private static final Set<String> SPOKEN_APPS = new HashSet<>(Arrays.asList(
      "de.danoeh.antennapod", "de.ph1b.audiobook", "com.audiobookshelf.app", "au.com.shiftyjelly.pocketcasts",
      "com.bambuna.podcastaddict", "com.google.android.apps.podcasts", "fm.castbox.audiobook.radio.podcast",
      "com.podcast.podcasts", "app.grapheneos.podcasts", "org.kde.kasts", "com.github.ashutoshgngwr.noice",
      "net.programmierecke.radiodroid2.podcast"));

  private static final LinkedHashMap<String, Msg> MESSAGES = new LinkedHashMap<>();
  private static final LinkedHashMap<String, Timer> TIMERS = new LinkedHashMap<>();
  private static volatile List<Event> sEvents = new ArrayList<>();
  private static long sEventsAt;
  private static long sLastMissedAt;

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
  private static final String[] HUBS = EuriseSuite.HUBS;

  @Nullable private static Media sMedia;
  @Nullable private static Call sCall;
  @Nullable private static Context sApp;
  @Nullable private static MediaSessionManager sSessions;
  @Nullable private static ComponentName sComponent;
  @Nullable private static MediaController sController;
  private static long sCallSeen;

  private EuriseNowPlaying() {}

  // ---- Interruttore -------------------------------------------------------------------------------------------------

  /** L'interruttore di Livelli (spento di base). */
  public static boolean wanted(@NonNull Context c)
  {
    return PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF, false);
  }

  /** Acceso e con l'accesso alle notifiche dato dalla persona nelle impostazioni del telefono. */
  public static boolean enabled(@NonNull Context c)
  {
    return wanted(c) && access(c);
  }

  public static boolean access(@NonNull Context c)
  {
    return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
  }

  public static void setWanted(@NonNull Context c, boolean on)
  {
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
    p.edit().putBoolean(PREF, on).apply();
    if (!on)
      clear();
    else if (EuriseNowPlayingService.sInstance != null)
      onConnected(EuriseNowPlayingService.sInstance);
  }

  // ---- Chi mostra la bolla ------------------------------------------------------------------------------------------

  @MainThread
  public static void listen(@NonNull Runnable r)
  {
    LISTENERS.addIfAbsent(r);
  }

  @MainThread
  public static void unlisten(@NonNull Runnable r)
  {
    LISTENERS.remove(r);
  }

  @Nullable
  public static Media media()
  {
    return sMedia;
  }

  @Nullable
  public static Call call()
  {
    return sCall;
  }

  /** I messaggi arrivati in guida, dal più vecchio. */
  @NonNull
  public static List<Msg> messages()
  {
    return new ArrayList<>(MESSAGES.values());
  }

  /** Dimentica i messaggi (fine del viaggio o «Dopo» quando si sono letti). */
  public static void forgetMessages()
  {
    MESSAGES.clear();
  }

  /** Il timer che suona per primo, se c'è. */
  @Nullable
  public static Timer timer()
  {
    Timer best = null;
    final long now = System.currentTimeMillis();
    for (Timer t : TIMERS.values())
      if (t.endsAt > now && (best == null || t.endsAt < best.endsAt))
        best = t;
    return best;
  }

  /** La prossima sveglia che il telefono sa (anche di altre app), in millisecondi; 0 se nessuna. */
  public static long nextAlarm(@NonNull Context c)
  {
    final AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    final AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
    return info == null ? 0 : info.getTriggerTime();
  }

  public static boolean messagesWanted(@NonNull Context c)
  {
    return PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF_MESSAGES, false);
  }

  public static void setMessagesWanted(@NonNull Context c, boolean on)
  {
    PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(PREF_MESSAGES, on).apply();
    if (!on)
      forgetMessages();
  }

  /**
   * Gli impegni delle prossime ore dall'app Eurise (anche senza luogo), letti al massimo ogni 3 minuti fuori dal
   * thread principale; intanto torna l'ultima lettura.
   */
  @NonNull
  public static List<Event> events(@NonNull Context c)
  {
    final long now = SystemClock.elapsedRealtime();
    if (sEventsAt == 0 || now - sEventsAt > 3 * 60_000L)
    {
      sEventsAt = now;
      final Context app = c.getApplicationContext();
      IO.execute(() -> {
        final List<Event> list = readEvents(app);
        if (list != null)
          MAIN.post(() -> {
            sEvents = list;
            // Arrivati gli impegni durante una chiamata: si guarda subito se chi chiama ti aspetta.
            if (sCall != null)
              EuriseTrip.onCall(app, sCall);
            changed();
          });
      });
    }
    return sEvents;
  }

  /** «in vivavoce», «audio dell'auto», «al telefono»: da dove passa la voce della chiamata. */
  @Nullable
  public static String route(@NonNull Context c)
  {
    final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    if (am == null)
      return null;
    int type = -1;
    if (Build.VERSION.SDK_INT >= 31)
    {
      final AudioDeviceInfo d = am.getCommunicationDevice();
      if (d != null)
        type = d.getType();
    }
    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (type == -1 && am.isBluetoothScoOn()))
      return c.getString(R.string.eurise_np_route_car);
    if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || (type == -1 && am.isSpeakerphoneOn()))
      return c.getString(R.string.eurise_np_route_speaker);
    if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_BLE_HEADSET)
      return c.getString(R.string.eurise_np_route_headset);
    return null;
  }

  /** Qualcosa del viaggio è cambiato ({@link EuriseTrip}): la bolla si ridisegna. */
  static void poke()
  {
    changed();
  }

  private static void changed()
  {
    for (Runnable r : LISTENERS)
      r.run();
  }

  // ---- Dal servizio delle notifiche ---------------------------------------------------------------------------------

  @MainThread
  static void onConnected(@NonNull NotificationListenerService s)
  {
    sApp = s.getApplicationContext();
    if (!wanted(s))
      return;
    sComponent = new ComponentName(s, EuriseNowPlayingService.class);
    sSessions = (MediaSessionManager) s.getSystemService(Context.MEDIA_SESSION_SERVICE);
    if (sSessions != null)
    {
      try
      {
        sSessions.removeOnActiveSessionsChangedListener(SESSIONS);
        sSessions.addOnActiveSessionsChangedListener(SESSIONS, sComponent, MAIN);
        onSessions(sSessions.getActiveSessions(sComponent));
      }
      catch (SecurityException e)
      {
        // Accesso tolto nel frattempo.
        clear();
        return;
      }
    }
    try
    {
      final StatusBarNotification[] all = s.getActiveNotifications();
      if (all != null)
        for (StatusBarNotification n : all)
          onNotification(n, false);
    }
    catch (RuntimeException ignored)
    {
      // Il servizio si sta scollegando.
    }
  }

  @MainThread
  static void onDisconnected()
  {
    if (sSessions != null)
      sSessions.removeOnActiveSessionsChangedListener(SESSIONS);
    clear();
  }

  private static void clear()
  {
    if (sController != null)
      sController.unregisterCallback(CALLBACK);
    sController = null;
    sMedia = null;
    sCall = null;
    MESSAGES.clear();
    TIMERS.clear();
    changed();
  }

  @MainThread
  static void onNotification(@NonNull StatusBarNotification sbn, boolean removed)
  {
    final Context c = sApp;
    if (c == null || !wanted(c))
      return;
    final Notification n = sbn.getNotification();
    if (n == null || c.getPackageName().equals(sbn.getPackageName()))
      return;
    if ("missed_call".equals(n.category))
    {
      // Solo quelle appena arrivate: rileggendo le notifiche all'avvio non tornano le chiamate perse di ieri.
      if (!removed && System.currentTimeMillis() - sbn.getPostTime() < 2 * 60_000L)
        onMissedNotification(c, n);
      return;
    }
    if (!Notification.CATEGORY_CALL.equals(n.category))
    {
      if (onTimer(sbn, n, removed) || !messagesWanted(c))
        return;
      onMessage(c, sbn, n, removed);
      return;
    }
    if (removed)
    {
      final Call gone = sCall;
      sCall = null;
      // Squillava e nessuno ha risposto: chiamata persa (tavola MapBollaPersa).
      if (gone != null && gone.incoming && !gone.answered)
      {
        sLastMissedAt = System.currentTimeMillis();
        EuriseTrip.onMissed(c, gone.name, gone.number, gone.photo);
      }
      changed();
      EuriseTrip.onCall(c, null);
      return;
    }
    final Bundle x = n.extras;
    String name = null;
    if (Build.VERSION.SDK_INT >= 31)
    {
      @SuppressWarnings("deprecation") final Object o = x.getParcelable(Notification.EXTRA_CALL_PERSON);
      final android.app.Person p = o instanceof android.app.Person ? (android.app.Person) o : null;
      if (p != null && p.getName() != null)
        name = p.getName().toString();
    }
    final CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
    if (name == null && title != null)
      name = title.toString();
    if (name == null || name.trim().isEmpty())
      return;
    final CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
    final int type = x.getInt("android.callType", 0); // Notification.CallStyle: 1 in arrivo, 2 in corso
    final boolean chrono = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER);
    final boolean incoming = type == 1 || (type == 0 && !chrono && n.fullScreenIntent != null);
    long start = 0;
    if (!incoming)
    {
      final Call old = sCall;
      if (chrono && n.when > 0)
        start = n.when;
      else if (old != null && !old.incoming && old.name.equals(name))
        start = old.start;
      else
        start = System.currentTimeMillis();
    }
    final Bitmap photo = Build.VERSION.SDK_INT >= 23 ? bitmap(c, n.getLargeIcon(), 160) : null;
    final Call call = new Call(name.trim(), text == null ? null : text.toString(), start, incoming, photo, n.contentIntent);
    call.number = number(x);
    final Call old = sCall;
    if (old != null && old.name.equals(call.name))
    {
      call.together = old.together;
      call.answered = old.answered;
      if (call.number == null)
        call.number = old.number;
    }
    if (!incoming)
      call.answered = true;
    sCall = call;
    EuriseTrip.onCall(c, call);
    if (old == null || !old.name.equals(call.name))
    {
      final long seen = ++sCallSeen;
      final String who = call.name;
      IO.execute(() -> {
        final String t = together(c, who);
        MAIN.post(() -> {
          if (seen == sCallSeen && sCall != null && who.equals(sCall.name))
          {
            sCall.together = t;
            changed();
          }
        });
      });
    }
    changed();
  }

  /** Il numero dalla persona della notifica («tel:…»), se c'è. */
  @Nullable
  private static String number(@NonNull Bundle x)
  {
    if (Build.VERSION.SDK_INT < 28)
      return null;
    final List<android.app.Person> people = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= 31)
    {
      @SuppressWarnings("deprecation") final Object o = x.getParcelable(Notification.EXTRA_CALL_PERSON);
      if (o instanceof android.app.Person)
        people.add((android.app.Person) o);
    }
    @SuppressWarnings("deprecation") final ArrayList<?> list = x.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST);
    if (list != null)
      for (Object o : list)
        if (o instanceof android.app.Person)
          people.add((android.app.Person) o);
    for (android.app.Person p : people)
    {
      final String n = NowPlayingText.phone(p.getUri());
      if (n != null)
        return n;
    }
    return null;
  }

  /** La notifica «chiamata persa» del telefono, se non l'abbiamo già vista sparire squillando. */
  private static void onMissedNotification(@NonNull Context c, @NonNull Notification n)
  {
    if (System.currentTimeMillis() - sLastMissedAt < 3 * 60_000L)
      return;
    final Bundle x = n.extras;
    String name = null;
    if (Build.VERSION.SDK_INT >= 28)
    {
      @SuppressWarnings("deprecation") final ArrayList<?> list = x.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST);
      if (list != null && !list.isEmpty() && list.get(0) instanceof android.app.Person
          && ((android.app.Person) list.get(0)).getName() != null)
        name = ((android.app.Person) list.get(0)).getName().toString();
    }
    final CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
    final CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
    if (name == null)
      name = text != null ? text.toString() : title != null ? title.toString() : null;
    if (name == null || name.trim().isEmpty())
      return;
    sLastMissedAt = System.currentTimeMillis();
    final Bitmap photo = Build.VERSION.SDK_INT >= 23 ? bitmap(c, n.getLargeIcon(), 160) : null;
    EuriseTrip.onMissed(c, name.trim(), number(x), photo);
  }

  /** Timer con il conto alla rovescia (tavola MapBollaImpegni). Vero se la notifica era un timer. */
  private static boolean onTimer(@NonNull StatusBarNotification sbn, @NonNull Notification n, boolean removed)
  {
    final Bundle x = n.extras;
    if (removed)
    {
      if (TIMERS.remove(sbn.getKey()) != null)
      {
        changed();
        return true;
      }
      return false;
    }
    final boolean countdown = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                              && x.getBoolean("android.chronometerCountDown");
    if (!countdown || n.when <= System.currentTimeMillis())
    {
      if (TIMERS.remove(sbn.getKey()) != null)
        changed();
      return countdown;
    }
    final CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
    TIMERS.put(sbn.getKey(), new Timer(title == null ? "" : title.toString().trim(), n.when));
    changed();
    return true;
  }

  /**
   * Un messaggio (tavola MapBollaMessaggio), solo in guida e con «Messaggi in guida» acceso: chi scrive, il testo
   * (tenuto in memoria per Alfred, mai mostrato in guida) e la risposta rapida della notifica.
   */
  private static void onMessage(@NonNull Context c, @NonNull StatusBarNotification sbn, @NonNull Notification n,
                                boolean removed)
  {
    if (removed)
    {
      if (MESSAGES.remove(sbn.getKey()) != null)
        changed();
      return;
    }
    if ((n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0
        || !app.organicmaps.sdk.routing.RoutingController.get().isNavigating())
      return;
    final androidx.core.app.NotificationCompat.MessagingStyle style =
        androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
    if (style == null && !Notification.CATEGORY_MESSAGE.equals(n.category))
      return;
    String sender = null, text = null, group = null;
    if (style != null && !style.getMessages().isEmpty())
    {
      final androidx.core.app.NotificationCompat.MessagingStyle.Message last =
          style.getMessages().get(style.getMessages().size() - 1);
      if (last.getPerson() != null && last.getPerson().getName() != null)
        sender = last.getPerson().getName().toString();
      if (last.getText() != null)
        text = last.getText().toString();
      if (style.isGroupConversation() && style.getConversationTitle() != null)
        group = style.getConversationTitle().toString();
    }
    final Bundle x = n.extras;
    if (sender == null)
    {
      final CharSequence t = x.getCharSequence(Notification.EXTRA_TITLE);
      sender = t == null ? null : t.toString();
    }
    if (text == null)
    {
      final CharSequence t = x.getCharSequence(Notification.EXTRA_TEXT);
      text = t == null ? null : t.toString();
    }
    if (sender == null || sender.trim().isEmpty())
      return;
    final Msg old = MESSAGES.get(sbn.getKey());
    if (old != null && old.sender.equals(sender.trim()) && java.util.Objects.equals(old.text, text))
      return;
    Notification.Action reply = null;
    if (n.actions != null)
      for (Notification.Action a : n.actions)
      {
        final android.app.RemoteInput[] in = a.getRemoteInputs();
        if (in != null && in.length > 0 && in[0].getAllowFreeFormInput() && a.actionIntent != null)
        {
          reply = a;
          break;
        }
      }
    String app = null;
    try
    {
      final PackageManager pm = c.getPackageManager();
      app = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
    }
    catch (PackageManager.NameNotFoundException ignored)
    {
      // App senza nome: si dice solo chi scrive.
    }
    final Msg m = new Msg(sbn.getKey(), sbn.getPackageName(), sender.trim(), text, app, group,
                          sbn.getPostTime(), reply, n.contentIntent);
    MESSAGES.remove(sbn.getKey());
    MESSAGES.put(sbn.getKey(), m);
    changed();
    EuriseTrip.onMessage(c, m);
  }

  /** Una sola istanza, perché si possa anche togliere. */
  private static final MediaSessionManager.OnActiveSessionsChangedListener SESSIONS = EuriseNowPlaying::onSessions;

  @MainThread
  private static void onSessions(@Nullable List<MediaController> list)
  {
    MediaController pick = null;
    if (list != null)
    {
      for (MediaController m : list)
      {
        final PlaybackState s = m.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING)
        {
          pick = m;
          break;
        }
      }
      if (pick == null && !list.isEmpty())
        pick = list.get(0);
    }
    if (sController != null && (pick == null || !pick.getSessionToken().equals(sController.getSessionToken())))
    {
      sController.unregisterCallback(CALLBACK);
      sController = null;
    }
    if (pick != null && sController == null)
    {
      sController = pick;
      pick.registerCallback(CALLBACK, MAIN);
    }
    read();
  }

  private static final MediaController.Callback CALLBACK = new MediaController.Callback() {
    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata)
    {
      read();
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state)
    {
      read();
      // Un'altra app potrebbe aver cominciato a suonare: si riguarda chi suona.
      if (state != null && state.getState() != PlaybackState.STATE_PLAYING && sSessions != null && sComponent != null)
      {
        try
        {
          onSessions(sSessions.getActiveSessions(sComponent));
        }
        catch (SecurityException ignored)
        {
          // Accesso tolto.
        }
      }
    }

    @Override
    public void onSessionDestroyed()
    {
      if (sController != null)
        sController.unregisterCallback(this);
      sController = null;
      sMedia = null;
      changed();
    }
  };

  private static void read()
  {
    final Context c = sApp;
    final MediaController m = sController;
    if (c == null || m == null)
    {
      sMedia = null;
      changed();
      return;
    }
    final MediaMetadata md = m.getMetadata();
    final PlaybackState ps = m.getPlaybackState();
    final String title = md == null ? null : text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
    if (title == null)
    {
      sMedia = null;
      changed();
      return;
    }
    final boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
    final Media old = sMedia;
    final boolean same = old != null && old.pkg.equals(m.getPackageName()) && old.title.equals(title);
    final long pausedAt = playing ? 0 : (same && old.pausedAt > 0 ? old.pausedAt : SystemClock.elapsedRealtime());
    Bitmap art = same ? old.art : null;
    if (art == null)
    {
      Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
      if (b == null)
        b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
      if (b == null)
        b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
      art = scaled(b, 160);
    }
    String year = text(md, MediaMetadata.METADATA_KEY_DATE);
    if (year == null)
    {
      final long y = md.getLong(MediaMetadata.METADATA_KEY_YEAR);
      if (y > 0)
        year = Long.toString(y);
    }
    String appName = same ? old.appName : null;
    Drawable appIcon = same ? old.appIcon : null;
    if (!same)
    {
      try
      {
        final PackageManager pm = c.getPackageManager();
        final ApplicationInfo ai = pm.getApplicationInfo(m.getPackageName(), 0);
        appName = pm.getApplicationLabel(ai).toString();
        appIcon = pm.getApplicationIcon(ai);
      }
      catch (PackageManager.NameNotFoundException ignored)
      {
        // App senza icona: la bolla mostra solo la copertina.
      }
    }
    sMedia = new Media(m.getPackageName(), title,
                       text(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                       text(md, MediaMetadata.METADATA_KEY_ALBUM), NowPlayingText.year(year), appName,
                       Math.max(0, md.getLong(MediaMetadata.METADATA_KEY_DURATION)),
                       ps == null ? 0 : Math.max(0, ps.getPosition()),
                       ps == null ? 0 : ps.getLastPositionUpdateTime(),
                       ps == null ? 1f : ps.getPlaybackSpeed(), playing, art, appIcon, pausedAt);
    sMedia.next = next(m, ps);
    sMedia.track = md.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
    sMedia.tracks = md.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);
    sMedia.live = NowPlayingText.live(sMedia.durationMs);
    sMedia.spoken = !sMedia.live && NowPlayingText.spoken(sMedia.durationMs, SPOKEN_APPS.contains(m.getPackageName()));
    if (!same)
      EuriseLyrics.onTrack(c, sMedia);
    changed();
  }

  /** Il brano dopo quello che suona, dalla coda condivisa dall'app (tavola MapBollaDopo). */
  @Nullable
  private static String next(@NonNull MediaController m, @Nullable PlaybackState ps)
  {
    if (ps == null || ps.getActiveQueueItemId() == MediaSession.QueueItem.UNKNOWN_ID)
      return null;
    try
    {
      final List<MediaSession.QueueItem> q = m.getQueue();
      if (q == null)
        return null;
      for (int i = 0; i < q.size() - 1; i++)
      {
        if (q.get(i).getQueueId() != ps.getActiveQueueItemId())
          continue;
        final MediaDescription d = q.get(i + 1).getDescription();
        if (d.getTitle() == null || d.getTitle().toString().trim().isEmpty())
          return null;
        final String t = d.getTitle().toString().trim();
        return d.getSubtitle() == null || d.getSubtitle().toString().trim().isEmpty()
               ? t : t + " · " + d.getSubtitle().toString().trim();
      }
    }
    catch (RuntimeException ignored)
    {
      // Coda troppo grande o sessione chiusa nel frattempo.
    }
    return null;
  }

  /** Impegni delle prossime 3 ore («soon»), o quelli con un luogo se l'app Eurise è di prima. Fuori dal thread principale. */
  @Nullable
  private static List<Event> readEvents(@NonNull Context c)
  {
    Bundle b = null;
    for (String h : HUBS)
    {
      for (String method : new String[] {"soon", "list"})
      {
        try
        {
          b = EuriseSuite.call(c, Uri.parse("content://" + h + ".destinations"), method, null, null);
        }
        catch (Exception ignored)
        {
          b = null;
        }
        if (b != null && b.getStringArray("title") != null)
          break;
      }
      if (b != null)
        break;
    }
    if (b == null)
      return null;
    final String[] titles = b.getStringArray("title");
    final long[] starts = b.getLongArray("start");
    final List<Event> out = new ArrayList<>();
    if (titles != null && starts != null)
      for (int i = 0; i < Math.min(titles.length, starts.length); i++)
        if (titles[i] != null && !titles[i].trim().isEmpty())
          out.add(new Event(titles[i].trim(), starts[i]));
    return out;
  }

  @Nullable
  private static String text(@NonNull MediaMetadata md, @NonNull String... keys)
  {
    for (String k : keys)
    {
      final CharSequence s = md.getText(k);
      if (s != null && !s.toString().trim().isEmpty())
        return s.toString().trim();
    }
    return null;
  }

  @androidx.annotation.RequiresApi(23)
  @Nullable
  private static Bitmap bitmap(@NonNull Context c, @Nullable Icon icon, int max)
  {
    if (icon == null)
      return null;
    try
    {
      final Drawable d = icon.loadDrawable(c);
      if (d == null)
        return null;
      if (d instanceof BitmapDrawable)
        return scaled(((BitmapDrawable) d).getBitmap(), max);
      final int w = Math.max(1, Math.min(max, d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : max));
      final int h = Math.max(1, Math.min(max, d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : max));
      final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      d.setBounds(0, 0, w, h);
      d.draw(new Canvas(b));
      return b;
    }
    catch (RuntimeException e)
    {
      return null;
    }
  }

  /** Copertine e foto ridotte alla misura della bolla (regola della leggerezza: immagini alla dimensione mostrata). */
  @Nullable
  private static Bitmap scaled(@Nullable Bitmap b, int max)
  {
    if (b == null || b.isRecycled())
      return null;
    final int w = b.getWidth(), h = b.getHeight();
    if (w <= max && h <= max)
      return b.copy(Bitmap.Config.ARGB_8888, false);
    final float k = (float) max / Math.max(w, h);
    return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true);
  }

  /**
   * Il prossimo impegno di oggi o domani in cui compare il nome di chi chiama (per esempio «Pranzo con Giulia»), dagli
   * impegni con un luogo che l'app Eurise dà a Mappe. Fuori dal thread principale.
   */
  @Nullable
  private static String together(@NonNull Context c, @NonNull String name)
  {
    final String first = name.split("\\s+")[0].toLowerCase(Locale.ROOT);
    if (first.length() < 3 || !Character.isLetter(first.charAt(0)))
      return null;
    Bundle b = null;
    for (String h : HUBS)
    {
      try
      {
        b = EuriseSuite.call(c, Uri.parse("content://" + h + ".destinations"), "list", null, null);
        if (b != null)
          break;
      }
      catch (Exception ignored)
      {
        // App Eurise assente o di un'altra firma.
      }
    }
    if (b == null)
      return null;
    final String[] titles = b.getStringArray("title");
    final long[] starts = b.getLongArray("start");
    if (titles == null || starts == null)
      return null;
    final long now = System.currentTimeMillis();
    final Calendar limit = Calendar.getInstance();
    limit.add(Calendar.DAY_OF_YEAR, 2);
    limit.set(Calendar.HOUR_OF_DAY, 0);
    limit.set(Calendar.MINUTE, 0);
    int best = -1;
    for (int i = 0; i < Math.min(titles.length, starts.length); i++)
    {
      if (titles[i] == null || starts[i] < now - 60 * 60_000L || starts[i] > limit.getTimeInMillis())
        continue;
      if (!(" " + titles[i].toLowerCase(Locale.ROOT) + " ").matches(".*[^\\p{L}]" + java.util.regex.Pattern.quote(first) + "[^\\p{L}].*"))
        continue;
      if (best < 0 || starts[i] < starts[best])
        best = i;
    }
    if (best < 0)
      return null;
    final Calendar when = Calendar.getInstance();
    when.setTimeInMillis(starts[best]);
    final Calendar today = Calendar.getInstance();
    final boolean isToday = when.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                            && when.get(Calendar.YEAR) == today.get(Calendar.YEAR);
    final String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(when.getTime());
    return c.getString(isToday ? R.string.eurise_np_together_today : R.string.eurise_np_together_tomorrow,
                       titles[best], time);
  }
}
