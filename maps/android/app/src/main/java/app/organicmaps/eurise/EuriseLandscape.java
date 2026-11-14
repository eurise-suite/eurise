package app.organicmaps.eurise;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Calendar;
import java.util.Locale;

/**
 * Il paesaggio della guida (tavola MapCieli, approvata il 14 novembre): il cielo segue il tempo che fa (sette tempi) e
 * l'ora (giorno, sera, notte), prato e alberi la stagione, e nelle feste compare una piccola sorpresa. Stessi colori
 * tenui degli sfondi della Scrivania. Tutto disegnato, niente immagini.
 *
 * In movimento: le nuvole scorrono piano (più veloci con raffiche forti), nel temporale un lampo ogni tanto; pioggia,
 * neve e grandine cadono sul cielo e sul prato, **mai sulla strada** (la strada si passa in [precip] e resta fuori).
 * Il meteo è quello che la guida ha già (EuriseRouteWeather): nessun dato in più esce dal telefono; se è spento, il
 * cielo segue solo l'ora. Ogni parte si spegne in Impostazioni › Navigazione › Cielo e stagioni.
 *
 * Lo spazio di disegno è quello delle scene (390 × 260); [t] in secondi.
 */
public final class EuriseLandscape
{
  public enum Tempo { SERENO, POCO_NUVOLOSO, MOLTO_NUVOLOSO, PIOGGIA, TEMPORALE, GRANDINE, NEVE }
  public enum Momento { GIORNO, SERA, NOTTE }
  public enum Stagione { PRIMAVERA, ESTATE, AUTUNNO, INVERNO }
  public enum Festa { NESSUNA, NATALE, CAPODANNO, NAZIONALE, HALLOWEEN }

  public static final String PREF_SKY = "eurise_sky_weather", PREF_SEASONS = "eurise_seasons", PREF_FEASTS = "eurise_feasts",
                             PREF_YOUR_DAYS = "eurise_your_days";

  /** Cosa disegnare adesso. */
  public static final class State
  {
    public Tempo tempo = Tempo.SERENO;
    public Momento momento = Momento.GIORNO;
    public Stagione stagione = Stagione.AUTUNNO;
    public Festa festa = Festa.NESSUNA;
    public boolean windy;
    /** I colori della festa nazionale del paese in cui si guida (tre scie di fumo). */
    public int[] flag = {0xFF3E9B5C, 0xFFFFFFFF, 0xFFD9544F};
  }

  private EuriseLandscape() {}

  // ---- Lo stato, dal meteo e dall'orologio ----------------------------------------------------------------------

  @NonNull
  public static State now(@NonNull Context c)
  {
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
    final State s = new State();
    final Calendar cal = Calendar.getInstance();
    final int month = cal.get(Calendar.MONTH); // 0 = gennaio
    final int day = cal.get(Calendar.DAY_OF_MONTH);
    final float hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f;
    s.momento = momento(month, hour);
    if (p.getBoolean(PREF_SKY, true))
    {
      final float[] w = EuriseRouteWeather.now();
      if (w != null)
      {
        s.tempo = tempo((int) w[0]);
        s.windy = w[1] >= 40f;
      }
    }
    s.stagione = p.getBoolean(PREF_SEASONS, true) ? stagione(month) : Stagione.ESTATE;
    if (p.getBoolean(PREF_FEASTS, true))
      feast(s, month, day, Locale.getDefault().getCountry());
    return s;
  }

  /** Codici WMO (quelli di Open-Meteo) → i sette tempi della tavola. */
  @NonNull
  static Tempo tempo(int code)
  {
    if (code == 0) return Tempo.SERENO;
    if (code == 1 || code == 2) return Tempo.POCO_NUVOLOSO;
    if (code == 3 || code == 45 || code == 48) return Tempo.MOLTO_NUVOLOSO;
    if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Tempo.NEVE;
    if (code == 96 || code == 99) return Tempo.GRANDINE;
    if (code == 95) return Tempo.TEMPORALE;
    if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return Tempo.PIOGGIA;
    return Tempo.POCO_NUVOLOSO;
  }

  /** Alba e tramonto medi per mese (ora locale, Europa centrale): la sera è l'ora intorno al tramonto. */
  private static final float[] SUNRISE = {7.6f, 7.2f, 6.6f, 6.6f, 6f, 5.6f, 5.8f, 6.3f, 7f, 7.5f, 7.1f, 7.5f};
  private static final float[] SUNSET = {17f, 17.6f, 18.4f, 20f, 20.5f, 21f, 20.9f, 20.4f, 19.5f, 18.5f, 17f, 16.7f};

  @NonNull
  static Momento momento(int month, float hour)
  {
    if (hour < SUNRISE[month] - 0.3f || hour > SUNSET[month] + 0.6f)
      return Momento.NOTTE;
    if (hour > SUNSET[month] - 1.2f)
      return Momento.SERA;
    return Momento.GIORNO;
  }

  @NonNull
  static Stagione stagione(int month)
  {
    if (month == 11 || month <= 1) return Stagione.INVERNO;
    if (month <= 4) return Stagione.PRIMAVERA;
    if (month <= 7) return Stagione.ESTATE;
    return Stagione.AUTUNNO;
  }

  private static void feast(@NonNull State s, int month, int day, @NonNull String country)
  {
    if ((month == 11 && day == 31) || (month == 0 && day == 1))
      s.festa = Festa.CAPODANNO;
    else if ((month == 11 && day >= 8) || (month == 0 && day <= 6))
      s.festa = Festa.NATALE;
    else if (month == 9 && day == 31)
      s.festa = Festa.HALLOWEEN;
    else
    {
      // La festa nazionale del paese del telefono, con i suoi colori.
      switch (country)
      {
      case "IT" -> national(s, month, day, 5, 2, 0xFF3E9B5C, 0xFFFFFFFF, 0xFFD9544F);
      case "FR" -> national(s, month, day, 6, 14, 0xFF3E5FA8, 0xFFFFFFFF, 0xFFD9544F);
      case "DE" -> national(s, month, day, 9, 3, 0xFF3A3A3A, 0xFFD9544F, 0xFFE9C24A);
      case "ES" -> national(s, month, day, 9, 12, 0xFFD9544F, 0xFFE9C24A, 0xFFD9544F);
      case "AT" -> national(s, month, day, 9, 26, 0xFFD9544F, 0xFFFFFFFF, 0xFFD9544F);
      case "CH" -> national(s, month, day, 7, 1, 0xFFD9544F, 0xFFFFFFFF, 0xFFD9544F);
      case "BE" -> national(s, month, day, 6, 21, 0xFF3A3A3A, 0xFFE9C24A, 0xFFD9544F);
      case "PT" -> national(s, month, day, 5, 10, 0xFF3E9B5C, 0xFFE9C24A, 0xFFD9544F);
      case "NL" -> national(s, month, day, 3, 27, 0xFFD9544F, 0xFFFFFFFF, 0xFF3E5FA8);
      default -> { }
      }
    }
  }

  private static void national(@NonNull State s, int month, int day, int m, int d, int a, int b, int c)
  {
    if (month == m && day == d)
    {
      s.festa = Festa.NAZIONALE;
      s.flag = new int[] {a, b, c};
    }
  }

  // ---- I colori --------------------------------------------------------------------------------------------------

  /** Cielo (alto, basso) e nuvole per tempo e momento: gli stessi della tavola. */
  private static int[] palette(@NonNull State s)
  {
    final int m = s.momento.ordinal();
    final int[][][] P = {
        {{0xFF6FB4E6, 0xFFD6ECF7, 0xFFFFFFFF}, {0xFF3E3A5C, 0xFFE0A27A, 0xFFE8B9A5}, {0xFF0E1424, 0xFF2B3554, 0xFF3A4466}},
        {{0xFF7DB8E2, 0xFFDCEBF4, 0xFFFFFFFF}, {0xFF46405F, 0xFFD99E7E, 0xFFE2B4A2}, {0xFF111829, 0xFF2D3756, 0xFF3A4466}},
        {{0xFFAEBBCD, 0xFFE6E3E2, 0xFFF2F1EE}, {0xFF5A5370, 0xFFC99A86, 0xFF9A8B9C}, {0xFF1A2133, 0xFF36405A, 0xFF2E3850}},
        {{0xFF8E9BAE, 0xFFC9D0D9, 0xFFB7C0CC}, {0xFF4A4660, 0xFF9C8592, 0xFF6E6680}, {0xFF141B2B, 0xFF2A3248, 0xFF262E44}},
        {{0xFF5E6A80, 0xFFA3ACBA, 0xFF6B7588}, {0xFF36324A, 0xFF7C6878, 0xFF4E4860}, {0xFF0B101C, 0xFF1F2639, 0xFF1A2030}},
        {{0xFF7C889C, 0xFFBCC4CF, 0xFF8E98A8}, {0xFF433F58, 0xFF8F7B88, 0xFF5E5872}, {0xFF111726, 0xFF262E42, 0xFF20283A}},
        {{0xFFC9D2DE, 0xFFEEF1F5, 0xFFE6EAF0}, {0xFF6A6585, 0xFFD9C2C0, 0xFFB9AFC0}, {0xFF1E2538, 0xFF3C4660, 0xFF3A4460}},
    };
    return P[s.tempo.ordinal()][m];
  }

  /** Il prato, alto e basso: dalla stagione; la neve lo imbianca. */
  @NonNull
  public static int[] ground(@NonNull State s)
  {
    if (s.tempo == Tempo.NEVE) return new int[] {0xFFE9EDF1, 0xFFF6F8FA};
    switch (s.stagione)
    {
    case PRIMAVERA: return new int[] {0xFFCFDCC6, 0xFFE6EDDD};
    case ESTATE: return new int[] {0xFFD5D7B8, 0xFFECEBD6};
    case AUTUNNO: return new int[] {0xFFD9D2BC, 0xFFEDE6D6};
    default: return new int[] {0xFFDDE2E4, 0xFFEEF1F2};
    }
  }

  /** Quanto scurire la scena (sera, notte, maltempo). */
  public static float shade(@NonNull State s)
  {
    final float base = s.momento == Momento.NOTTE ? 0.36f : s.momento == Momento.SERA ? 0.15f : 0f;
    final float dark = switch (s.tempo)
    {
      case MOLTO_NUVOLOSO -> 0.03f;
      case PIOGGIA -> 0.07f;
      case TEMPORALE -> 0.14f;
      case GRANDINE -> 0.10f;
      default -> 0f;
    };
    return base + dark;
  }

  /** Le finestre dei palazzi: bianche di giorno, accese di sera e di notte. */
  public static int windows(@NonNull State s)
  {
    return s.momento == Momento.GIORNO ? 0x99FFFFFF : 0xEBF6D88C;
  }

  // ---- Il cielo --------------------------------------------------------------------------------------------------

  /** Cielo, sole o luna e stelle, sorprese delle feste, nuvole che scorrono e il lampo, fino a [horizon]. */
  public static void sky(@NonNull Canvas k, @NonNull State s, float w, float horizon, float t)
  {
    final int[] pal = palette(s);
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setShader(new LinearGradient(0, 0, 0, horizon, pal[0], pal[1], Shader.TileMode.CLAMP));
    k.drawRect(-w, -horizon, 2 * w, horizon, p);
    p.setShader(null);
    final float h = horizon;
    final Rnd r = new Rnd(11);
    final boolean clear = s.tempo == Tempo.SERENO || s.tempo == Tempo.POCO_NUVOLOSO;

    if (s.momento == Momento.NOTTE)
    {
      if (clear)
      {
        for (int i = 0; i < 16; i++)
          circle(k, p, r.f() * w, r.f() * h * 0.75f, 0.6f + r.f() * 0.9f, 0xFFE9E4D6, 0.5f + r.f() * 0.4f);
        circle(k, p, w * 0.82f, h * 0.26f, h * 0.11f, 0xFFE9E4D6, 0.95f);
        circle(k, p, w * 0.82f + h * 0.05f, h * 0.23f, h * 0.1f, pal[0], 1f);
      }
    }
    else if (clear || s.tempo == Tempo.NEVE)
    {
      final boolean sera = s.momento == Momento.SERA;
      circle(k, p, w * (sera ? 0.8f : 0.78f), h * (sera ? 0.82f : 0.3f), h * 0.13f, sera ? 0xFFF2B98E : 0xFFFFF3D6,
             s.tempo == Tempo.NEVE ? 0.45f : 0.95f);
    }

    feastSky(k, p, s, w, h);

    // Nuvole: avanti e indietro piano; con le raffiche forti più in fretta.
    final int n = switch (s.tempo)
    {
      case SERENO -> 0;
      case POCO_NUVOLOSO -> 2;
      case TEMPORALE -> 6;
      default -> 5;
    };
    final float period = s.windy ? 9f : 38f;
    final float drift = w * (s.windy ? 0.16f : 0.07f) * (float) Math.sin(2 * Math.PI * t / period);
    for (int i = 0; i < n; i++)
    {
      final float cx = (i + 0.5f) / n * w + (r.f() - 0.5f) * w * 0.12f + drift;
      final float cy = h * (0.15f + r.f() * 0.45f);
      final float rx = w * (n > 3 ? 0.13f : 0.11f), ry = h * 0.09f;
      oval(k, p, cx, cy, rx, ry, pal[2], 0.92f);
      oval(k, p, cx + rx * 0.35f, cy - ry * 0.7f, rx * 0.55f, ry * 0.9f, pal[2], 0.92f);
      oval(k, p, cx - rx * 0.4f, cy - ry * 0.35f, rx * 0.45f, ry * 0.75f, pal[2], 0.92f);
    }

    // Il lampo: ogni sette secondi, un attimo, con un tremolio.
    if (s.tempo == Tempo.TEMPORALE)
    {
      final float f = (t % 7f) / 7f;
      final float a = f < 0.70f || f > 0.80f ? 0f : (f < 0.73f || (f > 0.75f && f < 0.78f)) ? 1f : 0.2f;
      if (a > 0)
      {
        final float x = w * 0.36f, y = h * 0.3f;
        final Path b = new Path();
        b.moveTo(x, y);
        b.rLineTo(-w * 0.03f, h * 0.22f);
        b.rLineTo(w * 0.025f, 0);
        b.rLineTo(-w * 0.025f, h * 0.25f);
        b.rLineTo(w * 0.06f, -h * 0.3f);
        b.rLineTo(-w * 0.025f, 0);
        b.rLineTo(w * 0.02f, -h * 0.17f);
        b.close();
        p.setColor(0xFFFFE9A6);
        p.setAlpha((int) (242 * a));
        k.drawPath(b, p);
        p.setColor(Color.WHITE);
        p.setAlpha((int) (40 * a));
        k.drawRect(-w, -h, 2 * w, h, p);
      }
    }
  }

  private static void feastSky(@NonNull Canvas k, @NonNull Paint p, @NonNull State s, float w, float h)
  {
    if (s.festa == Festa.CAPODANNO && s.momento != Momento.GIORNO)
    {
      final float[][] bursts = {{0.2f, 0.3f}, {0.5f, 0.2f}, {0.78f, 0.38f}};
      final int[] colors = {0xFFF2C94C, 0xFFE57373, 0xFF81C7E8};
      for (int j = 0; j < bursts.length; j++)
      {
        final float[] b = bursts[j];
        final int c = colors[j];
        for (int i = 0; i < 12; i++)
        {
          final double a = i / 12.0 * Math.PI * 2;
          circle(k, p, w * b[0] + (float) Math.cos(a) * h * 0.12f, h * b[1] + (float) Math.sin(a) * h * 0.12f, 1.6f, c, 0.95f);
        }
        circle(k, p, w * b[0], h * b[1], 2f, c, 0.9f);
      }
    }
    if (s.festa == Festa.NAZIONALE)
    {
      final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
      st.setStyle(Paint.Style.STROKE);
      st.setStrokeCap(Paint.Cap.ROUND);
      st.setStrokeWidth(Math.max(3f, h * 0.045f));
      for (int i = 0; i < 3; i++)
      {
        final Path trail = new Path();
        trail.moveTo(-10, h * (0.62f + i * 0.07f));
        trail.cubicTo(w * 0.3f, h * (0.2f + i * 0.07f), w * 0.6f, h * (0.22f + i * 0.07f), w + 10, h * (0.08f + i * 0.07f));
        st.setColor(s.flag[i]);
        st.setAlpha(217);
        k.drawPath(trail, st);
      }
    }
  }

  // ---- Alberi ----------------------------------------------------------------------------------------------------

  /** Gli alberelli dietro i palazzi: [trees] è un elenco di {x, base, misura}. */
  public static void trees(@NonNull Canvas k, @NonNull State s, @NonNull float[][] trees)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Rnd r = new Rnd(5);
    final boolean conifer = s.festa == Festa.NATALE || (s.stagione == Stagione.INVERNO && s.festa != Festa.HALLOWEEN);
    for (int i = 0; i < trees.length; i++)
    {
      final float x = trees[i][0], y = trees[i][1], z = trees[i][2];
      p.setColor(0xFF8A7A68);
      k.drawRect(x - z * 0.12f, y - z * 1.1f, x + z * 0.12f, y, p);
      if (conifer)
      {
        final Path c = new Path();
        c.moveTo(x, y - z * 2.6f);
        c.lineTo(x + z * 0.9f, y - z * 0.7f);
        c.lineTo(x - z * 0.9f, y - z * 0.7f);
        c.close();
        p.setColor(0xFF7F9A8A);
        k.drawPath(c, p);
        if (s.tempo == Tempo.NEVE)
        {
          final Path cap = new Path();
          cap.moveTo(x, y - z * 2.6f);
          cap.lineTo(x + z * 0.35f, y - z * 1.85f);
          cap.lineTo(x - z * 0.35f, y - z * 1.85f);
          cap.close();
          p.setColor(0xFFF6F8FA);
          k.drawPath(cap, p);
        }
      }
      else if (s.stagione == Stagione.PRIMAVERA)
      {
        circle(k, p, x, y - z * 1.6f, z, i % 2 == 1 ? 0xFF93B38C : 0xFFA9C4A0, 1f);
        for (int b = 0; b < 4; b++)
          circle(k, p, x + (r.f() - 0.5f) * z * 1.4f, y - z * 1.6f + (r.f() - 0.5f) * z * 1.2f, z * 0.13f, 0xFFF2CFD8, 1f);
      }
      else if (s.stagione == Stagione.ESTATE)
      {
        circle(k, p, x, y - z * 1.7f, z * 1.1f, i % 2 == 1 ? 0xFF7E9F70 : 0xFF8FAE7E, 1f);
        circle(k, p, x - z * 0.3f, y - z * 2f, z * 0.45f, 0xFFA6C295, 0.8f);
      }
      else if (s.stagione == Stagione.AUTUNNO)
      {
        final int[] leaves = {0xFFD8A878, 0xFFC98C5E, 0xFFC9B06A};
        circle(k, p, x, y - z * 1.6f, z, leaves[i % 3], 1f);
        circle(k, p, x + z * 0.35f, y - z * 1.35f, z * 0.5f, 0xFFC98C5E, 0.8f);
      }
      else
      {
        final Paint br = new Paint(Paint.ANTI_ALIAS_FLAG);
        br.setStyle(Paint.Style.STROKE);
        br.setStrokeCap(Paint.Cap.ROUND);
        br.setStrokeWidth(z * 0.16f);
        br.setColor(0xFF8A7A68);
        k.drawLine(x, y - z, x, y - z * 2.2f, br);
        k.drawLine(x, y - z * 1.5f, x - z * 0.6f, y - z * 2.1f, br);
        k.drawLine(x, y - z * 1.3f, x + z * 0.6f, y - z * 1.9f, br);
      }
      if (s.tempo == Tempo.NEVE && !conifer)
        oval(k, p, x, y - z * 2.4f, z * 0.6f, z * 0.2f, 0xFFF6F8FA, 1f);
      if (s.festa == Festa.NATALE)
      {
        final int[] lights = {0xFFF2C94C, 0xFFE57373, 0xFF81C784, 0xFF81C7E8};
        for (int l = 0; l < 4; l++)
          circle(k, p, x + (l % 2 == 1 ? 1 : -1) * z * (0.25f + l * 0.1f), y - z * (1.0f + l * 0.38f), Math.max(1f, z * 0.09f), lights[l], 1f);
        circle(k, p, x, y - z * 2.65f, Math.max(1.4f, z * 0.14f), 0xFFF2C94C, 1f);
      }
      if (s.festa == Festa.HALLOWEEN && i % 2 == 0)
      {
        oval(k, p, x + z * 0.7f, y - z * 0.2f, z * 0.36f, z * 0.26f, 0xFFE8913A, 1f);
        p.setColor(0xFF6F8F5E);
        k.drawRect(x + z * 0.67f, y - z * 0.55f, x + z * 0.74f, y - z * 0.43f, p);
      }
    }
  }

  // ---- Pioggia, neve, grandine -----------------------------------------------------------------------------------

  /**
   * Le precipitazioni che cadono su tutta l'altezza [h], tranne che sulla strada [road] (mai sulla strada: leggibilità
   * prima di tutto). Senza strada, cadono ovunque nell'area.
   */
  public static void precip(@NonNull Canvas k, @NonNull State s, float w, float h, float t, @Nullable Path road)
  {
    final int drops = switch (s.tempo)
    {
      case PIOGGIA -> 26;
      case TEMPORALE -> 40;
      case GRANDINE -> 14;
      default -> 0;
    };
    final boolean snow = s.tempo == Tempo.NEVE, hail = s.tempo == Tempo.GRANDINE;
    if (drops == 0 && !snow && !hail)
      return;
    k.save();
    if (road != null)
    {
      if (Build.VERSION.SDK_INT >= 26)
        k.clipOutPath(road);
      else
        //noinspection deprecation
        k.clipPath(road, Region.Op.DIFFERENCE);
    }
    final Rnd r = new Rnd(23);
    final float fall = switch (s.tempo)
    {
      case NEVE -> h * 0.05f;
      case GRANDINE -> h * 0.9f;
      case TEMPORALE -> h * 0.8f;
      default -> h * 0.6f;
    };
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    line.setStyle(Paint.Style.STROKE);
    line.setStrokeCap(Paint.Cap.ROUND);
    line.setColor(0xA6E8EEF5);
    line.setStrokeWidth(s.tempo == Tempo.TEMPORALE ? 1.6f : 1.3f);
    final float len = Math.max(6f, h * 0.04f);
    for (int i = 0; i < drops; i++)
    {
      final float x0 = r.f() * w, y0 = r.f() * h;
      final float y = (y0 + t * fall) % h, x = x0 - (y - y0) * 0.1f;
      k.drawLine(x, y, x - len * 0.35f, y + len, line);
    }
    final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    if (hail)
      for (int i = 0; i < 30; i++)
      {
        final float x0 = r.f() * w, y0 = r.f() * h, z = 1.4f + r.f() * 1.2f;
        circle(k, dot, x0, (y0 + t * fall * 1.2f) % h, z, Color.WHITE, 0.9f);
      }
    if (snow)
      for (int i = 0; i < 40; i++)
      {
        final float x0 = r.f() * w, y0 = r.f() * h, z = 1.2f + r.f() * 1.6f;
        final float y = (y0 + t * fall * (0.7f + z * 0.2f)) % h;
        circle(k, dot, x0 + (float) Math.sin(t * 0.8f + i) * 3f, y, z, Color.WHITE, 0.85f);
      }
    k.restore();
  }

  // ---- Pezzi ---------------------------------------------------------------------------------------------------

  private static void circle(@NonNull Canvas k, @NonNull Paint p, float x, float y, float rad, int color, float alpha)
  {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    p.setAlpha((int) (Color.alpha(color) * alpha));
    k.drawCircle(x, y, rad, p);
  }

  private static void oval(@NonNull Canvas k, @NonNull Paint p, float x, float y, float rx, float ry, int color, float alpha)
  {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    p.setAlpha((int) (Color.alpha(color) * alpha));
    k.drawOval(new RectF(x - rx, y - ry, x + rx, y + ry), p);
  }

  /** Numeri a caso ma sempre gli stessi: le nuvole e le gocce non saltano da un fotogramma all'altro. */
  private static final class Rnd
  {
    private long s;

    Rnd(long seed) { s = seed; }

    float f()
    {
      s = (s * 16807) % 2147483647;
      return (s % 10000) / 10000f;
    }
  }
}
