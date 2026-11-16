package app.organicmaps.eurise;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import app.organicmaps.sdk.Framework;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cielo e colline in fondo alla mappa in guida (tavola MapCieloColline).
 *
 * <p>Qui si disegna l'immagine, con i colori e i pezzi di {@link EuriseLandscape} (cielo dal meteo, sole o luna, nuvole,
 * colline e alberelli della stagione, la sera più scuro); il motore (drape_frontend/eurise_sky) la stende sopra il bordo
 * lontano della mappa, che in guida si inclina di più. La riga {@link #HORIZON_V} dell'immagine cade sul bordo: sopra il
 * cielo, sotto solo colline. Si rifà quando cambia la finestra (rotazione) e ogni cinque minuti (momento, meteo); spento
 * l'interruttore o finita la guida, il motore torna alla vista di CoMaps. Niente rete: il meteo è quello della guida.
 */
public final class EuriseSky
{
  public static final String PREF = "eurise_sky_map";

  /** Come kEuriseSkyHorizonV in eurise_sky.hpp. */
  private static final float HORIZON_V = 0.8f;
  /** Quota di schermo che il cielo occupa in guida (circa: dipende dall'inclinazione in screenbase.cpp). */
  private static final float SKY_SHARE = 0.27f;
  /** Larghezza della tavola: le misure dei pezzi di EuriseLandscape sono pensate su questa. */
  private static final float UNITS = 390f;
  private static final long REFRESH_MS = 5 * 60_000L;

  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

  private static boolean sGuide;
  private static boolean sShown;
  private static long sLastMs;
  private static int sLastW, sLastH;

  private EuriseSky() {}

  /** La guida si apre o si chiude (NavigationController.show). */
  public static synchronized void guide(@NonNull Context c, boolean on)
  {
    if (sGuide == on)
      return;
    sGuide = on;
    if (on)
      refresh(c.getApplicationContext());
    else
      hide();
  }

  /** A ogni aggiornamento della guida: rifà l'immagine solo se serve. */
  public static synchronized void tick(@NonNull Context c)
  {
    if (!sGuide)
      return;
    final boolean wanted = PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF, true);
    if (!wanted)
    {
      hide();
      return;
    }
    final DisplayMetrics dm = c.getResources().getDisplayMetrics();
    if (!sShown || dm.widthPixels != sLastW || dm.heightPixels != sLastH
        || SystemClock.elapsedRealtime() - sLastMs > REFRESH_MS)
      refresh(c.getApplicationContext());
  }

  private static void hide()
  {
    if (!sShown)
      return;
    sShown = false;
    WORKER.execute(() -> Framework.nativeEuriseSetSky(null, 0, 0));
  }

  private static void refresh(@NonNull Context app)
  {
    if (!PreferenceManager.getDefaultSharedPreferences(app).getBoolean(PREF, true))
      return;
    final DisplayMetrics dm = app.getResources().getDisplayMetrics();
    sLastW = dm.widthPixels;
    sLastH = dm.heightPixels;
    sLastMs = SystemClock.elapsedRealtime();
    sShown = true;
    final int screenW = dm.widthPixels, screenH = dm.heightPixels;
    // Lo stato legge «I tuoi giorni» dall'app Eurise: fuori dal thread principale.
    WORKER.execute(() -> {
      final Bitmap b = draw(EuriseLandscape.now(app), screenW, screenH);
      final int[] px = new int[b.getWidth() * b.getHeight()];
      b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
      synchronized (EuriseSky.class)
      {
        if (sGuide && sShown)
          Framework.nativeEuriseSetSky(px, b.getWidth(), b.getHeight());
      }
      b.recycle();
    });
  }

  /**
   * L'immagine: mezza risoluzione dello schermo (il motore la stira con il filtro lineare), alta quanto il cielo più la
   * striscia di colline sotto il bordo.
   */
  @NonNull
  static Bitmap draw(@NonNull EuriseLandscape.State s, int screenW, int screenH)
  {
    final int bw = Math.max(64, Math.min(720, screenW / 2));
    final float skyPx = screenH * SKY_SHARE / HORIZON_V;
    final int bh = Math.max(32, Math.round(bw * skyPx / Math.max(1, screenW)));
    final Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    final Canvas k = new Canvas(b);
    // Si disegna in unità della tavola (larghezza 390): nuvole, sole, alberelli e tratti hanno le misure di MapCieloColline.
    final float u = bw / UNITS;
    k.scale(u, u);
    final float hUnits = bh / u, horizon = hUnits * HORIZON_V;

    EuriseLandscape.sky(k, s, UNITS, horizon, 0f);
    EuriseLandscape.precip(k, s, UNITS, horizon, 0f, null);
    hills(k, s, horizon, hUnits);

    final float shade = EuriseLandscape.shade(s);
    if (shade > 0)
    {
      final Paint p = new Paint();
      p.setColor(0xFF0E1424);
      p.setAlpha((int) (255 * shade));
      k.drawRect(0, 0, UNITS, hUnits, p);
    }
    return b;
  }

  /**
   * La fascia dell'orizzonte mentre si esplora (tavola MapEsplora): stessi pezzi della guida, su un'immagine larga quanto
   * lo schermo (a metà risoluzione) e alta quanto la fascia; le colline occupano l'ultimo quinto. Si disegna sul lavoratore
   * del cielo (lo stato legge «I tuoi giorni» dall'app Eurise) e torna sul thread principale con {@code done}.
   */
  public static void band(@NonNull Context c, int width, int height, @NonNull java.util.function.Consumer<Bitmap> done)
  {
    final Context app = c.getApplicationContext();
    final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    WORKER.execute(() -> {
      final int bw = Math.max(64, Math.min(720, width / 2));
      final int bh = Math.max(16, Math.round(bw * (float) height / Math.max(1, width)));
      final Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
      final Canvas k = new Canvas(b);
      final float u = bw / UNITS;
      k.scale(u, u);
      final float hUnits = bh / u, horizon = hUnits * 0.8f;
      final EuriseLandscape.State s = EuriseLandscape.now(app);
      EuriseLandscape.sky(k, s, UNITS, horizon, 0f);
      EuriseLandscape.precip(k, s, UNITS, horizon, 0f, null);
      hills(k, s, horizon, hUnits);
      final float shade = EuriseLandscape.shade(s);
      if (shade > 0)
      {
        final Paint p = new Paint();
        p.setColor(0xFF0E1424);
        p.setAlpha((int) (255 * shade));
        k.drawRect(0, 0, UNITS, hUnits, p);
      }
      main.post(() -> done.accept(b));
    });
  }

  /** Due profili di colline (lontano più chiaro, vicino più scuro) e quattro alberelli, come nella tavola. */
  private static void hills(@NonNull Canvas k, @NonNull EuriseLandscape.State s, float horizon, float bottom)
  {
    // Nella tavola il bordo della mappa sta a 236 e il cielo è alto 236: le quote si scalano con l'altezza del cielo.
    final float q = horizon / 236f;
    final int[] c = hillColors(s);
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    final Path far = new Path();
    far.moveTo(0, horizon + 2 * q);
    far.quadTo(50, horizon - 30 * q, 110, horizon - 14 * q);
    far.quadTo(170, horizon + 2 * q, 230, horizon - 22 * q);
    far.quadTo(310, horizon - 34 * q, UNITS, horizon - 10 * q);
    far.lineTo(UNITS, bottom);
    far.lineTo(0, bottom);
    far.close();
    p.setColor(c[0]);
    k.drawPath(far, p);

    final Path near = new Path();
    near.moveTo(0, horizon + 4 * q);
    near.quadTo(80, horizon - 12 * q, 170, horizon - 2 * q);
    near.quadTo(280, horizon + 8 * q, UNITS, horizon - 4 * q);
    near.lineTo(UNITS, bottom);
    near.lineTo(0, bottom);
    near.close();
    p.setColor(c[1]);
    k.drawPath(near, p);

    final float z = 5.5f * Math.min(1.4f, Math.max(0.7f, q));
    EuriseLandscape.trees(k, s, new float[][] {{40, horizon - 6 * q, z}, {64, horizon - 10 * q, z * 0.85f},
                                               {330, horizon - 8 * q, z}, {352, horizon - 6 * q, z * 0.85f}});
  }

  /** Colline lontane e vicine: il colore della stagione, le lontane schiarite verso il fondo del cielo; la neve le imbianca. */
  @NonNull
  private static int[] hillColors(@NonNull EuriseLandscape.State s)
  {
    if (s.tempo == EuriseLandscape.Tempo.NEVE)
      return new int[] {0xFFDCE2E8, 0xFFC9D1DA};
    final int[] base = switch (s.stagione)
    {
      case PRIMAVERA -> new int[] {0xFFA9BE96, 0xFF93AA80};
      case ESTATE -> new int[] {0xFFB7B888, 0xFFA3A574};
      case INVERNO -> new int[] {0xFFB4B8AE, 0xFFA0A59A};
      default -> new int[] {0xFFB9A889, 0xFFA89878}; // autunno, come la tavola
    };
    final int[] day = {mix(base[0], EuriseLandscape.skyBottom(s), 0.35f), base[1]};
    // Di sera e di notte le colline prendono i toni del cielo (blu-grafite della suite): il velo scuro sopra un colore
    // di giorno lasciava un marrone strano sull'orizzonte (prova di Mappe 58).
    return switch (s.momento)
    {
      case NOTTE -> new int[] {mix(day[0], 0xFF2B3554, 0.8f), mix(day[1], 0xFF1F2A44, 0.85f)};
      case SERA -> new int[] {mix(day[0], 0xFF6A6585, 0.45f), mix(day[1], 0xFF4A4660, 0.45f)};
      default -> day;
    };
  }

  private static int mix(int a, int b, float t)
  {
    final int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
    final int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
    final int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
    return 0xFF000000 | (r << 16) | (g << 8) | bl;
  }
}
