package app.organicmaps.eurise;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Manda un SMS con Eurise Messaggi, per «Avvisa: arrivo alle…» e l'arrivo condiviso (tavole MapBollaRitardo e
 * MapBollaArrivo): passa solo tra app firmate con la stessa chiave (permesso della suite). Mappe non ha il permesso degli
 * SMS né quello dei contatti: il numero, se manca, lo cerca Messaggi dal nome nella Rubrica.
 */
public final class EuriseSms
{
  public interface Result
  {
    /** [ok]: partito; [why]: «no_app», «no_number», «no_permission», «failed» se non è partito. */
    void onResult(boolean ok, @Nullable String why);
  }

  // Stessi nomi del ricevitore di Messaggi (messages/…/SuiteSmsReceiver.kt).
  private static final String ACTION = "eu.eurise.action.SEND_SMS";
  private static final String[] PACKAGES = {"eu.eurise.messages.debug", "eu.eurise.messages"};
  private static final String PERMISSION = "eu.eurise.permission.SUITE_DEBUG";

  private EuriseSms() {}

  /** Eurise Messaggi c'è (per scegliere se offrire l'invio in un tocco). */
  public static boolean available(@NonNull Context c)
  {
    return pkg(c) != null;
  }

  public static void send(@NonNull Context c, @Nullable String number, @NonNull String name, @NonNull String text,
                          @NonNull Result result)
  {
    final String pkg = pkg(c);
    if (pkg == null)
    {
      result.onResult(false, "no_app");
      return;
    }
    final Intent i = new Intent(ACTION).setPackage(pkg)
                         .putExtra("to", number == null ? "" : number)
                         .putExtra("name", name)
                         .putExtra("text", text);
    c.getApplicationContext().sendOrderedBroadcast(i, PERMISSION, new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent)
      {
        final boolean ok = getResultCode() == Activity.RESULT_OK;
        result.onResult(ok, ok ? null : getResultData() == null ? "failed" : getResultData());
      }
    }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, "no_app", null);
  }

  @Nullable
  private static String pkg(@NonNull Context c)
  {
    for (String p : PACKAGES)
    {
      try
      {
        c.getPackageManager().getPackageInfo(p, 0);
        return p;
      }
      catch (PackageManager.NameNotFoundException ignored)
      {
        // Prova l'altra.
      }
    }
    return null;
  }
}
