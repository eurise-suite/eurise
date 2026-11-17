package app.organicmaps.eurise.car;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.core.widget.ImageViewCompat;
import app.organicmaps.eurise.EuriseGuide;
import app.organicmaps.eurise.EuriseJunctionScene;
import app.organicmaps.eurise.EuriseRouteWeather;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.widget.roadshield.RoadShieldUtils;
import app.organicmaps.widget.LanesDrawable;
import java.text.DateFormat;
import java.util.Date;

/**
 * La guida in Android Auto con i riquadri della suite (tavole MapAutoSuiteGiorno e MapAutoSuiteNotte, approvate il 17
 * novembre): li disegna Mappe sullo schermo dell'auto, nel vetro chiaro di giorno e scuro di notte come sul telefono,
 * al posto dei riquadri neri dell'auto (il modello di guida non riceve più manovra e stima d'arrivo; la guida va ancora
 * all'auto con NavigationManager per il quadro strumenti).
 *
 * <p>Colonna a sinistra: manovra (freccia nel colore del tipo di strada, distanza, sigla e via, corsie, scena
 * dell'uscita o della rotonda), sotto il meteo sul percorso; in basso l'arrivo (ora, minuti e km, avanzamento, «Sei
 * su…»). La bolla della musica e delle chiamate le sta accanto ({@link #columnRight}).
 */
public final class EuriseCarGuide extends FrameLayout
{
  private static final long TICK_MS = 1000;

  private final CarContext mCar;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::refresh;
  private final LinearLayout mManeuver, mArrival;
  private final ImageView mTurn, mLanes, mScene;
  private final TextView mExit, mDistance, mStreet, mThen, mWeather, mEta, mLeft, mWhere;
  private final View mProgressTrack, mProgressFill;
  private int mColumnWidth;

  private EuriseCarGuide(@NonNull CarContext car)
  {
    super(car);
    mCar = car;
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // ---- Manovra (in alto) ----
    mManeuver = new LinearLayout(car);
    mManeuver.setOrientation(LinearLayout.VERTICAL);
    final LinearLayout row = new LinearLayout(car);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    mManeuver.addView(row);
    final FrameLayout turnBox = new FrameLayout(car);
    mTurn = new ImageView(car);
    turnBox.addView(mTurn, new FrameLayout.LayoutParams(dp(52), dp(52)));
    mExit = text(car, 15, true);
    mExit.setGravity(Gravity.CENTER);
    turnBox.addView(mExit, new FrameLayout.LayoutParams(dp(52), dp(52)));
    row.addView(turnBox, new LinearLayout.LayoutParams(dp(52), dp(52)));
    final LinearLayout words = new LinearLayout(car);
    words.setOrientation(LinearLayout.VERTICAL);
    final LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    wlp.leftMargin = dp(14);
    row.addView(words, wlp);
    mDistance = text(car, 30, true);
    mStreet = text(car, 17, true);
    words.addView(mDistance);
    words.addView(mStreet);
    mLanes = new ImageView(car);
    mLanes.setScaleType(ImageView.ScaleType.FIT_START);
    final LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
    llp.topMargin = dp(8);
    mManeuver.addView(mLanes, llp);
    mScene = new ImageView(car);
    mScene.setScaleType(ImageView.ScaleType.CENTER_CROP);
    mScene.setClipToOutline(true);
    final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
    slp.topMargin = dp(8);
    mManeuver.addView(mScene, slp);
    mThen = text(car, 14, false);
    final LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                        ViewGroup.LayoutParams.WRAP_CONTENT);
    tlp.topMargin = dp(6);
    mManeuver.addView(mThen, tlp);
    addView(mManeuver);

    // ---- Meteo sul percorso (sotto la manovra) ----
    mWeather = text(car, 14, false);
    mWeather.setMaxLines(2);
    addView(mWeather);

    // ---- Arrivo (in basso) ----
    mArrival = new LinearLayout(car);
    mArrival.setOrientation(LinearLayout.VERTICAL);
    final LinearLayout times = new LinearLayout(car);
    times.setOrientation(LinearLayout.HORIZONTAL);
    times.setGravity(Gravity.BOTTOM);
    mEta = text(car, 26, true);
    mLeft = text(car, 16, false);
    times.addView(mEta);
    final LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                           ViewGroup.LayoutParams.WRAP_CONTENT);
    leftLp.leftMargin = dp(12);
    leftLp.bottomMargin = dp(3);
    times.addView(mLeft, leftLp);
    mArrival.addView(times);
    final FrameLayout track = new FrameLayout(car);
    mProgressTrack = track;
    mProgressFill = new View(car);
    track.addView(mProgressFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
    final LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
    plp.topMargin = dp(8);
    mArrival.addView(track, plp);
    mWhere = text(car, 14, true);
    final LinearLayout.LayoutParams whereLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                            ViewGroup.LayoutParams.WRAP_CONTENT);
    whereLp.topMargin = dp(8);
    mArrival.addView(mWhere, whereLp);
    addView(mArrival);

    setVisibility(GONE);
  }

  /** Sullo schermo dell'auto, nell'area libera (SurfaceCallback), sotto la bolla. */
  @NonNull
  public static View create(@NonNull CarContext car)
  {
    return new EuriseCarGuide(car);
  }

  /** Il bordo destro della colonna (in pixel dentro l'area libera), 0 se la guida non è in vista: la bolla sta accanto. */
  public int columnRight()
  {
    return getVisibility() == VISIBLE ? dp(12) + mColumnWidth : 0;
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    refresh();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    mHandler.removeCallbacks(mTick);
    super.onDetachedFromWindow();
  }

  private void refresh()
  {
    mHandler.removeCallbacks(mTick);
    mHandler.postDelayed(mTick, TICK_MS);
    final RoutingInfo info = RoutingController.get().isNavigating() ? Framework.nativeGetRouteFollowingInfo() : null;
    if (info == null)
    {
      setVisibility(GONE);
      return;
    }
    app.organicmaps.eurise.EuriseWit.update(mCar, info, false);
    final boolean night = app.organicmaps.car.util.ThemeUtils.isNightMode(mCar);
    layoutColumn();
    style(night);

    // Manovra.
    info.carDirection.setTurnDrawable(mTurn);
    ImageViewCompat.setImageTintList(mTurn, ColorStateList.valueOf(
        EuriseGuide.arrowColor(EuriseGuide.road(info.nextRef, info.nextStreetRoadShields), night)));
    if (CarDirection.isRoundAbout(info.carDirection) && info.exitNum > 0)
    {
      mExit.setText(String.valueOf(info.exitNum));
      mExit.setVisibility(VISIBLE);
    }
    else
      mExit.setVisibility(GONE);
    mDistance.setText(EuriseGuide.turnDistance(mCar, info.distToTurn));
    final CharSequence street = RoadShieldUtils.composeInstruction(info, mStreet.getTextSize());
    set(mStreet, street);
    if (info.lanes != null && info.lanes.length > 0)
    {
      mLanes.setImageDrawable(new LanesDrawable(mCar, info.lanes, night ? 0xFFE8ECF4 : 0xFF1F2A44,
                                                night ? 0x55E8ECF4 : 0x551F2A44));
      mLanes.setVisibility(VISIBLE);
    }
    else
      mLanes.setVisibility(GONE);
    final Bitmap scene = EuriseJunctionScene.bitmap(
        mCar, EuriseJunctionScene.from(mCar, info, EuriseCarInfo.isFastRoad()), 640, 360);
    mScene.setImageBitmap(scene);
    mScene.setVisibility(scene == null ? GONE : VISIBLE);
    set(mThen, TextUtils.isEmpty(info.nextNextStreet) || !info.nextCarDirection.containsNextTurn()
               ? null : mCar.getString(app.organicmaps.R.string.eurise_car_then, info.nextNextStreet));

    // Meteo sul percorso.
    set(mWeather, EuriseRouteWeather.carLine(mCar, info, () -> {}));

    // Arrivo.
    final int seconds = Math.max(0, info.totalTimeInSeconds);
    mEta.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(System.currentTimeMillis() + seconds * 1000L)));
    final int minutes = (seconds + 30) / 60;
    final String time = minutes < 60 ? minutes + " min"
                                     : minutes % 60 == 0 ? (minutes / 60) + " h" : (minutes / 60) + " h " + (minutes % 60) + " min";
    String distance = "";
    try
    {
      distance = info.distToTarget.toString(mCar);
    }
    catch (RuntimeException ignored)
    {
      // Distanza non pronta.
    }
    mLeft.setText(distance.isEmpty() ? time : time + " · " + distance);
    final int w = mProgressTrack.getWidth();
    final int fill = (int) (w * Math.max(0, Math.min(100, info.completionPercent)) / 100.0);
    final ViewGroup.LayoutParams fl = mProgressFill.getLayoutParams();
    if (fl.width != fill)
    {
      fl.width = fill;
      mProgressFill.setLayoutParams(fl);
    }
    set(mWhere, EuriseCarInfo.where(mCar, info.currentStreet));

    setVisibility(VISIBLE);
  }

  /** La colonna: un terzo dello schermo dell'auto, da 300 a 420 dp. */
  private void layoutColumn()
  {
    final int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
    final int column = Math.max(dp(300), Math.min(dp(420), width * 34 / 100));
    mColumnWidth = column;
    final int m = dp(12);
    place(mManeuver, column, Gravity.TOP | Gravity.START, m, m, 0);
    final int weatherTop = m + (mManeuver.getHeight() > 0 ? mManeuver.getHeight() : dp(100)) + dp(8);
    place(mWeather, column, Gravity.TOP | Gravity.START, m, weatherTop, 0);
    place(mArrival, column, Gravity.BOTTOM | Gravity.START, m, 0, m);
  }

  private static void place(@NonNull View v, int width, int gravity, int left, int top, int bottom)
  {
    final FrameLayout.LayoutParams lp = v.getLayoutParams() instanceof FrameLayout.LayoutParams
                                        ? (FrameLayout.LayoutParams) v.getLayoutParams()
                                        : new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (lp.width == width && lp.gravity == gravity && lp.leftMargin == left && lp.topMargin == top
        && lp.bottomMargin == bottom && v.getLayoutParams() == lp)
      return;
    lp.width = width;
    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    lp.gravity = gravity;
    lp.leftMargin = left;
    lp.topMargin = top;
    lp.bottomMargin = bottom;
    v.setLayoutParams(lp);
  }

  /** Il vetro della suite: chiaro di giorno, scuro di notte, come la guida sul telefono. */
  private void style(boolean night)
  {
    final int ink = night ? 0xFFE8ECF4 : 0xFF1E1D1B;
    final int sub = night ? 0xFFC9D2E0 : 0xFF3B3A36;
    for (View v : new View[] {mManeuver, mArrival, mWeather})
    {
      final GradientDrawable bg = new GradientDrawable();
      bg.setColor(night ? 0xF01C2333 : 0xF0F8F7F4);
      bg.setCornerRadius(dp(v == mWeather ? 16 : 22));
      bg.setStroke(Math.max(1, dp(1) / 2), night ? 0x29FFFFFF : 0x1F1E1D1B);
      v.setBackground(bg);
      v.setElevation(dp(4));
      final int ph = dp(v == mWeather ? 14 : 18), pv = dp(v == mWeather ? 9 : 12);
      v.setPadding(ph, pv, ph, pv);
    }
    mDistance.setTextColor(ink);
    mStreet.setTextColor(ink);
    mExit.setTextColor(night ? 0xFF1C2333 : 0xFFFFFFFF);
    mThen.setTextColor(sub);
    mWeather.setTextColor(sub);
    mEta.setTextColor(night ? 0xFF9CC3A9 : 0xFF2F6B45);
    mLeft.setTextColor(sub);
    mWhere.setTextColor(sub);
    mProgressTrack.setBackground(round(night ? 0x2EFFFFFF : 0xFFDAD7CF));
    mProgressFill.setBackground(round(night ? 0xFFA9C1F2 : 0xFF1F2A44));
    final GradientDrawable scene = new GradientDrawable();
    scene.setCornerRadius(dp(14));
    scene.setColor(0x00000000);
    mScene.setBackground(scene);
  }

  @NonNull
  private GradientDrawable round(int color)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(3));
    return d;
  }

  private static void set(@NonNull TextView t, @Nullable CharSequence s)
  {
    if (TextUtils.isEmpty(s))
    {
      t.setVisibility(GONE);
      return;
    }
    if (!TextUtils.equals(s, t.getText()))
      t.setText(s);
    t.setVisibility(VISIBLE);
  }

  @NonNull
  private TextView text(@NonNull Context c, float sp, boolean bold)
  {
    final TextView t = new TextView(c);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    t.setMaxLines(1);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setIncludeFontPadding(false);
    return t;
  }

  private int dp(float v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()));
  }
}
