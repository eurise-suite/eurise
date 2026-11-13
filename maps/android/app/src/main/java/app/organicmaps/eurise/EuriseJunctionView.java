package app.organicmaps.eurise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.eurise.car.EuriseCarInfo;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.RoutingInfo;

/**
 * La scena dell'uscita o della rotonda in guida, sul telefono (tavole MapIncrocio e MapRotonda): compare da sola sopra
 * la mappa quando la manovra si avvicina, con le frecce che scorrono, e sparisce dopo. Mentre è nascosta non disegna
 * nulla e non consuma batteria.
 */
public class EuriseJunctionView extends View
{
  @Nullable private EuriseJunctionScene.Model mModel;
  private final long mStart = SystemClock.uptimeMillis();

  public EuriseJunctionView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setVisibility(GONE);
    setClipToOutline(true);
    setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline(View view, Outline outline)
      {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 18 * getResources().getDisplayMetrics().density);
      }
    });
  }

  /** A ogni aggiornamento della guida. */
  public void update(@Nullable RoutingInfo info)
  {
    final boolean fast = EuriseCarInfo.isFastRoad()
                         || (info != null && (info.carDirection == CarDirection.EXIT_HIGHWAY_TO_LEFT
                                              || info.carDirection == CarDirection.EXIT_HIGHWAY_TO_RIGHT));
    mModel = EuriseJunctionScene.from(info, fast);
    final int visibility = mModel == null ? GONE : VISIBLE;
    if (getVisibility() != visibility)
      setVisibility(visibility);
    if (mModel != null)
    {
      setContentDescription(describe(mModel));
      invalidate();
    }
  }

  public void hide()
  {
    mModel = null;
    setVisibility(GONE);
  }

  @NonNull
  private String describe(@NonNull EuriseJunctionScene.Model m)
  {
    if (m.kind == EuriseJunctionScene.Kind.ROUNDABOUT)
      return getContext().getString(app.organicmaps.R.string.eurise_junction_roundabout, m.exitNum)
             + (m.street.isEmpty() ? "" : ", " + m.street);
    final String exit = m.exitRef.isEmpty() ? "" : getContext().getString(app.organicmaps.R.string.eurise_junction_exit, m.exitRef) + ", ";
    return exit + m.roadRef + " " + m.destination;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    super.onDraw(canvas);
    final EuriseJunctionScene.Model m = mModel;
    if (m == null)
      return;
    final float t = (SystemClock.uptimeMillis() - mStart) / 1000f;
    EuriseJunctionScene.draw(getContext(), canvas, m, getWidth(), getHeight(), t);
    // Le frecce scorrono finché la scena è visibile.
    postInvalidateOnAnimation();
  }
}
