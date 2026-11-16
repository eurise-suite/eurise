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

  private static String two(long v)
  {
    return v < 10 ? "0" + v : Long.toString(v);
  }
}
