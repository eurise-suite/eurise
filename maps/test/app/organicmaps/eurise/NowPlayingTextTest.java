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

    eq(true, NowPlayingText.spoken(25 * 60_000L, false), "puntata lunga");
    eq(false, NowPlayingText.spoken(4 * 60_000L, false), "canzone");
    eq(true, NowPlayingText.spoken(4 * 60_000L, true), "app di podcast");
    eq(true, NowPlayingText.live(0), "radio senza durata");
    eq(600_000L, NowPlayingText.leftAtArrival(0, 1_440_000, 1f, 840_000), "mancheranno 10 min");
    eq(-60_000L, NowPlayingText.leftAtArrival(0, 960_000, 1.2f, 860_000), "finisce prima, più veloce");
    eq(10, NowPlayingText.minutesUp(540_001), "minuti per eccesso");
    eq(1, NowPlayingText.minutesUp(0), "almeno un minuto");
    eq("1,2×", NowPlayingText.speed(1.2f), "velocità");
    eq("2×", NowPlayingText.speed(2f), "velocità intera");
    eq("", NowPlayingText.speed(1f), "velocità normale");
    eq(false, NowPlayingText.shareUpdate(0, 0, 4 * 60_000L, 20 * 60_000L), "arrivo quasi uguale");
    eq(true, NowPlayingText.shareUpdate(0, 0, 6 * 60_000L, 20 * 60_000L), "arrivo spostato");
    eq(false, NowPlayingText.shareUpdate(0, 0, 6 * 60_000L, 5 * 60_000L), "troppo presto");
    eq(true, NowPlayingText.soon(20 * 60_000L, 0, 30 * 60_000L), "tra 20 min");
    eq(false, NowPlayingText.soon(40 * 60_000L, 0, 30 * 60_000L), "tra 40 min");
    eq(true, NowPlayingText.nameIn("Pranzo con Giulia", "Giulia Rossi"), "nome nell'impegno");
    eq(false, NowPlayingText.nameIn("Giuliano in ufficio", "Giulia"), "parola intera");
    eq(false, NowPlayingText.nameIn("Riunione", "Al"), "nome troppo corto");
    eq(9, NowPlayingText.lateMinutes(9 * 60_000L, 0), "ritardo");
    eq(0, NowPlayingText.lateMinutes(0, 9 * 60_000L), "in anticipo");
    eq("+393331234567", NowPlayingText.phone("tel:+39%20333%20123%204567"), "numero dal tel:");
    eq(null, NowPlayingText.phone("mailto:a@b.it"), "non è un numero");

    eq("SS7", NowPlayingText.roadRef("IT:national/SS7"), "rete tolta");
    eq("A1 E35", NowPlayingText.roadRef("A1;e-road/E35"), "più numeri");
    eq("SP50", NowPlayingText.roadRef("SP50"), "numero semplice");
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
