package app.organicmaps.eurise;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;

/**
 * Il tasto «3D»/«2D» mentre si esplora (tavola MapEsploraInclinata, approvata il 17 novembre): a destra, sotto la fascia
 * dell'orizzonte. «3D» piega la mappa fino al cielo, come in guida ({@link EuriseSky#explore3d}); «2D» la rimette
 * piatta. Si nasconde mentre si pianifica o si guida (lì l'inclinazione la decide la guida).
 */
public final class EuriseTilt extends AppCompatTextView
{
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::tick;

  public EuriseTilt(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setGravity(Gravity.CENTER);
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    setTypeface(Typeface.DEFAULT_BOLD);
    setElevation(dp(4));
    setOnClickListener(v -> {
      EuriseSky.explore3d(getContext(), !EuriseSky.exploring3d());
      update();
    });
    update();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    tick();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    mHandler.removeCallbacks(mTick);
    super.onDetachedFromWindow();
  }

  private void tick()
  {
    final RoutingController rc = RoutingController.get();
    setVisibility(rc.isPlanning() || rc.isNavigating() ? GONE : VISIBLE);
    update();
    place();
    mHandler.postDelayed(mTick, 1000);
  }

  private void update()
  {
    final boolean on = EuriseSky.exploring3d();
    final boolean night = app.organicmaps.util.ThemeUtils.isNightTheme();
    final String label = getContext().getString(on ? R.string.eurise_tilt_flat : R.string.eurise_tilt_3d);
    if (!label.contentEquals(getText()))
    {
      setText(label);
      setContentDescription(getContext().getString(on ? R.string.eurise_tilt_flat_desc : R.string.eurise_tilt_3d_desc));
    }
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(16));
    bg.setColor(night ? 0xFF1C2027 : 0xFFFFFFFF);
    setBackground(bg);
    setTextColor(night ? 0xFFA9C1F2 : 0xFF1F2A44);
  }

  /** Sotto la fascia dell'orizzonte e le scorciatoie, dal lato destro. */
  private void place()
  {
    if (!(getLayoutParams() instanceof ViewGroup.MarginLayoutParams))
      return;
    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) getLayoutParams();
    final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
    final int top = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.statusBars()
                                                          | WindowInsetsCompat.Type.displayCutout()).top;
    final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    final int want = top + dp(land ? 120 : 176);
    final int size = dp(48);
    if (lp.topMargin != want || lp.width != size || lp.rightMargin != dp(14))
    {
      lp.topMargin = want;
      lp.rightMargin = dp(14);
      lp.width = size;
      lp.height = size;
      setLayoutParams(lp);
    }
  }

  private int dp(float v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
  }
}
