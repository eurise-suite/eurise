package app.organicmaps.eurise.car;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.media.CarAudioRecord;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Locale;

/**
 * Il microfono di Alfred in Android Auto (tavola MapAutoGuida, correzione 2 del 13 novembre): si tocca, si dice dove
 * andare («portami in via Roma 3 a Monza»), e si apre la ricerca con quelle parole.
 *
 * <p>Ascolta il microfono dell'auto ({@link CarAudioRecord}, la sola via che Android Auto permette), capisce da solo
 * quando si è finito di parlare (come {@code Endpoint} della suite) e manda l'audio a Eurise Voce, che lo trascrive con
 * Whisper sul telefono: l'audio resta sul telefono e non si salva. Senza Eurise Voce o senza il suo modello lo dice e
 * non fa altro. Il permesso del microfono lo chiede Android sul telefono, la prima volta.
 */
public final class EuriseCarVoice
{
  public interface Callback
  {
    /** La frase già ripulita («via Roma 3 Monza»). */
    void onText(@NonNull String text);
  }

  /**
   * Lo stato dell'ascolto, per la schermata «Ti ascolto» ({@link EuriseCarListenScreen}): ogni passo e ogni errore si
   * vede sullo schermo dell'auto (prova del 16 novembre: toccando il microfono non succedeva nulla, senza un perché).
   */
  public interface Listener extends Callback
  {
    /** [message]: testo da mostrare; [retry]: si può riprovare (errore o silenzio); [busy]: sta lavorando. */
    void onStatus(int message, boolean retry, boolean busy);
  }

  /** Quanto si aspetta il primo pezzo d'audio dall'auto prima di dire che il microfono non risponde. */
  private static final long FIRST_AUDIO_MS = 3_000;
  @Nullable private static volatile CarAudioRecord sRecord;
  private static volatile boolean sCancelled;
  private static volatile long sBytes;

  // Stessi nomi di core/suite VoiceContract (Mappe non dipende dai moduli Kotlin della suite).
  private static final String ACTION_TRANSCRIBE = "eu.eurise.action.TRANSCRIBE";
  private static final String[] VOICE_PACKAGES = {app.organicmaps.eurise.EuriseSuite.VOICE};
  private static final int MSG_TRANSCRIBE = 1;
  private static final String KEY_AUDIO = "audio", KEY_LANGUAGE = "language", KEY_TEXT = "text", KEY_ERROR = "error";
  private static final String ERROR_NO_MODEL = "no_model";
  private static final int RATE = CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE;

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static volatile boolean sBusy;

  private EuriseCarVoice() {}

  /**
   * Frase intera, senza togliere le parole di comando: per la risposta a voce a un messaggio (tavola MapBollaMessaggio),
   * dove «vai pure» o «a dopo» vanno mandati come sono. Una sola frase alla volta: vale per l'ascolto in corso.
   */
  private static volatile boolean sRaw;

  /** Come {@link #listen(CarContext, Listener)}, ma la frase arriva intera (risposte ai messaggi). */
  public static void listenRaw(@NonNull CarContext car, @NonNull Listener callback)
  {
    sRaw = true;
    listenAny(car, callback);
  }

  /** Il tocco sul microfono (thread principale), dalla schermata «Ti ascolto». */
  public static void listen(@NonNull CarContext car, @NonNull Listener callback)
  {
    sRaw = false;
    listenAny(car, callback);
  }

  private static void listenAny(@NonNull CarContext car, @NonNull Listener callback)
  {
    // Un ascolto rimasto appeso (l'auto non mandava audio) non blocca più i tocchi successivi.
    cancel();
    sCancelled = false;
    final String voice = voicePackage(car);
    if (voice == null)
    {
      callback.onStatus(R.string.eurise_car_voice_missing, false, false);
      return;
    }
    if (ContextCompat.checkSelfPermission(car, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
    {
      // Android mostra la richiesta sul telefono; se la persona accetta, si ascolta subito.
      callback.onStatus(R.string.eurise_car_voice_permission, true, true);
      car.requestPermissions(Collections.singletonList(Manifest.permission.RECORD_AUDIO), (granted, rejected) -> {
        if (sCancelled)
          return;
        if (granted.contains(Manifest.permission.RECORD_AUDIO))
          listen(car, callback);
        else
          callback.onStatus(R.string.eurise_car_voice_mic, true, false);
      });
      return;
    }
    sBusy = true;
    sBytes = 0;
    callback.onStatus(R.string.eurise_car_voice_listening, false, true);
    new Thread(() -> record(car, voice, callback), "eurise-car-ear").start();
    // L'auto a volte non manda nulla (microfono occupato, auto che non lo permette): dopo 3 s si dice e si chiude.
    MAIN.postDelayed(() -> {
      if (sBusy && sBytes == 0 && sRecord != null && !sCancelled)
      {
        stopRecord();
        callback.onStatus(R.string.eurise_car_voice_no_audio, true, false);
      }
    }, FIRST_AUDIO_MS);
  }

  /** Si chiude la schermata «Ti ascolto» o si riprova: si ferma tutto. */
  public static void cancel()
  {
    sCancelled = true;
    stopRecord();
    sBusy = false;
  }

  private static void stopRecord()
  {
    final CarAudioRecord r = sRecord;
    sRecord = null;
    if (r != null)
    {
      try
      {
        r.stopRecording();
      }
      catch (Throwable ignored) {}
    }
  }

  private static void record(@NonNull CarContext car, @NonNull String voice, @NonNull Listener callback)
  {
    final AudioManager am = ContextCompat.getSystemService(car, AudioManager.class);
    AudioFocusRequest focus = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && am != null)
    {
      // La musica si ferma mentre si parla, così non finisce nella frase.
      focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                  .setAudioAttributes(new AudioAttributes.Builder()
                                          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                          .build())
                  .build();
      am.requestAudioFocus(focus);
    }
    final ByteArrayOutputStream pcm = new ByteArrayOutputStream(RATE * 2 * 8);
    final Endpoint endpoint = new Endpoint();
    boolean heard = false, silent = false;
    try
    {
      final CarAudioRecord rec = CarAudioRecord.create(car);
      sRecord = rec;
      rec.startRecording();
      final byte[] buf = new byte[CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE];
      final int blockBytes = RATE / 10 * 2; // 100 ms
      double sum = 0;
      int inBlock = 0, samples = 0;
      boolean go = true;
      while (go)
      {
        final int n = rec.read(buf, 0, buf.length);
        if (n < 0 || sCancelled || sRecord != rec)
          break; // microfono chiuso sullo schermo dell'auto, schermata chiusa o nessun audio
        sBytes += n;
        pcm.write(buf, 0, n);
        for (int i = 0; i + 1 < n; i += 2)
        {
          final int v = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
          sum += (double) v * v;
          samples++;
          inBlock += 2;
          if (inBlock >= blockBytes)
          {
            final float rms = (float) (Math.sqrt(sum / (inBlock / 2.0)) / 32768.0);
            sum = 0;
            inBlock = 0;
            final int ev = endpoint.feed(rms, samples);
            if (ev == Endpoint.STARTED)
              heard = true;
            else if (ev == Endpoint.FINISHED)
              go = false;
            else if (ev == Endpoint.NO_SPEECH)
            {
              silent = true;
              go = false;
            }
          }
        }
      }
      if (sRecord == rec)
        stopRecord();
    }
    catch (Throwable e)
    {
      done(am, focus);
      if (!sCancelled)
        MAIN.post(() -> callback.onStatus(R.string.eurise_car_voice_mic_car, true, false));
      return;
    }
    done(am, focus);
    if (sCancelled || sBytes == 0)
      return; // chiusa, o già detto «il microfono non risponde»
    if (silent || !heard)
    {
      MAIN.post(() -> callback.onStatus(R.string.eurise_car_voice_nothing, true, false));
      return;
    }
    sBusy = true;
    final byte[] audio = pcm.toByteArray();
    MAIN.post(() -> transcribe(car, voice, audio, callback));
  }

  private static void done(@Nullable AudioManager am, @Nullable AudioFocusRequest focus)
  {
    sBusy = false;
    if (am != null && focus != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      am.abandonAudioFocusRequest(focus);
  }

  /** L'audio va a Voce in un tubo; la risposta torna con un Messenger (come AlfredEar nella suite). */
  private static void transcribe(@NonNull CarContext car, @NonNull String voice, @NonNull byte[] audio,
                                 @NonNull Listener callback)
  {
    final Context app = car.getApplicationContext();
    final ServiceConnection[] conn = new ServiceConnection[1];
    final boolean[] finished = new boolean[1];
    final Runnable unbind = () -> {
      if (conn[0] != null)
      {
        try
        {
          app.unbindService(conn[0]);
        }
        catch (Exception ignored) {}
        conn[0] = null;
      }
    };
    final Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
      if (finished[0])
        return true;
      finished[0] = true;
      sBusy = false;
      unbind.run();
      if (sCancelled)
        return true;
      final String text = msg.getData().getString(KEY_TEXT);
      if (text != null && !text.trim().isEmpty())
      {
        final String place = sRaw ? text.trim().replaceAll("\\s+", " ") : place(text);
        if (place.isEmpty())
          callback.onStatus(R.string.eurise_car_voice_nothing, true, false);
        else
          callback.onText(place);
      }
      else
        callback.onStatus(ERROR_NO_MODEL.equals(msg.getData().getString(KEY_ERROR)) ? R.string.eurise_car_voice_model
                                                                                    : R.string.eurise_car_voice_nothing,
                          true, false);
      return true;
    }));
    conn[0] = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder service)
      {
        try
        {
          final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
          final Message m = Message.obtain(null, MSG_TRANSCRIBE);
          final Bundle data = new Bundle();
          data.putParcelable(KEY_AUDIO, pipe[0]);
          data.putString(KEY_LANGUAGE, Locale.getDefault().getLanguage());
          m.setData(data);
          m.replyTo = reply;
          new Messenger(service).send(m);
          pipe[0].close();
          new Thread(() -> {
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]))
            {
              out.write(audio);
            }
            catch (Exception ignored) {}
          }, "eurise-car-ear-pipe").start();
        }
        catch (Exception e)
        {
          fail();
        }
      }

      @Override
      public void onServiceDisconnected(ComponentName name)
      {
        fail();
      }

      private void fail()
      {
        if (finished[0])
          return;
        finished[0] = true;
        sBusy = false;
        unbind.run();
        if (!sCancelled)
          callback.onStatus(R.string.eurise_car_voice_failed, true, false);
      }
    };
    boolean bound;
    try
    {
      bound = app.bindService(new Intent(ACTION_TRANSCRIBE).setPackage(voice), conn[0], Context.BIND_AUTO_CREATE);
    }
    catch (Exception e)
    {
      bound = false;
    }
    if (!bound)
    {
      conn[0] = null;
      sBusy = false;
      callback.onStatus(R.string.eurise_car_voice_failed, true, false);
      return;
    }
    sBusy = true;
    callback.onStatus(R.string.eurise_car_voice_working, false, true);
    // Whisper sul telefono impiega qualche secondo; oltre il minuto qualcosa non va.
    MAIN.postDelayed(() -> {
      if (finished[0])
        return;
      finished[0] = true;
      sBusy = false;
      unbind.run();
      if (!sCancelled)
        callback.onStatus(R.string.eurise_car_voice_failed, true, false);
    }, 60_000);
  }

  /**
   * Dalla frase al luogo da cercare: via le parole di comando («portami a», «andiamo in», «naviga verso», «per favore»)
   * e la punteggiatura che Whisper aggiunge.
   */
  @NonNull
  static String place(@NonNull String said)
  {
    String s = said.trim().replaceAll("[.!?¿¡]+$", "").trim();
    final String lower = s.toLowerCase(Locale.ITALIAN);
    final String[] lead = {"alfred,", "alfred", "portami", "andiamo", "vai", "voglio andare", "devo andare", "naviga",
                           "guidami", "take me", "navigate", "go", "drive"};
    final String[] link = {"a", "ad", "al", "allo", "alla", "ai", "agli", "alle", "all'", "in", "nel", "nella", "da",
                           "dal", "dalla", "verso", "fino a", "to", "towards"};
    String rest = lower;
    int cut = 0;
    for (int round = 0; round < 3; round++)
    {
      boolean changed = false;
      for (String w : lead)
      {
        if (rest.startsWith(w + " ") || rest.equals(w))
        {
          cut += Math.min(rest.length(), w.length() + 1);
          rest = lower.substring(Math.min(cut, lower.length()));
          changed = true;
        }
      }
      for (String w : link)
      {
        final boolean apostrophe = w.endsWith("'");
        if (apostrophe ? rest.startsWith(w) : rest.startsWith(w + " "))
        {
          cut += w.length() + (apostrophe ? 0 : 1);
          rest = lower.substring(Math.min(cut, lower.length()));
          changed = true;
          break;
        }
      }
      if (!changed)
        break;
    }
    s = s.substring(Math.min(cut, s.length())).replaceAll("(?i),?\\s*per favore$", "").trim();
    return s.replaceAll("^[,;:]+", "").trim();
  }

  @Nullable
  private static String voicePackage(@NonNull Context c)
  {
    // Solo Voce della suite: l'audio del microfono non va a un'app con lo stesso nome.
    for (String p : VOICE_PACKAGES)
      if (app.organicmaps.eurise.EuriseSuite.isSuite(c, p))
        return p;
    return null;
  }

  /** La fine della frase, come {@code eu.eurise.core.suite.Endpoint} (blocchi da 100 ms). */
  static final class Endpoint
  {
    static final int NONE = 0, STARTED = 1, FINISHED = 2, NO_SPEECH = 3;
    private static final int END_PAUSE_MS = 900, MIN_SPEECH_MS = 300, WAIT_MS = 6_000, MAX_MS = 12_000;

    private float floor = -1f;
    private int voiced, run, silentSince = -1, last;
    private boolean started;

    int feed(float rms, int end)
    {
      final int block = end - last;
      last = end;
      floor = floor < 0 ? Math.min(rms, 0.006f)
            : rms < floor ? floor * 0.7f + rms * 0.3f : floor * 0.995f + rms * 0.005f;
      final boolean speech = rms > Math.max(0.012f, floor * 2.8f);
      if (speech)
      {
        run += block;
        voiced += block;
        silentSince = -1;
      }
      else
      {
        run = 0;
        if (silentSince < 0)
          silentSince = end - block;
      }
      if (!started && run >= ms(200))
      {
        started = true;
        return STARTED;
      }
      if (!started && end >= ms(WAIT_MS))
        return NO_SPEECH;
      if (end >= ms(MAX_MS))
        return started ? FINISHED : NO_SPEECH;
      if (started && voiced >= ms(MIN_SPEECH_MS) && silentSince >= 0 && end - silentSince >= ms(END_PAUSE_MS))
        return FINISHED;
      return NONE;
    }

    private static int ms(int v)
    {
      return RATE / 1000 * v;
    }
  }
}
