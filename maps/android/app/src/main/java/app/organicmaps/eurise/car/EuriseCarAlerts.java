package app.organicmaps.eurise.car;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.ScreenManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.Alert;
import androidx.car.app.model.AlertCallback;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.core.graphics.drawable.IconCompat;
import app.organicmaps.eurise.EuriseTrip;
import java.lang.ref.WeakReference;

/**
 * Gli avvisi del viaggio in Android Auto (tavola MapBollaAutoStati, approvata il 17 novembre): chiamata persa
 * («Ignora» · «Richiama all'arrivo»), messaggi («Dopo» · «Leggi», poi «Rispondi a voce» con «Ti ascolto»), ritardo
 * («Dopo» · «Avvisa»), conferme con «Annulla». L'auto li mostra sopra la mappa con al massimo due pulsanti e li chiude da
 * sola: così la fila della guida resta a quattro pulsanti (il massimo dell'auto). Se l'auto non sa mostrare gli avvisi
 * (Android Auto più vecchio), compare un messaggio breve senza pulsanti.
 */
public final class EuriseCarAlerts
{
  @Nullable private static WeakReference<CarContext> sCar;
  private static long sShownId;
  private static boolean sListening;

  private EuriseCarAlerts() {}

  /** Android Auto è aperto (per gli avvisi che arrivano a fine viaggio). */
  public static boolean active()
  {
    return sCar != null && sCar.get() != null;
  }

  /** Dallo schermo dell'auto (bolla nella mappa): da qui gli avvisi del viaggio vanno anche all'auto. */
  public static void attach(@NonNull CarContext car)
  {
    sCar = new WeakReference<>(car);
    if (!sListening)
    {
      sListening = true;
      EuriseTrip.listen(EuriseCarAlerts::onChanged);
    }
    EuriseTrip.ensure(car);
  }

  private static void onChanged()
  {
    final CarContext car = sCar == null ? null : sCar.get();
    final EuriseTrip.Notice n = EuriseTrip.notice();
    if (car == null || n == null || n.id == sShownId)
      return;
    sShownId = n.id;
    try
    {
      if (car.getCarAppApiLevel() < CarAppApiLevels.LEVEL_5)
      {
        CarToast.makeText(car, n.sub == null ? n.title : n.title + " · " + n.sub, CarToast.LENGTH_LONG).show();
        return;
      }
      final Host host = new Host(car);
      final long ms = n.remainingMs() > 0 ? n.remainingMs() : 10_000;
      final Alert.Builder b = new Alert.Builder((int) (n.id & 0x7FFFFFFF), CarText.create(n.title), ms);
      if (n.sub != null)
        b.setSubtitle(CarText.create(n.sub));
      if (n.icon != 0)
        b.setIcon(new CarIcon.Builder(IconCompat.createWithResource(car, n.icon)).build());
      final int id = (int) (n.id & 0x7FFFFFFF);
      if (n.left != null)
        b.addAction(new Action.Builder().setTitle(n.left).setOnClickListener(() -> {
          close(car, id);
          EuriseTrip.left(host, n);
        }).build());
      if (n.right != null)
        b.addAction(new Action.Builder().setTitle(n.rightCar != null ? n.rightCar : n.right).setOnClickListener(() -> {
          close(car, id);
          EuriseTrip.right(host, n);
        }).build());
      b.setCallback(new AlertCallback() {
        @Override
        public void onCancel(int reason)
        {
          // Scaduto o chiuso senza toccare: l'azione a tempo (invio dopo «Annulla») parte come sul telefono.
          if (reason == AlertCallback.REASON_TIMEOUT)
            EuriseTrip.expired(n);
        }

        @Override
        public void onDismiss() {}
      });
      car.getCarService(AppManager.class).showAlert(b.build());
    }
    catch (RuntimeException e)
    {
      // L'auto si è scollegata o rifiuta l'avviso: resta la bolla del telefono.
    }
  }

  private static void close(@NonNull CarContext car, int id)
  {
    try
    {
      car.getCarService(AppManager.class).dismissAlert(id);
    }
    catch (RuntimeException ignored)
    {
      // Già chiuso.
    }
  }

  /** L'auto come {@link EuriseTrip.Host}: la risposta con «Ti ascolto», la chiamata con il telefono dell'auto. */
  private static final class Host implements EuriseTrip.Host
  {
    private final CarContext mCar;

    Host(@NonNull CarContext car)
    {
      mCar = car;
    }

    @NonNull
    @Override
    public android.content.Context context()
    {
      return mCar;
    }

    @Override
    public boolean car()
    {
      return true;
    }

    @Override
    public void listen(@NonNull String prompt, @NonNull EuriseTrip.Text then)
    {
      mCar.getCarService(ScreenManager.class).push(new EuriseCarListenScreen(mCar, then::onText, true));
    }

    @Override
    public void dial(@Nullable String number)
    {
      try
      {
        mCar.startCarApp(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + (number == null ? "" : number))));
      }
      catch (RuntimeException ignored)
      {
        // L'auto non sa chiamare da qui.
      }
    }
  }
}
