package app.organicmaps.eurise;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.sound.TtsPlayer;
import java.util.Random;

/**
 * Alfred spiritoso in guida, sul telefono e in Android Auto: a ogni aggiornamento della guida chiede a {@link GuideWit}
 * se è il momento, e la battuta passa da {@link EuriseSpeak} (la voce di Alfred). Solo con la voce della guida accesa e se
 * Alfred non sta già leggendo altro; all'arrivo aspetta qualche secondo, dopo l'annuncio della guida.
 */
public final class EuriseWit
{
  private static final GuideWit WIT = new GuideWit();
  private static final Random RANDOM = new Random();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final long ARRIVAL_DELAY_MS = 4_000;

  private EuriseWit() {}

  public static void update(@NonNull Context context, @Nullable RoutingInfo info, boolean pedestrian)
  {
    if (info == null)
      return;
    final GuideWit.Moment moment = WIT.next(System.currentTimeMillis(), EuriseJunctionScene.meters(info.distToTurn),
                                            EuriseJunctionScene.meters(info.distToTarget), pedestrian);
    if (moment == null || !voiceOn() || EuriseSpeak.busy())
      return;
    final int array = switch (moment)
    {
      case DEPARTURE -> R.array.eurise_wit_departure;
      case CRUISE -> R.array.eurise_wit_cruise;
      case ARRIVAL -> R.array.eurise_wit_arrival;
    };
    final Context app = context.getApplicationContext();
    final String line = GuideWit.pick(moment, app.getResources().getStringArray(array), RANDOM);
    if (line == null)
      return;
    if (moment == GuideWit.Moment.ARRIVAL)
      MAIN.postDelayed(() -> { if (!EuriseSpeak.busy()) EuriseSpeak.say(app, line, null); }, ARRIVAL_DELAY_MS);
    else
      EuriseSpeak.say(app, line, null);
  }

  private static boolean voiceOn()
  {
    try
    {
      return TtsPlayer.isEnabled();
    }
    catch (Throwable t)
    {
      return false;
    }
  }
}
