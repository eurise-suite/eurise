package app.organicmaps.eurise;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I calcoli della bolla della musica e delle chiamate in guida (tavole MapBollaAuto, MapBollaTelefono), in Java puro
 * perché si provano senza telefono (maps/test/…/NowPlayingTextTest.java): i tempi «1:42», «−2:13», «di 3:55», la
 * posizione della canzone adesso, e le righe del testo sincronizzato (formato LRC, «[01:42.30] parole»).
 */
public final class NowPlayingText
{
  private NowPlayingText() {}

  /** 102_000 → «1:42»; oltre l'ora «1:02:05». Negativo → 0. */
  public static String clock(long ms)
  {
    long s = Math.max(0, ms) / 1000;
    final long h = s / 3600;
    final long m = (s % 3600) / 60;
    s %= 60;
    if (h > 0)
      return h + ":" + two(m) + ":" + two(s);
    return m + ":" + two(s);
  }

  /** Quanto manca, col segno meno tipografico: «−2:13». */
  public static String remaining(long positionMs, long durationMs)
  {
    return "−" + clock(durationMs - positionMs);
  }

  /**
   * Dove è la canzone adesso: la posizione data dall'app [position] al momento [updatedAt] (stesso orologio di [now]),
   * più il tempo passato per la velocità, se suona. Mai oltre la durata, se è nota.
   */
  public static long positionAt(long position, long updatedAt, float speed, boolean playing, long durationMs, long now)
  {
    long p = position;
    if (playing && updatedAt > 0 && now > updatedAt)
      p += (long) ((now - updatedAt) * (speed > 0 ? speed : 1f));
    if (durationMs > 0)
      p = Math.min(p, durationMs);
    return Math.max(0, p);
  }

  /** La percentuale della barra (0–1000), 0 se la durata non c'è. */
  public static int progress(long positionMs, long durationMs)
  {
    if (durationMs <= 0)
      return 0;
    return (int) Math.max(0, Math.min(1000, positionMs * 1000 / durationMs));
  }

  /** «Album · 2019» con le parti che ci sono; vuota se nessuna. */
  public static String albumLine(String album, String year)
  {
    final StringBuilder b = new StringBuilder();
    if (album != null && !album.trim().isEmpty())
      b.append(album.trim());
    final String y = year(year);
    if (y != null)
    {
      if (b.length() > 0)
        b.append(" · ");
      b.append(y);
    }
    return b.toString();
  }

  private static final Pattern YEAR = Pattern.compile("(1[89]\\d\\d|20\\d\\d)");

  /** L'anno da una data qualsiasi delle app («2019-05-03», «2019», «20190503T…»); null se non si trova. */
  public static String year(String date)
  {
    if (date == null)
      return null;
    final Matcher m = YEAR.matcher(date);
    return m.find() ? m.group(1) : null;
  }

  /** Le righe di un testo sincronizzato, in ordine di tempo. */
  public static final class Lyrics
  {
    public final long[] times;
    public final String[] lines;

    Lyrics(long[] times, String[] lines)
    {
      this.times = times;
      this.lines = lines;
    }

    /** La riga che si canta a [ms], -1 prima della prima. */
    public int indexAt(long ms)
    {
      int lo = 0, hi = times.length - 1, found = -1;
      while (lo <= hi)
      {
        final int mid = (lo + hi) >>> 1;
        if (times[mid] <= ms)
        {
          found = mid;
          lo = mid + 1;
        }
        else
          hi = mid - 1;
      }
      return found;
    }

    /** La riga a [i] o null; le righe vuote (pause) restano vuote. */
    public String line(int i)
    {
      return i >= 0 && i < lines.length ? lines[i] : null;
    }

    public boolean isEmpty()
    {
      return times.length == 0;
    }
  }

  private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

  /**
   * Legge un testo LRC. Una riga può avere più tempi («[00:12.00][01:30.00] ritornello»); le righe senza tempo (titolo,
   * autore) si saltano. Null o vuoto → testo vuoto.
   */
  public static Lyrics parseLrc(String lrc)
  {
    final List<long[]> stamps = new ArrayList<>();
    final List<String> texts = new ArrayList<>();
    if (lrc != null)
    {
      for (String raw : lrc.split("\r?\n"))
      {
        final Matcher m = STAMP.matcher(raw);
        int end = 0;
        final List<Long> mine = new ArrayList<>();
        while (m.find() && m.start() == end)
        {
          final long min = Long.parseLong(m.group(1));
          final long sec = Long.parseLong(m.group(2));
          long frac = 0;
          final String f = m.group(3);
          if (f != null)
            frac = f.length() == 1 ? Long.parseLong(f) * 100 : f.length() == 2 ? Long.parseLong(f) * 10 : Long.parseLong(f);
          mine.add(min * 60_000 + sec * 1000 + frac);
          end = m.end();
        }
        if (mine.isEmpty())
          continue;
        final String text = raw.substring(end).trim();
        for (Long t : mine)
        {
          stamps.add(new long[] {t, stamps.size()});
          texts.add(text);
        }
      }
    }
    stamps.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
    final long[] times = new long[stamps.size()];
    final String[] lines = new String[stamps.size()];
    for (int i = 0; i < times.length; i++)
    {
      times[i] = stamps.get(i)[0];
      lines[i] = texts.get((int) stamps.get(i)[1]);
    }
    return new Lyrics(times, lines);
  }

  // ---- Le righe nuove della bolla (tavole MapBollaPodcast…MapBollaArrivo, approvate il 17 novembre) -----------------

  /** Da questa durata in su una traccia è una puntata o un capitolo, anche se l'app non è tra quelle note. */
  public static final long SPOKEN_MS = 20 * 60_000L;

  /**
   * Podcast o audiolibro: un'app nota per questo ([spokenApp]) oppure una durata lunga. Le canzoni più lunghe di 20
   * minuti sono rare e la riga d'arrivo resta comunque vera.
   */
  public static boolean spoken(long durationMs, boolean spokenApp)
  {
    return spokenApp || durationMs >= SPOKEN_MS;
  }

  /** Radio o diretta: suona senza una durata. */
  public static boolean live(long durationMs)
  {
    return durationMs <= 0;
  }

  /**
   * Quanto resterà della puntata quando arrivi: tempo che manca diviso per la velocità di ascolto, meno il tempo di
   * guida [etaInMs]. Negativo: finisce prima dell'arrivo (tanti millisecondi prima).
   */
  public static long leftAtArrival(long positionMs, long durationMs, float speed, long etaInMs)
  {
    final float s = speed > 0.1f ? speed : 1f;
    return Math.round(Math.max(0, durationMs - positionMs) / (double) s) - etaInMs;
  }

  /** Minuti interi arrotondati per eccesso, almeno 1: «mancheranno 10 min». */
  public static int minutesUp(long ms)
  {
    return (int) Math.max(1, (Math.max(0, ms) + 59_999) / 60_000);
  }

  /** «1,2×» per le velocità diverse da 1, vuota a velocità normale. */
  public static String speed(float speed)
  {
    if (speed <= 0 || Math.abs(speed - 1f) < 0.05f)
      return "";
    final int tenths = Math.round(speed * 10);
    return tenths % 10 == 0 ? (tenths / 10) + "×" : (tenths / 10) + "," + (tenths % 10) + "×";
  }

  /**
   * Arrivo condiviso per SMS: si manda un aggiornamento quando l'arrivo previsto [eta] si sposta di almeno 5 minuti
   * rispetto all'ultimo mandato [sentEta], e non prima di 10 minuti dall'ultimo SMS [sentAt].
   */
  public static boolean shareUpdate(long sentEta, long sentAt, long eta, long now)
  {
    return Math.abs(eta - sentEta) >= 5 * 60_000L && now - sentAt >= 10 * 60_000L;
  }

  /** Comincia tra adesso e [windowMs] (impegni a 30 minuti, timer e sveglie a 15). */
  public static boolean soon(long start, long now, long windowMs)
  {
    return start > now && start - now <= windowMs;
  }

  /**
   * Il nome di chi chiama compare nel titolo dell'impegno («Pranzo con Giulia» per «Giulia Rossi»): si guarda la prima
   * parola del nome, almeno tre lettere, come parola intera e senza badare alle maiuscole.
   */
  public static boolean nameIn(String title, String name)
  {
    if (title == null || name == null)
      return false;
    final String first = name.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
    if (first.length() < 3 || !Character.isLetter(first.charAt(0)))
      return false;
    return (" " + title.toLowerCase(java.util.Locale.ROOT) + " ")
        .matches("(?s).*[^\\p{L}]" + Pattern.quote(first) + "[^\\p{L}].*");
  }

  /** Il ritardo su un impegno, in minuti interi (0 se arrivi in tempo o prima). */
  public static int lateMinutes(long etaMillis, long startMillis)
  {
    return (int) Math.max(0, Math.round((etaMillis - startMillis) / 60_000.0));
  }

  /** Il numero di telefono da «tel:+39 333…» (o da un numero già pulito); null se non è un numero. */
  public static String phone(String uri)
  {
    if (uri == null)
      return null;
    String s = uri.trim();
    if (s.startsWith("tel:"))
      s = s.substring(4);
    try
    {
      s = java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
    }
    catch (Exception ignored)
    {
      // Resta com'era.
    }
    s = s.replaceAll("[\\s().-]", "");
    return s.matches("\\+?\\d{3,}") ? s : null;
  }

  /**
   * Il numero della strada da mostrare: senza la rete delle relazioni di OpenStreetMap («IT:national/SS7» → «SS7»),
   * più numeri separati da uno spazio, senza doppioni.
   */
  public static String roadRef(String raw)
  {
    if (raw == null)
      return "";
    final java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    for (String part : raw.split("[;\\s]+"))
    {
      final String p = part.substring(part.lastIndexOf('/') + 1).trim();
      if (!p.isEmpty())
        out.add(p);
    }
    return String.join(" ", out);
  }

  private static String two(long v)
  {
    return v < 10 ? "0" + v : Long.toString(v);
  }
}
