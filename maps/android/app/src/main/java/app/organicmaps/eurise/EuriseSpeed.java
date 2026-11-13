package app.organicmaps.eurise;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.widget.CurrentSpeedView;

/**
 * Velocità e limite in guida (tavola MapVelocita). La velocità diventa un tondo rosso quando supera il limite più la
 * tolleranza scelta (0, +5 km/h, +10%; di base +5), con un suono breve all'inizio del superamento; il cartello del
 * limite resta bianco col bordo rosso. Quando il limite cambia il cartello pulsa e, se lo si vuole, suona due volte.
 * Limite dalle mappe (OpenStreetMap), velocità dal GPS: niente fotocamera, niente servizi esterni.
 */
public final class EuriseSpeed
{
  public static final String PREF_TOLERANCE = "eurise_speed_tolerance";
  public static final String PREF_SOUND = "eurise_speed_sound";
  public static final String PREF_LIMIT_NOTICE = "eurise_limit_notice";

  /** Un altro suono di superamento non prima di 20 secondi, anche se si scende e si risale sopra il limite. */
  private static final long SOUND_GAP_MS = 20_000;

  private static int sLastLimit = -1;
  private static boolean sOver;
  private static long sLastBeep;
  @Nullable private static ToneGenerator sTone;

  private EuriseSpeed() {}

  /** Limite e velocità nelle unità mostrate (km/h o mph) → si supera? Pura, si prova senza telefono. */
  public static boolean isOver(int limit, int speed, @NonNull String tolerance)
  {
    if (limit <= 0 || speed < 0)
      return false;
    final int margin = switch (tolerance)
    {
      case "0" -> 0;
      case "10p" -> Math.round(limit * 0.10f);
      default -> 5;
    };
    return speed > limit + margin;
  }

  /**
   * A ogni aggiornamento della guida. [limitMps] e [speedMps] come li dà CoMaps (negativi se non si sanno). Restituisce
   * il limite da mostrare nelle unità dell'utente (o -1).
   */
  public static int apply(@NonNull View limitSign, @NonNull CurrentSpeedView speed, double limitMps, double speedMps)
  {
    final Context c = limitSign.getContext();
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
    final int limit = limitMps > 0 ? StringUtils.nativeFormatSpeed(limitMps) : -1;
    final int current = speedMps >= 0 ? StringUtils.nativeFormatSpeed(speedMps) : -1;

    final boolean over = isOver(limit, current, p.getString(PREF_TOLERANCE, "5"));
    speed.setAlertTextColor(0xFFFFFFFF);
    speed.setAlert(over);
    if (over && !sOver && p.getBoolean(PREF_SOUND, true))
    {
      final long now = SystemClock.elapsedRealtime();
      if (now - sLastBeep > SOUND_GAP_MS)
      {
        sLastBeep = now;
        beep(ToneGenerator.TONE_PROP_BEEP, 150);
      }
    }
    sOver = over;

    if (limit > 0 && sLastLimit > 0 && limit != sLastLimit && p.getBoolean(PREF_LIMIT_NOTICE, true))
    {
      limitSign.animate().cancel();
      limitSign.setScaleX(1.25f);
      limitSign.setScaleY(1.25f);
      limitSign.animate().scaleX(1f).scaleY(1f).setDuration(900).start();
      beep(ToneGenerator.TONE_PROP_BEEP2, 220);
    }
    if (limit > 0)
      sLastLimit = limit;
    return limit;
  }

  /** A fine guida: il prossimo viaggio riparte senza ricordi. */
  public static void reset()
  {
    sLastLimit = -1;
    sOver = false;
  }

  private static void beep(int tone, int ms)
  {
    try
    {
      if (sTone == null)
        sTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
      sTone.startTone(tone, ms);
    }
    catch (RuntimeException ignored)
    {
      // Nessun suono disponibile (altra app con l'audio esclusivo): resta il colore.
    }
  }
}
