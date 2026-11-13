package app.organicmaps.eurise.car;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.model.CarText;
import androidx.car.app.navigation.model.MessageInfo;
import app.organicmaps.R;
import app.organicmaps.eurise.EuriseRouteWeather;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.util.concurrency.UiThread;
import java.util.Locale;

/**
 * Le righe di Eurise in Android Auto (tavole MapAutoGuida e MapAutoCerca).
 *
 * <ul>
 *   <li>«Sei su A51 Tangenziale Est»: la strada sotto l'auto, trovata come il limite di velocità (strada più vicina
 *   nella direzione di marcia) con {@code Framework.nativeEuriseRoadAt}. In guida va nella bolla dell'arrivo
 *   ({@code TravelEstimate.setTripText}); senza destinazione in un messaggio della mappa.</li>
 *   <li>Il riferimento per i soccorsi su autostrade, superstrade, statali e provinciali. Le mappe di CoMaps non hanno le
 *   pietre miliari (il km): finché non le aggiungiamo noi, il riferimento sono le coordinate, che il 112 accetta.</li>
 *   <li>Il meteo qui, senza destinazione, dallo stesso interruttore del meteo sul percorso (app Eurise e centro
 *   privacy).</li>
 * </ul>
 *
 * Il traffico non c'è: nessuna fonte libera nazionale (vedi docs/STATO.md); la riga non lo inventa.
 */
public final class EuriseCarInfo
{
  /** Una nuova ricerca della strada solo dopo qualche metro, come per il limite. */
  private static final float LOOKUP_MIN_DISTANCE_M = 20f;
  private static final double MIN_MOVING_SPEED_MPS = 2.0;

  // Classi di ftypes::HighwayClass (libs/indexer/ftypes_matcher.hpp).
  private static final int MOTORWAY = 1, TRUNK = 2, PRIMARY = 3, SECONDARY = 4, TERTIARY = 5;

  @Nullable private static Location sLookedUp;
  @Nullable private static Location sLast;
  private static int sClass;
  @NonNull private static String sRef = "", sName = "";

  private EuriseCarInfo() {}

  /** A ogni posizione (thread principale): rilegge la strada se ci si è spostati abbastanza. */
  public static void onLocation(@Nullable Location location)
  {
    if (location == null)
      return;
    sLast = location;
    if (sLookedUp != null && location.distanceTo(sLookedUp) < LOOKUP_MIN_DISTANCE_M)
      return;
    sLookedUp = location;
    final boolean moving = location.hasBearing() && location.hasSpeed() && location.getSpeed() >= MIN_MOVING_SPEED_MPS;
    final String road = Framework.nativeEuriseRoadAt(location.getLatitude(), location.getLongitude(),
                                                     moving ? location.getBearing() : -1);
    final String[] p = road == null ? new String[0] : road.split("\\|", -1);
    if (p.length < 3)
    {
      // Fuori strada (parcheggio, piazzale): si tiene l'ultima solo per pochi metri.
      sClass = 0;
      sRef = "";
      sName = "";
      return;
    }
    try
    {
      sClass = Integer.parseInt(p[0]);
    }
    catch (NumberFormatException e)
    {
      sClass = 0;
    }
    sRef = p[1];
    sName = p[2];
  }

  /** Si riparte da capo (nuova sessione dell'auto). */
  public static void reset()
  {
    sLookedUp = null;
    sClass = 0;
    sRef = "";
    sName = "";
  }

  /** «Sei su A51 Tangenziale Est», «Sei in Via Roma»; null se non si sa. [routeStreet]: la via della guida, se c'è. */
  @Nullable
  public static String where(@NonNull Context c, @Nullable String routeStreet)
  {
    String road = join(sRef, sName);
    if (road.isEmpty() && !TextUtils.isEmpty(routeStreet))
      road = routeStreet;
    if (road.isEmpty())
      return null;
    final boolean on = !sRef.isEmpty() || (sClass >= MOTORWAY && sClass <= TERTIARY);
    return c.getString(on ? R.string.eurise_car_on : R.string.eurise_car_in, road);
  }

  /** Il riferimento per i soccorsi, solo sulle strade dove serve (autostrade … provinciali); altrimenti null. */
  @Nullable
  public static String rescue(@NonNull Context c)
  {
    final Location l = sLast;
    if (l == null || sClass < MOTORWAY || sClass > SECONDARY)
      return null;
    return c.getString(R.string.eurise_car_rescue,
                       String.format(Locale.ROOT, "%.5f, %.5f", l.getLatitude(), l.getLongitude()));
  }

  /** Il testo nella bolla dell'arrivo, in guida: «Sei su A51 Tangenziale Est · meteo». */
  @Nullable
  public static CarText tripText(@NonNull Context c, @Nullable String routeStreet, @Nullable String weather)
  {
    final String where = where(c, routeStreet);
    final String text = join(where, weather, " · ");
    return text.isEmpty() ? null : CarText.create(text);
  }

  /**
   * Senza destinazione: il messaggio sulla mappa con «Sei su», soccorsi e meteo qui. [changed] ridisegna la schermata
   * quando arriva il meteo.
   */
  @Nullable
  public static MessageInfo freeDrive(@NonNull Context c, @NonNull Runnable changed)
  {
    final String where = where(c, null);
    final Location l = sLast;
    final String weather = l == null ? null
                         : EuriseRouteWeather.here(c.getApplicationContext(), l.getLatitude(), l.getLongitude(), changed);
    final String text = join(rescue(c), weather, " · ");
    if (where == null && text.isEmpty())
      return null;
    final MessageInfo.Builder b = new MessageInfo.Builder(where != null ? where : text);
    if (where != null && !text.isEmpty())
      b.setText(text);
    return b.build();
  }

  /**
   * In auto la freccia guarda sempre in alto lungo la strada, anche senza guida (correzione 3 del 13 novembre): quando
   * la mappa entra in «segui» senza rotazione e non ci si arriva dalla rotazione (cioè non l'ha chiesto chi guida), si
   * passa a «segui e ruota». Rimandato al giro successivo: il cambio di modo non va fatto dentro l'avviso del cambio.
   */
  public static void onModeChanged(int previousMode, int newMode)
  {
    if (newMode == LocationState.FOLLOW && previousMode != LocationState.FOLLOW_AND_ROTATE)
      rotate();
  }

  /** A fine guida la mappa resta orientata sulla strada. */
  public static void rotate()
  {
    UiThread.runLater(() -> {
      if (app.organicmaps.sdk.Map.isEngineCreated() && LocationState.getMode() == LocationState.FOLLOW)
        LocationState.nativeSwitchToNextMode();
    });
  }

  @NonNull
  private static String join(@Nullable String a, @Nullable String b)
  {
    return join(a, b, " ");
  }

  @NonNull
  private static String join(@Nullable String a, @Nullable String b, @NonNull String sep)
  {
    final boolean hasA = !TextUtils.isEmpty(a), hasB = !TextUtils.isEmpty(b);
    if (hasA && hasB)
      return a.equals(b) ? a : a + sep + b;
    return hasA ? a : hasB ? b : "";
  }
}
