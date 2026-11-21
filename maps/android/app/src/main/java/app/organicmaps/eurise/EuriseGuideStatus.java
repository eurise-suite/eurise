package app.organicmaps.eurise;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;

/**
 * La guida in corso per Alfred (tavola AlfAzioni, «In guida»: «quanto manca?», «avvisa Anna che arrivo»): la meta,
 * l'ora d'arrivo e i metri che mancano, aggiornati a ogni passo della guida (telefono e Android Auto, da
 * {@link EuriseWit}). Li legge solo un'app della suite con la stessa firma ({@link Provider}); niente esce dal telefono.
 */
public final class EuriseGuideStatus
{
  private static volatile long sEta;
  private static volatile double sMeters = -1;
  private static volatile long sUpdated;
  private static volatile String sTo = "";

  private EuriseGuideStatus() {}

  public static void update(@NonNull RoutingInfo info)
  {
    final long now = System.currentTimeMillis();
    sEta = now + Math.max(0, info.totalTimeInSeconds) * 1000L;
    sMeters = EuriseJunctionScene.meters(info.distToTarget);
    sUpdated = now;
    try
    {
      final MapObject end = RoutingController.get().getEndPoint();
      sTo = end != null && !end.isMyPosition() && end.getTitle() != null ? end.getTitle() : "";
    }
    catch (Throwable t)
    {
      sTo = "";
    }
  }

  /** Lo stato se la guida si è aggiornata negli ultimi 15 secondi; null se non si sta guidando. */
  @Nullable
  static Bundle read()
  {
    if (System.currentTimeMillis() - sUpdated > 15_000)
      return null;
    final Bundle b = new Bundle();
    b.putLong("eta", sEta);
    b.putDouble("meters", sMeters);
    b.putString("to", sTo);
    return b;
  }

  /** «content://eu.eurise.maps.guida», metodo «guide»: solo per chi ha il permesso della suite. */
  public static final class Provider extends ContentProvider
  {
    @Override
    public boolean onCreate()
    {
      return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras)
    {
      if (getContext() == null || !"guide".equals(method))
        return null;
      final boolean suite = getContext().checkCallingPermission("eu.eurise.permission.SUITE") == PackageManager.PERMISSION_GRANTED
                            || getContext().checkCallingPermission("eu.eurise.permission.SUITE_DEBUG") == PackageManager.PERMISSION_GRANTED;
      if (!suite)
        return null;
      final Bundle b = read();
      return b != null ? b : new Bundle();
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o)
    {
      return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri)
    {
      return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values)
    {
      return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] a)
    {
      return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a)
    {
      return 0;
    }
  }
}
