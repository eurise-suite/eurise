package app.organicmaps.eurise;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import app.organicmaps.sdk.routing.RoutingController;

/**
 * L'orizzonte col meteo e l'ora sopra la mappa mentre si esplora (tavola MapEsplora): cielo, nuvole, pioggia o neve,
 * sole o luna, colline della stagione, in una fascia in alto (dietro la barra di stato, poi 112 dp; 64 dp in orizzontale).
 * Non prende i tocchi. Si nasconde in percorso e in guida (lì il cielo è nel motore) e con l'interruttore «Orizzonte col
 * meteo e l'ora» (lo stesso del cielo in guida, {@link EuriseSky#PREF}). Si rifà ogni cinque minuti e quando cambia la
 * misura; il controllo dello stato è una lettura al secondo, solo con la finestra visibile. Niente rete.
 */
public final class EuriseSkyBand extends View
{
  private static final long REFRESH_MS = 5 * 60_000L;

  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final Runnable mTick = this::tick;
  private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Rect mDst = new Rect();
  @Nullable
  private Bitmap mBitmap;
  private long mDrawnAt;
  private int mDrawnW, mDrawnH;
  private boolean mDrawing;
  private boolean mRunning;

  public EuriseSkyBand(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setClickable(false);
    setFocusable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  private int topInset()
  {
    final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
    if (insets == null)
      return 0;
    return insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()).top;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    final int band = Math.round((land ? 64 : 112) * getResources().getDisplayMetrics().density);
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), topInset() + band);
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility)
  {
    super.onWindowVisibilityChanged(visibility);
    mHandler.removeCallbacks(mTick);
    mRunning = visibility == VISIBLE;
    if (mRunning)
    {
      requestLayout();
      tick();
    }
  }

  @Override
  protected void onDetachedFromWindow()
  {
    mHandler.removeCallbacks(mTick);
    mRunning = false;
    super.onDetachedFromWindow();
  }

  private void tick()
  {
    final RoutingController rc = RoutingController.get();
    final boolean wanted = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(EuriseSky.PREF, true)
                           && !rc.isPlanning() && !rc.isNavigating() && !EuriseSky.exploring3d();
    // Col cielo vero («3D») si rifà ogni tanto anche quello (ora, meteo), come in guida.
    if (EuriseSky.exploring3d())
      EuriseSky.tick(getContext());
    setVisibility(wanted ? VISIBLE : GONE);
    if (wanted && getWidth() > 0 && getHeight() > 0 && !mDrawing
        && (mBitmap == null || mDrawnW != getWidth() || mDrawnH != getHeight()
            || SystemClock.elapsedRealtime() - mDrawnAt > REFRESH_MS))
    {
      mDrawing = true;
      final int w = getWidth(), h = getHeight();
      EuriseSky.band(getContext(), w, h, b -> {
        mDrawing = false;
        if (mBitmap != null)
          mBitmap.recycle();
        mBitmap = b;
        mDrawnW = w;
        mDrawnH = h;
        mDrawnAt = SystemClock.elapsedRealtime();
        invalidate();
      });
    }
    if (mRunning)
      mHandler.postDelayed(mTick, 1000);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    final Bitmap b = mBitmap;
    if (b == null || b.isRecycled())
      return;
    mDst.set(0, 0, getWidth(), getHeight());
    canvas.drawBitmap(b, null, mDst, mPaint);
  }
}
