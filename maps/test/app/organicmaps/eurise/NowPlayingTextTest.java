package app.organicmaps.eurise;

/** Prove di {@link NowPlayingText} senza telefono (lanciate dal lavoro di Mappe prima di compilare). */
public final class NowPlayingTextTest
{
  private static int sFailed;

  public static void main(String[] args)
  {
    eq("1:42", NowPlayingText.clock(102_000), "minuti e secondi");
    eq("0:05", NowPlayingText.clock(5_400), "secondi con lo zero");
    eq("1:02:05", NowPlayingText.clock(3_725_000), "oltre l'ora");
    eq("0:00", NowPlayingText.clock(-3), "negativo");
    eq("−2:13", NowPlayingText.remaining(102_000, 235_000), "quanto manca");

    eq(12_000L, NowPlayingText.positionAt(10_000, 1_000, 1f, true, 0, 3_000), "suona: avanza");
    eq(10_000L, NowPlayingText.positionAt(10_000, 1_000, 1f, false, 0, 3_000), "in pausa: ferma");
    eq(235_000L, NowPlayingText.positionAt(230_000, 1_000, 1f, true, 235_000, 100_000), "mai oltre la durata");
    eq(434, NowPlayingText.progress(102_000, 235_000), "barra");
    eq(0, NowPlayingText.progress(5_000, 0), "barra senza durata");

    eq("Abbey Road · 1969", NowPlayingText.albumLine("Abbey Road", "1969-09-26"), "album e anno");
    eq("2019", NowPlayingText.albumLine(" ", "20190503T000000"), "solo anno");
    eq("", NowPlayingText.albumLine(null, "boh"), "niente");

    final NowPlayingText.Lyrics l = NowPlayingText.parseLrc(
        "[ti:Titolo]\n[ar:Autore]\n[00:01.50] prima\n[00:04.00][00:20.0] ritornello\n[00:10.123]\n[00:12.00] dopo la pausa\n");
    eq(5, l.times.length, "righe con tempo");
    eq(1_500L, l.times[0], "centesimi");
    eq(20_000L, l.times[4], "decimi");
    eq(-1, l.indexAt(1_000), "prima della prima riga");
    eq("prima", l.line(l.indexAt(2_000)), "prima riga");
    eq("ritornello", l.line(l.indexAt(5_000)), "più tempi sulla stessa riga");
    eq("", l.line(l.indexAt(10_500)), "pausa");
    eq("dopo la pausa", l.line(l.indexAt(15_000)), "dopo la pausa");
    eq("ritornello", l.line(l.indexAt(25_000)), "ritornello di nuovo");
    eq(true, NowPlayingText.parseLrc(null).isEmpty(), "testo assente");
    eq(true, NowPlayingText.parseLrc("solo parole senza tempi").isEmpty(), "testo non sincronizzato");

    if (sFailed > 0)
    {
      System.out.println("NowPlayingTextTest: " + sFailed + " prove fallite");
      System.exit(1);
    }
    System.out.println("NowPlayingTextTest: tutto bene");
  }

  private static void eq(Object want, Object got, String what)
  {
    if (want == null ? got != null : !want.equals(got))
    {
      sFailed++;
      System.out.println("FALLITA " + what + ": atteso «" + want + "», avuto «" + got + "»");
    }
  }
}
