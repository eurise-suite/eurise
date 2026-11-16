package app.organicmaps.eurise.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import app.organicmaps.R;

/**
 * «Ti ascolto» sullo schermo dell'auto: si apre toccando il microfono (mappa, guida, ricerca) e mostra ogni passo
 * dell'ascolto: ti ascolto, un attimo, oppure perché non va (permesso sul telefono, microfono dell'auto che non
 * risponde, Eurise Voce assente o senza modello) con «Riprova». Prima (prova del 16 novembre) gli avvisi erano brevi e
 * a volte non si vedevano: toccando il microfono sembrava non succedere nulla.
 *
 * <p>Quando la frase è pronta la schermata si chiude da sola e la frase va a chi l'ha aperta.
 */
public final class EuriseCarListenScreen extends Screen implements EuriseCarVoice.Listener
{
  @NonNull private final EuriseCarVoice.Callback mResult;
  private int mMessage = R.string.eurise_car_voice_listening;
  private boolean mRetry;
  private boolean mDone;

  public EuriseCarListenScreen(@NonNull CarContext car, @NonNull EuriseCarVoice.Callback result)
  {
    super(car);
    mResult = result;
    getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onCreate(@NonNull LifecycleOwner owner)
      {
        EuriseCarVoice.listen(getCarContext(), EuriseCarListenScreen.this);
      }

      @Override
      public void onDestroy(@NonNull LifecycleOwner owner)
      {
        if (!mDone)
          EuriseCarVoice.cancel();
      }
    });
  }

  @NonNull
  @Override
  public Template onGetTemplate()
  {
    final CarContext c = getCarContext();
    final MessageTemplate.Builder b = new MessageTemplate.Builder(c.getString(mMessage))
                                          .setTitle(c.getString(R.string.eurise_car_voice_title))
                                          .setHeaderAction(Action.BACK)
                                          .setIcon(new CarIcon.Builder(
                                                       IconCompat.createWithResource(c, R.drawable.eurise_ic_mic))
                                                       .build());
    if (mRetry)
      b.addAction(new Action.Builder()
                      .setTitle(c.getString(R.string.eurise_car_voice_retry))
                      .setOnClickListener(() -> {
                        mRetry = false;
                        mMessage = R.string.eurise_car_voice_listening;
                        invalidate();
                        EuriseCarVoice.listen(getCarContext(), this);
                      })
                      .build());
    b.addAction(new Action.Builder()
                    .setTitle(c.getString(R.string.eurise_car_voice_cancel))
                    .setOnClickListener(() -> getScreenManager().remove(this))
                    .build());
    return b.build();
  }

  @Override
  public void onStatus(int message, boolean retry, boolean busy)
  {
    mMessage = message;
    mRetry = retry;
    invalidate();
  }

  @Override
  public void onText(@NonNull String text)
  {
    mDone = true;
    // Prima si chiude «Ti ascolto», poi la frase va a chi l'ha chiesta (che così è di nuovo in cima).
    getScreenManager().remove(this);
    mResult.onText(text);
  }
}
