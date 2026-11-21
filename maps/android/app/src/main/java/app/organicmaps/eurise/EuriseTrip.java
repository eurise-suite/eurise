package app.organicmaps.eurise;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Il viaggio in corso per la bolla (tavole MapBollaPersa, MapBollaRitardo, MapBollaMessaggio, MapBollaArrivo, approvate
 * il 17 novembre): gli avvisi con i pulsanti (chiamata persa, messaggi, «Avvisa», conferme con Annulla), i promemoria
 * per l'arrivo e l'arrivo condiviso per SMS.
 *
 * <p>Lo stesso avviso si mostra nella bolla del telefono (due pulsanti, il principale a destra) e come avviso di Android
 * Auto ({@link app.organicmaps.eurise.car.EuriseCarAlerts}), così la fila della guida in auto resta a quattro pulsanti.
 * Chi tocca passa un {@link Host}: telefono o auto fanno a modo loro solo l'ascolto della risposta e la chiamata.
 * Tutto sul thread principale; nulla resta dopo il viaggio.
 */
public final class EuriseTrip
{
  /** Dove si è toccato: telefono o auto. */
  public interface Host
  {
    @NonNull
    Context context();

    boolean car();

    /** Ascolta una frase (risposta a voce): sul telefono con il riconoscimento del telefono, in auto con «Ti ascolto». */
    void listen(@NonNull String prompt, @NonNull Text then);

    /** Apre la chiamata a [number] (o il tastierino, se il numero non c'è). */
    void dial(@Nullable String number);
  }

  public interface Text
  {
    void onText(@NonNull String text);
  }

  public interface Act
  {
    void run(@NonNull Host host);
  }

  public static final int KIND_MISSED = 1, KIND_MESSAGE = 2, KIND_READING = 3, KIND_SEND = 4, KIND_LATE = 5,
                          KIND_DONE = 6, KIND_SHARE = 7, KIND_ARRIVED = 8;
  public static final int TONE_PLAIN = 0, TONE_LATE = 1, TONE_MISSED = 2, TONE_OK = 3;

  /** Un avviso della bolla: titolo, riga sotto, fino a due pulsanti (il principale a destra), scadenza. */
  public static final class Notice
  {
    private static long sIds;
    public final long id = ++sIds;
    public final int kind;
    public String title;
    @Nullable public String sub;
    @Nullable public Bitmap photo;
    @Nullable public String initials;
    public int icon;
    public int tone;
    /** Quante volte (chiamate perse dalla stessa persona). */
    int count = 1;
    @Nullable public String left, right;
    /** Etichetta più corta per l'auto (gli avvisi di Android Auto hanno pulsanti piccoli), se serve. */
    @Nullable public String rightCar;
    @Nullable Act onLeft, onRight, onExpire;
    /** Quando sparisce (orologio di {@link SystemClock#elapsedRealtime}), 0 = finché non si tocca. */
    long until;

    Notice(int kind)
    {
      this.kind = kind;
    }

    /** Secondi che restano prima che sparisca (o che parta da solo, per «Annulla»). */
    public long remainingMs()
    {
      return until == 0 ? 0 : Math.max(0, until - SystemClock.elapsedRealtime());
    }
  }

  private static final long NOTICE_MS = 15_000;
  private static final long UNDO_MS = 5_000;
  private static final long TICK_MS = 5_000;
  private static final String[] HUBS = EuriseSuite.HUBS;
  private static final String SUITE_PERMISSION = EuriseSuite.PERMISSION;
  private static final String ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK";
  private static final String CHANNEL = "eurise_trip";
  private static final String PREFS = "eurise_trip";

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
  private static final ArrayDeque<Notice> QUEUE = new ArrayDeque<>();
  @Nullable private static Notice sNotice;
  @Nullable private static Context sApp;

  private static boolean sNavigating, sLooping;
  private static long sEtaSec = -1, sLastEtaSec = -1;
  /** Messaggi già letti o lasciati per dopo (orologio di {@link System#currentTimeMillis}). */
  private static long sReadUpTo;
  private static final Set<String> sLateOffered = new HashSet<>();
  /** «Richiama all'arrivo»: nome e numero. */
  private static final List<String[]> sReminders = new ArrayList<>();

  // Arrivo condiviso.
  @Nullable private static String sShareName, sShareNumber;
  private static boolean sShareActive, sShareFirstSent;
  private static long sShareSentEta, sShareSentAt, sShareStartedAt;

  private EuriseTrip() {}

  // ---- Per chi mostra ------------------------------------------------------------------------------------------------

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

  /** L'avviso da mostrare adesso, se c'è. */
  @Nullable
  public static Notice notice()
  {
    return sNotice;
  }

  /** Il tocco sul pulsante di sinistra (o «Dopo» nell'auto). */
  @MainThread
  public static void left(@NonNull Host host, @NonNull Notice n)
  {
    if (sNotice != n)
      return;
    dismiss(n);
    if (n.onLeft != null)
      n.onLeft.run(host);
  }

  /** Il tocco sul pulsante principale, a destra. */
  @MainThread
  public static void right(@NonNull Host host, @NonNull Notice n)
  {
    if (sNotice != n)
      return;
    dismiss(n);
    if (n.onRight != null)
      n.onRight.run(host);
  }

  /** L'avviso sparito senza tocchi (scaduto o chiuso dall'auto): se aveva un'azione a tempo, parte. */
  @MainThread
  public static void expired(@NonNull Notice n)
  {
    if (sNotice != n)
      return;
    dismiss(n);
    if (n.onExpire != null && sApp != null)
      n.onExpire.run(appHost(sApp));
  }

  /** Arrivo previsto (millisecondi), 0 se non si sta guidando verso una meta. */
  public static long eta()
  {
    return sNavigating && sEtaSec > 0 ? System.currentTimeMillis() + sEtaSec * 1000L : 0;
  }

  /** Il viaggio va: da chiamare quando si mostra la bolla (telefono o auto) e quando si accende la condivisione. */
  @MainThread
  public static void ensure(@NonNull Context c)
  {
    sApp = c.getApplicationContext();
    if (!sLooping)
    {
      sLooping = true;
      MAIN.post(TICK);
    }
  }

  // ---- Arrivo condiviso ----------------------------------------------------------------------------------------------

  public static boolean sharing()
  {
    return sShareActive;
  }

  @Nullable
  public static String shareName()
  {
    return sShareActive ? sShareName : null;
  }

  /**
   * Comincia a condividere l'arrivo con una persona: un SMS adesso (o alla partenza), uno se l'arrivo cambia di 5 minuti
   * o più, uno all'arrivo. Solo l'ora d'arrivo, mai la posizione.
   */
  @MainThread
  public static void startShare(@NonNull Context c, @NonNull String name, @Nullable String number)
  {
    ensure(c);
    sShareName = name;
    sShareNumber = number;
    sShareActive = true;
    sShareFirstSent = false;
    sShareStartedAt = System.currentTimeMillis();
    remember(c, name, number);
    if (sNavigating && sEtaSec > 0)
      shareFirst(c);
    changed();
  }

  @MainThread
  public static void stopShare()
  {
    sShareActive = false;
    sShareFirstSent = false;
    changed();
  }

  /** Le persone con cui si è condiviso di recente, per l'auto (nome, numero o stringa vuota). */
  @NonNull
  public static List<String[]> recent(@NonNull Context c)
  {
    final List<String[]> out = new ArrayList<>();
    final String all = prefs(c).getString("recent", "");
    for (String line : all.split("\n"))
    {
      final String[] p = line.split("\t", -1);
      if (p.length == 2 && !p[0].isEmpty())
        out.add(p);
    }
    return out;
  }

  private static void remember(@NonNull Context c, @NonNull String name, @Nullable String number)
  {
    final StringBuilder b = new StringBuilder(name.replace('\t', ' ').replace('\n', ' ')).append('\t')
                                .append(number == null ? "" : number);
    int n = 1;
    for (String[] p : recent(c))
    {
      if (p[0].equals(name) || n >= 6)
        continue;
      b.append('\n').append(p[0]).append('\t').append(p[1]);
      n++;
    }
    prefs(c).edit().putString("recent", b.toString()).apply();
  }

  private static void shareFirst(@NonNull Context c)
  {
    final long eta = eta();
    sShareFirstSent = true;
    sShareSentEta = eta;
    sShareSentAt = System.currentTimeMillis();
    final String name = sShareName == null ? "" : sShareName;
    sms(c, name, sShareNumber, c.getString(R.string.eurise_trip_share_first, time(eta)), false);
    final Notice n = new Notice(KIND_SHARE);
    n.title = c.getString(R.string.eurise_trip_share_title, name);
    n.sub = c.getString(R.string.eurise_trip_share_sub, time(eta), time(sShareSentAt));
    n.icon = R.drawable.eurise_np_pin;
    n.tone = TONE_OK;
    n.right = c.getString(R.string.eurise_trip_stop);
    n.onRight = h -> stopShare();
    n.until = SystemClock.elapsedRealtime() + 10_000;
    show(n);
  }

  // ---- Dalle notifiche ----------------------------------------------------------------------------------------------

  /** Chiamata persa mentre si guida (tavola MapBollaPersa). */
  @MainThread
  static void onMissed(@NonNull Context c, @NonNull String name, @Nullable String number, @Nullable Bitmap photo)
  {
    ensure(c);
    if (!sNavigating)
      return;
    int count = 1;
    final Notice old = find(KIND_MISSED, name);
    if (old != null)
    {
      count = old.count + 1;
      QUEUE.remove(old);
      if (sNotice == old)
        sNotice = null;
    }
    final Notice n = new Notice(KIND_MISSED);
    n.count = count;
    n.title = c.getString(R.string.eurise_trip_missed, name);
    n.sub = count > 1 ? c.getString(R.string.eurise_trip_missed_times, time(System.currentTimeMillis()), count)
                      : time(System.currentTimeMillis());
    n.photo = photo;
    n.initials = name;
    n.icon = R.drawable.eurise_np_missed;
    n.tone = TONE_MISSED;
    n.left = c.getString(R.string.eurise_trip_ignore);
    n.right = c.getString(R.string.eurise_trip_call_back_arrival);
    n.rightCar = c.getString(R.string.eurise_trip_call_back_arrival_car);
    n.until = SystemClock.elapsedRealtime() + NOTICE_MS;
    n.onRight = h -> {
      final Notice done = new Notice(KIND_DONE);
      done.title = c.getString(R.string.eurise_trip_reminder_done, name);
      done.icon = R.drawable.eurise_np_bell;
      done.tone = TONE_OK;
      done.right = c.getString(R.string.eurise_trip_undo);
      done.until = SystemClock.elapsedRealtime() + 4_000;
      done.onExpire = x -> {
        task(x.context(), x.context().getString(R.string.eurise_trip_task_call_back, name));
        sReminders.add(new String[] {name, number == null ? "" : number});
      };
      show(done, true);
    };
    show(n);
  }

  /** Un messaggio arrivato in guida (tavola MapBollaMessaggio). */
  @MainThread
  static void onMessage(@NonNull Context c, @NonNull EuriseNowPlaying.Msg m)
  {
    ensure(c);
    if (!sNavigating)
      return;
    final List<EuriseNowPlaying.Msg> unread = unread();
    if (unread.isEmpty())
      return;
    Notice n = find(KIND_MESSAGE, null);
    final boolean fresh = n == null;
    if (fresh)
      n = new Notice(KIND_MESSAGE);
    final Set<String> people = new HashSet<>();
    for (EuriseNowPlaying.Msg x : unread)
      people.add(x.sender);
    final EuriseNowPlaying.Msg last = unread.get(unread.size() - 1);
    if (unread.size() == 1)
    {
      n.title = c.getString(R.string.eurise_trip_message_from, last.sender);
      n.sub = last.app == null ? c.getString(R.string.eurise_trip_now) : c.getString(R.string.eurise_trip_app_now, last.app);
    }
    else
    {
      n.title = c.getResources().getQuantityString(R.plurals.eurise_trip_messages, unread.size(), unread.size(),
                                                   people.size());
      n.sub = android.text.TextUtils.join(", ", people);
    }
    n.initials = people.size() == 1 ? last.sender : Integer.toString(unread.size());
    n.icon = R.drawable.eurise_np_msg;
    n.left = c.getString(R.string.eurise_trip_later);
    n.right = c.getString(R.string.eurise_trip_read);
    n.until = SystemClock.elapsedRealtime() + NOTICE_MS;
    n.onLeft = h -> sReadUpTo = System.currentTimeMillis();
    n.onRight = EuriseTrip::read;
    if (fresh)
      show(n);
    else
      changed();
  }

  /** La chiamata è cambiata (o finita, [call] null): se chi chiama ti aspetta e arrivi tardi, «Avvisa». */
  @MainThread
  static void onCall(@NonNull Context c, @Nullable EuriseNowPlaying.Call call)
  {
    ensure(c);
    if (call == null)
      callEnded();
    if (call == null || !sNavigating || eta() == 0)
    {
      changed();
      return;
    }
    final long now = System.currentTimeMillis();
    final long eta = eta();
    EuriseNowPlaying.Event best = null;
    for (EuriseNowPlaying.Event e : EuriseNowPlaying.events(c))
    {
      if (!NowPlayingText.nameIn(e.title, call.name) || e.start < now - 30 * 60_000L || e.start > now + 6 * 3_600_000L)
        continue;
      if (NowPlayingText.lateMinutes(eta, e.start) < 3)
        continue;
      if (best == null || e.start < best.start)
        best = e;
    }
    if (best == null)
    {
      changed();
      return;
    }
    final String key = call.name + "\u0000" + best.start;
    if (!sLateOffered.add(key))
      return;
    final EuriseNowPlaying.Event e = best;
    final String name = call.name;
    final String number = call.number;
    final Notice n = new Notice(KIND_LATE);
    n.title = c.getString(R.string.eurise_trip_late_title, name, e.title, time(e.start));
    n.sub = c.getString(R.string.eurise_trip_late_sub, time(eta), NowPlayingText.lateMinutes(eta, e.start));
    n.initials = name;
    n.icon = R.drawable.eurise_np_clock;
    n.tone = TONE_LATE;
    n.left = c.getString(R.string.eurise_trip_later);
    n.right = c.getString(R.string.eurise_trip_warn, time(eta));
    n.rightCar = c.getString(R.string.eurise_trip_warn_car);
    n.until = 0;
    n.onRight = h -> warn(h, name, number);
    show(n);
  }

  /** La bolla mostra l'avviso di ritardo dentro la chiamata finché la chiamata c'è; dopo, ancora per 15 secondi. */
  static void callEnded()
  {
    if (sNotice != null && sNotice.kind == KIND_LATE && sNotice.until == 0)
      sNotice.until = SystemClock.elapsedRealtime() + NOTICE_MS;
  }

  // ---- Azioni --------------------------------------------------------------------------------------------------------

  private static void read(@NonNull Host h)
  {
    final Context c = h.context();
    final List<EuriseNowPlaying.Msg> unread = unread();
    sReadUpTo = System.currentTimeMillis();
    if (unread.isEmpty())
      return;
    final StringBuilder say = new StringBuilder();
    for (EuriseNowPlaying.Msg m : unread)
    {
      final String body = m.text == null || m.text.trim().isEmpty() ? c.getString(R.string.eurise_trip_no_text) : m.text;
      say.append(m.group == null ? c.getString(R.string.eurise_trip_say_from, m.sender, body)
                                 : c.getString(R.string.eurise_trip_say_group, m.sender, m.group, body))
         .append(' ');
    }
    final EuriseNowPlaying.Msg last = unread.get(unread.size() - 1);
    final Notice n = new Notice(KIND_READING);
    n.title = unread.size() == 1 ? c.getString(R.string.eurise_trip_reading_one, last.sender)
                                 : c.getString(R.string.eurise_trip_reading_many);
    n.icon = R.drawable.eurise_np_speak;
    n.left = c.getString(R.string.eurise_trip_enough);
    n.onLeft = x -> EuriseSpeak.stop();
    if (last.reply != null)
    {
      n.right = c.getString(R.string.eurise_trip_reply_voice);
      n.rightCar = c.getString(R.string.eurise_trip_reply_voice_car);
      n.onRight = x -> {
        EuriseSpeak.stop();
        x.listen(c.getString(R.string.eurise_trip_reply_to, last.sender), text -> confirmReply(x, last, text));
      };
    }
    n.until = 0;
    show(n, true);
    EuriseSpeak.say(c, say.toString().trim(), () -> {
      if (sNotice == n)
      {
        n.until = SystemClock.elapsedRealtime() + 10_000;
        changed();
      }
    });
  }

  /** Alfred rilegge la risposta; dopo 5 secondi parte, se non si tocca «Annulla». */
  private static void confirmReply(@NonNull Host h, @NonNull EuriseNowPlaying.Msg m, @NonNull String text)
  {
    final Context c = h.context();
    final Notice n = new Notice(KIND_SEND);
    n.title = c.getString(R.string.eurise_trip_sending_to, m.sender);
    n.sub = "«" + text + "»";
    n.icon = R.drawable.eurise_np_msg;
    n.right = c.getString(R.string.eurise_trip_undo);
    n.until = SystemClock.elapsedRealtime() + UNDO_MS + Math.min(10_000, text.length() * 60L);
    n.onExpire = x -> {
      final boolean ok = reply(x.context(), m, text);
      done(x.context(), ok ? x.context().getString(R.string.eurise_trip_sent_to, m.sender)
                           : x.context().getString(R.string.eurise_trip_not_sent), ok);
    };
    show(n, true);
    EuriseSpeak.say(c, c.getString(R.string.eurise_trip_say_sending, m.sender, text), null);
  }

  /** La risposta rapida della notifica, come se si fosse scritto lì. */
  private static boolean reply(@NonNull Context c, @NonNull EuriseNowPlaying.Msg m, @NonNull String text)
  {
    if (m.reply == null || m.reply.getRemoteInputs() == null)
      return false;
    try
    {
      final RemoteInput[] inputs = m.reply.getRemoteInputs();
      final Intent fill = new Intent();
      final Bundle results = new Bundle();
      for (RemoteInput in : inputs)
        results.putCharSequence(in.getResultKey(), text);
      RemoteInput.addResultsToIntent(inputs, fill, results);
      m.reply.actionIntent.send(c, 0, fill);
      return true;
    }
    catch (PendingIntent.CanceledException | RuntimeException e)
    {
      return false;
    }
  }

  /** «Avvisa: arrivo alle 9:54»: il messaggio parte da Eurise Messaggi dopo 5 secondi, se non si tocca «Annulla». */
  private static void warn(@NonNull Host h, @NonNull String name, @Nullable String number)
  {
    final Context c = h.context();
    final String text = c.getString(R.string.eurise_trip_warn_text, time(eta()));
    if (!EuriseSms.available(c))
    {
      if (!h.car() && number != null)
      {
        try
        {
          c.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number)).putExtra("sms_body", text)
                              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          return;
        }
        catch (RuntimeException ignored)
        {
          // Nessuna app dei messaggi: lo dice l'avviso sotto.
        }
      }
      done(c, c.getString(R.string.eurise_trip_needs_messages), false);
      return;
    }
    final Notice n = new Notice(KIND_SEND);
    n.title = c.getString(R.string.eurise_trip_sending_messages, name);
    n.sub = "«" + text + "»";
    n.icon = R.drawable.eurise_np_msg;
    n.right = c.getString(R.string.eurise_trip_undo);
    n.until = SystemClock.elapsedRealtime() + UNDO_MS;
    n.onExpire = x -> sms(x.context(), name, number, text, true);
    show(n, true);
  }

  private static void sms(@NonNull Context c, @NonNull String name, @Nullable String number, @NonNull String text,
                          boolean confirm)
  {
    EuriseSms.send(c, number, name, text, (ok, why) -> {
      if (ok && !confirm)
        return;
      final String msg;
      if (ok)
        msg = c.getString(R.string.eurise_trip_sent_to, name);
      else if ("no_number".equals(why))
        msg = c.getString(R.string.eurise_trip_no_number, name);
      else if ("no_app".equals(why))
        msg = c.getString(R.string.eurise_trip_needs_messages);
      else
        msg = c.getString(R.string.eurise_trip_not_sent);
      done(c, msg, ok);
    });
  }

  private static void done(@NonNull Context c, @NonNull String title, boolean ok)
  {
    final Notice n = new Notice(KIND_DONE);
    n.title = title;
    n.icon = ok ? R.drawable.eurise_np_msg : R.drawable.eurise_np_missed;
    n.tone = ok ? TONE_OK : TONE_MISSED;
    n.until = SystemClock.elapsedRealtime() + 4_000;
    show(n, true);
  }

  /** Una cosa da fare nell'app Eurise (Attività), come dalla Tastiera: passa solo tra app della suite. */
  private static void task(@NonNull Context c, @NonNull String text)
  {
    for (String hub : HUBS)
    {
      if (!EuriseSuite.isSuite(c, hub))
        continue;
      try
      {
        c.sendBroadcast(new Intent(ACTION_TASK).setPackage(hub).putExtra(Intent.EXTRA_TEXT, text), SUITE_PERMISSION);
        return;
      }
      catch (Exception ignored)
      {
        // Prova l'altra.
      }
    }
  }

  // ---- Battito del viaggio -------------------------------------------------------------------------------------------

  private static final Runnable TICK = new Runnable() {
    @Override
    public void run()
    {
      final Context c = sApp;
      if (c == null)
      {
        sLooping = false;
        return;
      }
      tick(c);
      // Fermo quando non c'è un viaggio né niente da fare: riparte con ensure() (bolla in guida, condivisione).
      if (!sNavigating && !sShareActive && sNotice == null)
      {
        sLooping = false;
        return;
      }
      MAIN.postDelayed(this, sNotice != null ? 500 : TICK_MS);
    }
  };

  private static void tick(@NonNull Context c)
  {
    final boolean nav = RoutingController.get().isNavigating();
    if (nav)
    {
      final RoutingInfo info = Framework.nativeGetRouteFollowingInfo();
      sEtaSec = info == null ? -1 : info.totalTimeInSeconds;
      if (sEtaSec > 0)
        sLastEtaSec = sEtaSec;
    }
    if (nav && !sNavigating)
    {
      sNavigating = true;
      sReadUpTo = System.currentTimeMillis();
      sLateOffered.clear();
      EuriseNowPlaying.forgetMessages();
      EuriseNowPlaying.events(c);
    }
    else if (!nav && sNavigating)
    {
      sNavigating = false;
      sEtaSec = -1;
      ended(c, sLastEtaSec >= 0 && sLastEtaSec <= 120);
      sLastEtaSec = -1;
    }
    if (sNavigating && sShareActive && sEtaSec > 0)
    {
      final long now = System.currentTimeMillis();
      if (!sShareFirstSent)
        shareFirst(c);
      else if (NowPlayingText.shareUpdate(sShareSentEta, sShareSentAt, eta(), now))
      {
        sShareSentEta = eta();
        sShareSentAt = now;
        sms(c, sShareName == null ? "" : sShareName, sShareNumber,
            c.getString(R.string.eurise_trip_share_update, time(sShareSentEta)), false);
      }
    }
    // Acceso dal percorso ma la guida non è mai partita: dopo due ore si spegne da solo, senza SMS.
    if (!sNavigating && sShareActive && !sShareFirstSent && System.currentTimeMillis() - sShareStartedAt > 2 * 3_600_000L)
      stopShare();
    final Notice n = sNotice;
    if (n != null && n.until > 0 && SystemClock.elapsedRealtime() >= n.until)
      expired(n);
  }

  /** Fine della guida: all'arrivo l'SMS «Sono arrivato» e i promemoria «Richiama…»; il resto si dimentica. */
  private static void ended(@NonNull Context c, boolean arrived)
  {
    if (sShareActive && sShareFirstSent && arrived)
      sms(c, sShareName == null ? "" : sShareName, sShareNumber, c.getString(R.string.eurise_trip_share_arrived), false);
    sShareActive = false;
    sShareFirstSent = false;
    for (String[] r : sReminders)
      remind(c, r[0], r[1].isEmpty() ? null : r[1], arrived);
    sReminders.clear();
    QUEUE.clear();
    sNotice = null;
    EuriseNowPlaying.forgetMessages();
    changed();
  }

  /** «Sei arrivato · richiama Giulia» con «Chiama»: notifica sul telefono, avviso in auto se Android Auto è aperto. */
  private static void remind(@NonNull Context c, @NonNull String name, @Nullable String number, boolean arrived)
  {
    final String title = c.getString(arrived ? R.string.eurise_trip_arrived_call : R.string.eurise_trip_later_call, name);
    if (app.organicmaps.eurise.car.EuriseCarAlerts.active())
    {
      final Notice n = new Notice(KIND_ARRIVED);
      n.title = title;
      n.initials = name;
      n.icon = R.drawable.eurise_np_arrive;
      n.tone = TONE_OK;
      n.left = c.getString(R.string.eurise_trip_later);
      n.right = c.getString(R.string.eurise_trip_call);
      n.until = SystemClock.elapsedRealtime() + 20_000;
      n.onRight = h -> h.dial(number);
      MAIN.post(() -> show(n));
    }
    final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null)
      return;
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
      nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.eurise_trip_channel),
                                                           NotificationManager.IMPORTANCE_DEFAULT));
    final Intent dial = number == null ? new Intent(Intent.ACTION_DIAL)
                                       : new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
    final PendingIntent pi = PendingIntent.getActivity(c, name.hashCode(), dial,
                                                       PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    final android.app.Notification.Builder b =
        Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, CHANNEL)
                                    : new android.app.Notification.Builder(c);
    b.setSmallIcon(R.drawable.eurise_np_missed)
        .setContentTitle(title)
        .setContentText(c.getString(R.string.eurise_trip_reminder_text))
        .setContentIntent(pi)
        .setAutoCancel(true)
        .addAction(action(c.getString(R.string.eurise_trip_call), pi));
    try
    {
      nm.notify(CHANNEL, name.hashCode(), b.build());
    }
    catch (SecurityException ignored)
    {
      // Notifiche non permesse: resta l'attività in Attività.
    }
  }

  @SuppressWarnings("deprecation")
  @NonNull
  private static android.app.Notification.Action action(@NonNull String title, @NonNull PendingIntent pi)
  {
    return new android.app.Notification.Action.Builder(0, title, pi).build();
  }

  // ---- Coda degli avvisi --------------------------------------------------------------------------------------------

  private static void show(@NonNull Notice n)
  {
    show(n, false);
  }

  /** [now]: prende subito il posto di quello che c'è (conferme e risposte a un tocco). */
  private static void show(@NonNull Notice n, boolean now)
  {
    if (sNotice == null || now)
    {
      if (sNotice != null && now && sNotice.kind != KIND_DONE && sNotice.kind != KIND_SEND)
        QUEUE.addFirst(sNotice);
      sNotice = n;
    }
    else
      QUEUE.add(n);
    changed();
  }

  private static void dismiss(@NonNull Notice n)
  {
    QUEUE.remove(n);
    if (sNotice == n)
      sNotice = QUEUE.poll();
    if (sNotice != null && sNotice.until > 0)
      sNotice.until = Math.max(sNotice.until, SystemClock.elapsedRealtime() + 6_000);
    changed();
  }

  @Nullable
  private static Notice find(int kind, @Nullable String initials)
  {
    if (sNotice != null && sNotice.kind == kind && (initials == null || initials.equals(sNotice.initials)))
      return sNotice;
    for (Notice n : QUEUE)
      if (n.kind == kind && (initials == null || initials.equals(n.initials)))
        return n;
    return null;
  }

  @NonNull
  private static List<EuriseNowPlaying.Msg> unread()
  {
    final List<EuriseNowPlaying.Msg> out = new ArrayList<>();
    for (EuriseNowPlaying.Msg m : EuriseNowPlaying.messages())
      if (m.at > sReadUpTo)
        out.add(m);
    return out;
  }

  private static void changed()
  {
    for (Runnable r : LISTENERS)
      r.run();
    EuriseNowPlaying.poke();
  }

  @NonNull
  static String time(long millis)
  {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
  }

  private static SharedPreferences prefs(@NonNull Context c)
  {
    return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /** Per le azioni a tempo: nessuno ha toccato, non si ascolta e non si chiama. */
  @NonNull
  private static Host appHost(@NonNull Context app)
  {
    return new Host() {
      @NonNull
      @Override
      public Context context()
      {
        return app;
      }

      @Override
      public boolean car()
      {
        return false;
      }

      @Override
      public void listen(@NonNull String prompt, @NonNull Text then) {}

      @Override
      public void dial(@Nullable String number) {}
    };
  }
}
