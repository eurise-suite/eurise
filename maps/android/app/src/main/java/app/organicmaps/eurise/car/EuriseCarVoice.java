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
import androidx.car.app.CarToast;
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

  // Stessi nomi di core/suite VoiceContract (Mappe non dipende dai moduli Kotlin della suite).
  private static final String ACTION_TRANSCRIBE = "eu.eurise.action.TRANSCRIBE";
  private static final String[] VOICE_PACKAGES = {"eu.eurise.voice", "eu.eurise.voice.debug"};
  private static final int MSG_TRANSCRIBE = 1;
  private static final String KEY_AUDIO = "audio", KEY_LANGUAGE = "language", KEY_TEXT = "text", KEY_ERROR = "error";
  private static final String ERROR_NO_MODEL = "no_model";
  private static final int RATE = CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE;

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static volatile boolean sBusy;

  private EuriseCarVoice() {}

  /** Il tocco sul microfono (thread principale). Un secondo tocco mentre ascolta non fa nulla. */
  public static void listen(@NonNull CarContext car, @NonNull Callback callback)
  {
    if (sBusy)
      return;
    final String voice = voicePackage(car);
    if (voice == null)
    {
      toast(car, R.string.eurise_car_voice_missing);
      return;
    }
    if (ContextCompat.checkSelfPermission(car, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
    {
      // Android mostra la richiesta sul telefono; se la persona accetta, si ascolta subito.
      car.requestPermissions(Collections.singletonList(Manifest.permission.RECORD_AUDIO), (granted, rejected) -> {
        if (granted.contains(Manifest.permission.RECORD_AUDIO))
          listen(car, callback);
        else
          toast(car, R.string.eurise_car_voice_mic);
      });
      return;
    }
    sBusy = true;
    toast(car, R.string.eurise_car_voice_listening);
    new Thread(() -> record(car, voice, callback), "eurise-car-ear").start();
  }

  private static void record(@NonNull CarContext car, @NonNull String voice, @NonNull Callback callback)
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
      rec.startRecording();
      final byte[] buf = new byte[CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE];
      final int blockBytes = RATE / 10 * 2; // 100 ms
      double sum = 0;
      int inBlock = 0, samples = 0;
      boolean go = true;
      while (go)
      {
        final int n = rec.read(buf, 0, buf.length);
        if (n < 0)
          break; // la persona ha chiuso il microfono sullo schermo dell'auto
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
      rec.stopRecording();
    }
    catch (Throwable e)
    {
      done(am, focus);
      MAIN.post(() -> toast(car, R.string.eurise_car_voice_mic));
      return;
    }
    done(am, focus);
    if (silent || !heard)
    {
      MAIN.post(() -> toast(car, R.string.eurise_car_voice_nothing));
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
                                 @NonNull Callback callback)
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
      final String text = msg.getData().getString(KEY_TEXT);
      if (text != null && !text.trim().isEmpty())
      {
        final String place = place(text);
        if (place.isEmpty())
          toast(car, R.string.eurise_car_voice_nothing);
        else
          callback.onText(place);
      }
      else
        toast(car, ERROR_NO_MODEL.equals(msg.getData().getString(KEY_ERROR)) ? R.string.eurise_car_voice_model
                                                                             : R.string.eurise_car_voice_nothing);
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
        toast(car, R.string.eurise_car_voice_failed);
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
      toast(car, R.string.eurise_car_voice_failed);
      return;
    }
    sBusy = true;
    toast(car, R.string.eurise_car_voice_working);
    // Whisper sul telefono impiega qualche secondo; oltre il minuto qualcosa non va.
    MAIN.postDelayed(() -> {
      if (finished[0])
        return;
      finished[0] = true;
      sBusy = false;
      unbind.run();
      toast(car, R.string.eurise_car_voice_failed);
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
    for (String p : VOICE_PACKAGES)
    {
      try
      {
        c.getPackageManager().getPackageInfo(p, 0);
        return p;
      }
      catch (Exception ignored) {}
    }
    return null;
  }

  private static void toast(@NonNull CarContext car, int res)
  {
    CarToast.makeText(car, car.getString(res), CarToast.LENGTH_SHORT).show();
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
