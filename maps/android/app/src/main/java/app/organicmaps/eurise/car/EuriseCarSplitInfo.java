package app.organicmaps.eurise.car;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.eurise.EuriseRouteWeather;
import androidx.car.app.CarContext;
import app.organicmaps.car.util.ThemeUtils;

/**
 * Android Auto con lo schermo diviso (la mappa accanto alla musica o al telefono): l'auto non mostra più la scheda con
 * «Sei su …» e il meteo, quindi li mettiamo noi sulla mappa, in una pillola di vetro in basso a sinistra (richiesta del
 * 16 novembre): velocità attuale, via (con il km se c'è) e meteo qui. A schermo pieno la pillola si nasconde, perché
 * le stesse cose le mostra già la scheda dell'auto.
 *
 * <p>Quando lo schermo è diviso: la superficie della mappa è molto più stretta dello schermo dell'auto, o l'area
 * visibile è poco più di metà della superficie. Tutto sul thread principale.
 */
public final class EuriseCarSplitInfo
{
  /** Sotto questa frazione della larghezza dell'auto la mappa è in un riquadro accanto a un'altra app. */
  private static final float SPLIT_SURFACE = 0.8f;
  private static final float SPLIT_VISIBLE = 0.55f;

  @Nullable private static TextView sView;
  @Nullable private static CarContext sCar;
  private static int sSurfaceW, sSurfaceH;
  @NonNull private static final Rect sVisible = new Rect();

  private EuriseCarSplitInfo() {}

  /** La pillola, da aggiungere sopra la mappa dell'auto (SurfaceCallback.initSpeedPill). */
  @NonNull
  public static View create(@NonNull CarContext car)
  {
    sCar = car;
    final TextView t = new TextView(car);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    t.setMaxLines(2);
    t.setEllipsize(TextUtils.TruncateAt.END);
    final int pad = dp(car, 10);
    t.setPadding(dp(car, 14), pad, dp(car, 14), pad);
    t.setVisibility(View.GONE);
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                                                                     FrameLayout.LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.START | Gravity.BOTTOM;
    lp.leftMargin = dp(car, 6);
    lp.bottomMargin = dp(car, 6);
    t.setLayoutParams(lp);
    sView = t;
    refresh();
    return t;
  }

  public static void onSurface(int width, int height)
  {
    sSurfaceW = width;
    sSurfaceH = height;
    refresh();
  }

  public static void onVisibleArea(@NonNull Rect visible)
  {
    sVisible.set(visible);
    refresh();
  }

  static boolean split()
  {
    if (sCar == null || sSurfaceW <= 0)
      return false;
    final int carW = sCar.getResources().getDisplayMetrics().widthPixels;
    if (carW > 0 && sSurfaceW < carW * SPLIT_SURFACE)
      return true;
    return !sVisible.isEmpty() && sVisible.width() < sSurfaceW * SPLIT_VISIBLE;
  }

  /** Riscrive la pillola (a ogni posizione e quando cambia l'area). */
  public static void refresh()
  {
    final TextView t = sView;
    final CarContext c = sCar;
    if (t == null || c == null)
      return;
    if (!split())
    {
      t.setVisibility(View.GONE);
      return;
    }
    final Location l = EuriseCarInfo.last();
    final StringBuilder b = new StringBuilder();
    if (l != null && l.hasSpeed())
      b.append(Math.round(l.getSpeed() * 3.6f)).append(" km/h");
    final String where = EuriseCarInfo.where(c, null);
    if (where != null && !where.isEmpty())
      append(b, where);
    if (l != null)
    {
      final String weather = EuriseRouteWeather.here(c.getApplicationContext(), l.getLatitude(), l.getLongitude(),
                                                     EuriseCarSplitInfo::refresh);
      if (weather != null && !weather.isEmpty())
        append(b, weather);
    }
    if (b.length() == 0)
    {
      t.setVisibility(View.GONE);
      return;
    }
    final boolean night = ThemeUtils.isNightMode(c);
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(c, 18));
    bg.setColor(night ? 0xE61C2333 : 0xE6FFFFFF);
    bg.setStroke(Math.max(1, dp(c, 1) / 2), night ? 0x29FFFFFF : 0x1F1E1D1B);
    t.setBackground(bg);
    t.setTextColor(night ? 0xFFE8ECF4 : 0xFF1F2A44);
    // La pillola non copre più di metà dell'area visibile.
    final int maxW = sVisible.isEmpty() ? sSurfaceW / 2 : sVisible.width() * 3 / 5;
    if (maxW > 0)
      t.setMaxWidth(maxW);
    final String text = b.toString();
    if (!text.contentEquals(t.getText()))
      t.setText(text);
    t.setVisibility(View.VISIBLE);
  }

  private static void append(@NonNull StringBuilder b, @NonNull String s)
  {
    if (b.length() > 0)
      b.append(" · ");
    b.append(s);
  }

  private static int dp(@NonNull Context c, int v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
  }
}
