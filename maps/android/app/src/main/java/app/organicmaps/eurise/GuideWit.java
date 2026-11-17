package app.organicmaps.eurise;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Quando Alfred può fare una battuta in guida (17 novembre: «anche alla guida può essere simpatico, l'importante è che non
 * confonda le istruzioni»). La parte senza Android: solo nei momenti tranquilli, mai vicino a una manovra, mai una parola
 * che somigli a un'istruzione.
 *
 * <ul>
 *   <li>Partenza: dopo 30 s di guida, se la prossima manovra è lontana.</li>
 *   <li>Rettilineo lungo: prossima manovra oltre 4 km (400 m a piedi), almeno 15 minuti dall'ultima battuta.</li>
 *   <li>Arrivo: una volta, a meno di 40 m dalla meta (chi chiama aspetta qualche secondo, dopo la voce della guida).</li>
 * </ul>
 * Le battute di partenza e del rettilineo non contengono mai parole di direzione ({@link #safe}); all'arrivo sì, perché lì
 * non ci sono più istruzioni.
 */
public final class GuideWit
{
  public enum Moment { DEPARTURE, CRUISE, ARRIVAL }

  static final long DEPARTURE_AFTER_MS = 30_000;
  static final long MIN_GAP_MS = 15 * 60_000;
  static final long NEW_TRIP_GAP_MS = 60_000;
  static final double QUIET_CAR_M = 4000;
  static final double QUIET_WALK_M = 400;
  static final double ARRIVED_M = 40;

  private static final String[] DIRECTION_WORDS = {
      "destra", "sinistra", "gira", "giri", "svolt", "uscit", "esci", "rotond", "prosegu", "dritt", "diritt",
      "inversion", "corsia", "imbocc", "mantien", "tieni", "accost", "frena", "rallent", "fermat", "ferma ",
      "right", "left", "turn", "exit", "roundabout", "lane", "keep", "straight", "u-turn", "stop"};

  private long mStartedAt = -1;
  private long mLastUpdate = -1;
  private long mLastSaid = -1;
  private boolean mDeparted;
  private boolean mArrived;

  /**
   * Il momento di dire qualcosa, o null. [toTurnM] e [toTargetM] in metri (negativi se non noti). Ogni chiamata è un
   * aggiornamento della guida; una pausa di oltre un minuto vuol dire un viaggio nuovo.
   */
  public Moment next(long now, double toTurnM, double toTargetM, boolean pedestrian)
  {
    if (mLastUpdate < 0 || now - mLastUpdate > NEW_TRIP_GAP_MS)
    {
      mStartedAt = now;
      mLastSaid = -1;
      mDeparted = false;
      mArrived = false;
    }
    mLastUpdate = now;
    if (mArrived)
      return null;
    if (toTargetM >= 0 && toTargetM < ARRIVED_M && now - mStartedAt > DEPARTURE_AFTER_MS)
    {
      mArrived = true;
      return Moment.ARRIVAL;
    }
    final double quiet = pedestrian ? QUIET_WALK_M : QUIET_CAR_M;
    final boolean calm = toTurnM >= quiet && toTargetM >= quiet;
    if (!mDeparted)
    {
      if (now - mStartedAt < DEPARTURE_AFTER_MS)
        return null;
      // Se la partenza non è mai tranquilla, si lascia perdere: nessuna battuta tra una svolta e l'altra.
      if (now - mStartedAt > 5 * 60_000)
      {
        mDeparted = true;
        return null;
      }
      if (!calm)
        return null;
      mDeparted = true;
      mLastSaid = now;
      return Moment.DEPARTURE;
    }
    if (calm && (mLastSaid < 0 || now - mLastSaid >= MIN_GAP_MS))
    {
      mLastSaid = now;
      return Moment.CRUISE;
    }
    return null;
  }

  /** Vero se [line] non ha parole che si possano scambiare per un'istruzione. */
  public static boolean safe(String line)
  {
    final String l = " " + Normalizer.normalize(line.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                                     .replaceAll("\\p{M}", "")
                                     .replaceAll("[^a-z]+", " ") + " ";
    for (String w : DIRECTION_WORDS)
      if (l.contains(" " + w))
        return false;
    return true;
  }

  private static final ArrayDeque<String> RECENT = new ArrayDeque<>();

  /** Una battuta del momento, mai una delle ultime cinque; per partenza e rettilineo solo quelle {@link #safe}. */
  public static String pick(Moment moment, String[] all, Random random)
  {
    final List<String> ok = new ArrayList<>();
    for (String s : all)
      if ((moment == Moment.ARRIVAL || safe(s)) && !RECENT.contains(s))
        ok.add(s);
    if (ok.isEmpty())
      for (String s : all)
        if (moment == Moment.ARRIVAL || safe(s))
          ok.add(s);
    if (ok.isEmpty())
      return null;
    final String s = ok.get(random.nextInt(ok.size()));
    RECENT.addLast(s);
    if (RECENT.size() > 5)
      RECENT.removeFirst();
    return s;
  }
}
