package app.organicmaps.eurise;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Alfred che legge ad alta voce in guida (tavola MapBollaMessaggio): la sintesi vocale del telefono, la stessa voce
 * scelta per la guida (Eurise Voce con Nicola, se è il motore predefinito). Abbassa la musica mentre parla; in auto la
 * voce esce dagli altoparlanti dell'auto come le indicazioni. Nulla esce dal telefono.
 */
public final class EuriseSpeak
{
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  @Nullable private static TextToSpeech sTts;
  private static boolean sReady;
  @Nullable private static String sPending;
  @Nullable private static Runnable sDone;
  @Nullable private static AudioFocusRequest sFocus;

  private EuriseSpeak() {}

  /** Legge [text]; [done] arriva sul thread principale quando ha finito (o se non può parlare). */
  public static void say(@NonNull Context context, @NonNull String text, @Nullable Runnable done)
  {
    sDone = done;
    if (sTts == null)
    {
      final Context app = context.getApplicationContext();
      sPending = text;
      sTts = new TextToSpeech(app, status -> MAIN.post(() -> {
        sReady = status == TextToSpeech.SUCCESS;
        if (!sReady)
        {
          sTts = null;
          finished();
          return;
        }
        setup(app);
        final String t = sPending;
        sPending = null;
        if (t != null)
          speak(app, t);
      }));
      return;
    }
    if (!sReady)
    {
      sPending = text;
      return;
    }
    speak(context.getApplicationContext(), text);
  }

  /** «Basta»: smette di leggere. */
  /** Vero se Alfred sta già leggendo qualcosa (un messaggio, una risposta): le battute in guida aspettano. */
  public static boolean busy()
  {
    return sPending != null || (sTts != null && sReady && sTts.isSpeaking());
  }

  public static void stop()
  {
    sPending = null;
    sDone = null;
    if (sTts != null && sReady)
      sTts.stop();
  }

  private static void setup(@NonNull Context app)
  {
    final TextToSpeech tts = sTts;
    if (tts == null)
      return;
    final Locale l = Locale.getDefault();
    if (tts.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE)
      tts.setLanguage(l);
    tts.setAudioAttributes(new AudioAttributes.Builder()
                               .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                               .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                               .build());
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
      @Override
      public void onStart(String id) {}

      @Override
      public void onDone(String id)
      {
        MAIN.post(() -> {
          release(app);
          finished();
        });
      }

      @Override
      public void onError(String id)
      {
        onDone(id);
      }

      @Override
      public void onStop(String id, boolean interrupted)
      {
        MAIN.post(() -> release(app));
      }
    });
  }

  private static void speak(@NonNull Context app, @NonNull String text)
  {
    final TextToSpeech tts = sTts;
    if (tts == null)
      return;
    focus(app);
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eurise-" + System.nanoTime());
  }

  private static void finished()
  {
    final Runnable r = sDone;
    sDone = null;
    if (r != null)
      r.run();
  }

  private static void focus(@NonNull Context app)
  {
    final AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    if (am == null || Build.VERSION.SDK_INT < 26)
      return;
    sFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                 .setAudioAttributes(new AudioAttributes.Builder()
                                         .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                         .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                         .build())
                 .build();
    am.requestAudioFocus(sFocus);
  }

  private static void release(@NonNull Context app)
  {
    final AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    if (am != null && sFocus != null && Build.VERSION.SDK_INT >= 26)
      am.abandonAudioFocusRequest(sFocus);
    sFocus = null;
  }
}
