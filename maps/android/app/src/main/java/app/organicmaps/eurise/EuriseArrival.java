package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingInfo;
import java.text.DateFormat;
import java.util.Date;

/**
 * La frase d'arrivo nella guida (tavola MapGuida): se vai verso un impegno scelto da «Dove devi andare», una riga dice
 * con quanto margine arrivi («Pioggia all'arrivo: arrivi a «Sopralluogo» delle 9:30 con qualche minuto di margine»).
 * Toccandola, «Manda l'arrivo». Tutto sul telefono: l'impegno resta nelle preferenze di Mappe e si dimentica da solo.
 */
public final class EuriseArrival
{
  private static final String PREFS = "eurise_arrival";

  private EuriseArrival() {}

  /** L'impegno verso cui stai andando (da «Dove devi andare»). */
  public static void remember(@NonNull Context context, @NonNull String title, long startMillis)
  {
    prefs(context).edit().putString("title", title).putLong("start", startMillis)
        .putLong("picked", System.currentTimeMillis()).apply();
  }

  public static void forget(@NonNull Context context)
  {
    prefs(context).edit().clear().apply();
  }

  private static SharedPreferences prefs(@NonNull Context c)
  {
    return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /** A ogni aggiornamento della guida (thread principale). */
  public static void update(@Nullable TextView line, @NonNull RoutingInfo info)
  {
    if (line == null)
      return;
    final Context c = line.getContext();
    final SharedPreferences p = prefs(c);
    final String title = p.getString("title", null);
    final long start = p.getLong("start", 0);
    final long now = System.currentTimeMillis();
    final long eta = now + Math.max(0, info.totalTimeInSeconds) * 1000L;
    if (title == null || info.totalTimeInSeconds <= 0 || !ArrivalPhrase.stillRelevant(p.getLong("picked", 0), start, now, eta))
    {
      line.setVisibility(View.GONE);
      return;
    }
    final int minutes = ArrivalPhrase.marginMinutes(eta, start);
    final String margin;
    switch (ArrivalPhrase.margin(minutes))
    {
    case EARLY: margin = c.getString(R.string.eurise_arr_early, minutes); break;
    case SOME: margin = c.getString(R.string.eurise_arr_some, minutes); break;
    case ON_TIME: margin = c.getString(R.string.eurise_arr_on_time); break;
    default: margin = c.getString(R.string.eurise_arr_late, -minutes); break;
    }
    final String hour = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(start));
    final String sentence = c.getString(R.string.eurise_arr_sentence, title, hour, margin);
    final String weather = EuriseRouteWeather.arrivalLabel(c);
    line.setText(weather == null ? sentence : c.getString(R.string.eurise_arr_with_weather, weather, sentence));
    EuriseRouteWeather.stylePill(line);
    line.setVisibility(View.VISIBLE);
    line.setOnClickListener(v -> {
      final Activity a = activity(c);
      if (a != null)
        EuriseGuide.shareArrival(a, info);
    });
  }

  @Nullable
  private static Activity activity(@Nullable Context c)
  {
    while (c instanceof ContextWrapper)
    {
      if (c instanceof Activity)
        return (Activity) c;
      c = ((ContextWrapper) c).getBaseContext();
    }
    return null;
  }
}
