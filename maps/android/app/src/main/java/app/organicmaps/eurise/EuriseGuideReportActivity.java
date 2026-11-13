package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.eurise.EuriseGuideReports.Kind;
import app.organicmaps.eurise.EuriseGuideReports.Report;

/**
 * «Segnala qui» in guida (tavola MapSegnalaGuida): sei voci in due gruppi, un tocco e basta. Il punto e la direzione si
 * prendono al tocco; per 5 secondi c'è «Annulla», poi il foglio si chiude da sé. Si apre dalla bandierina della guida o
 * da Alfred («Alfred, autovelox»: arriva con {@link #EXTRA_KIND} e segna subito).
 */
public class EuriseGuideReportActivity extends Activity
{
  /** Nome di {@link Kind} da segnare subito (da Alfred, app della suite con la stessa firma). */
  public static final String EXTRA_KIND = "eu.eurise.maps.REPORT_KIND";

  private static final int UNDO_SECONDS = 5;
  private static final Kind[] FOR_OSM = {Kind.CAMERA, Kind.LIMIT, Kind.CLOSED};
  private static final Kind[] SHORT = {Kind.WORKS, Kind.QUEUE, Kind.DANGER};

  private final Handler mMain = new Handler(Looper.getMainLooper());
  private View mUndo;
  private TextView mUndoText;
  private Countdown mCountdown;
  @Nullable private Report mLast;
  private long mUndoUntil;
  private int mInk;

  public static void open(@NonNull Activity from)
  {
    from.startActivity(new Intent(from, EuriseGuideReportActivity.class));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    final boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                         == Configuration.UI_MODE_NIGHT_YES;
    final int ground = dark ? 0xFF161A21 : 0xFFF4F4F1;
    final int card = dark ? 0xFF222833 : 0xFFFFFFFF;
    mInk = dark ? 0xFFE6E9F0 : 0xFF1E1D1B;
    final int muted = dark ? 0xFFA9AEB7 : 0xFF56564F;

    final FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(0x22000000);
    root.setOnClickListener(v -> finish());

    final LinearLayout sheet = new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(dp(14), dp(10), dp(14), dp(24));
    sheet.setBackground(roundedTop(ground, dp(28)));
    sheet.setClickable(true);
    sheet.setElevation(dp(12));

    final View handle = new View(this);
    handle.setBackground(rounded(dark ? 0xFF3A4150 : 0xFFC9C9C2, dp(3)));
    final LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(5));
    hp.gravity = Gravity.CENTER_HORIZONTAL;
    sheet.addView(handle, hp);

    final LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.BOTTOM);
    head.addView(text(getString(R.string.eurise_guide_title), 18, true, mInk),
                 new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    head.addView(text(getString(R.string.eurise_guide_one_tap), 12, false, muted));
    sheet.addView(head, lp(dp(10)));

    sheet.addView(caption(getString(R.string.eurise_guide_group_osm), muted), lp(dp(12)));
    sheet.addView(grid(FOR_OSM, card), lp(dp(8)));
    sheet.addView(caption(getString(R.string.eurise_guide_group_short), muted), lp(dp(12)));
    sheet.addView(grid(SHORT, card), lp(dp(8)));

    sheet.addView(undoBar(), lp(dp(10)));

    final TextView foot = text(getString(R.string.eurise_guide_foot), 11.5f, false, muted);
    foot.setLineSpacing(0, 1.2f);
    sheet.addView(foot, lp(dp(10)));

    root.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                     ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    setContentView(root);

    final Kind now = savedInstanceState == null ? Kind.parse(getIntent().getStringExtra(EXTRA_KIND)) : null;
    if (now != null)
      report(now);
  }

  @Override
  protected void onDestroy()
  {
    mMain.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @NonNull
  private GridLayout grid(@NonNull Kind[] kinds, int card)
  {
    final GridLayout grid = new GridLayout(this);
    grid.setColumnCount(3);
    for (int i = 0; i < kinds.length; i++)
    {
      final Kind k = kinds[i];
      final LinearLayout tile = new LinearLayout(this);
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setGravity(Gravity.CENTER);
      tile.setBackground(rounded(card, dp(18)));
      tile.setClickable(true);
      tile.setFocusable(true);
      tile.setContentDescription(getString(k.label));
      tile.setOnClickListener(v -> report(k));
      final ImageView icon = new ImageView(this);
      icon.setImageResource(k.icon);
      icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      tile.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
      final TextView name = text(getString(k.label), 12.5f, true, mInk);
      name.setGravity(Gravity.CENTER);
      name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      final LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                         ViewGroup.LayoutParams.WRAP_CONTENT);
      np.topMargin = dp(6);
      tile.addView(name, np);
      final GridLayout.LayoutParams gp = new GridLayout.LayoutParams(GridLayout.spec(0, 1f), GridLayout.spec(i, 1f));
      gp.width = 0;
      gp.height = dp(82);
      gp.setMargins(i == 0 ? 0 : dp(4), 0, i == kinds.length - 1 ? 0 : dp(4), 0);
      grid.addView(tile, gp);
    }
    return grid;
  }

  /** La barra scura con il conto alla rovescia: «Autovelox segnato qui · SS36» e «Annulla». */
  @NonNull
  private View undoBar()
  {
    final LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(12), dp(10), dp(12), dp(10));
    bar.setBackground(rounded(0xFF1F2A44, dp(16)));
    bar.setVisibility(View.GONE);

    mCountdown = new Countdown(this);
    bar.addView(mCountdown, new LinearLayout.LayoutParams(dp(34), dp(34)));

    mUndoText = text("", 13, false, Color.WHITE);
    mUndoText.setLineSpacing(0, 1.15f);
    final LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    tp.setMargins(dp(10), 0, dp(10), 0);
    bar.addView(mUndoText, tp);

    final Button undo = new Button(this);
    undo.setAllCaps(false);
    undo.setText(R.string.eurise_guide_undo);
    undo.setTextColor(Color.WHITE);
    undo.setTypeface(Typeface.DEFAULT_BOLD);
    undo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    undo.setMinWidth(0);
    undo.setMinimumWidth(0);
    undo.setMinHeight(0);
    undo.setMinimumHeight(0);
    undo.setPadding(dp(14), 0, dp(14), 0);
    undo.setBackground(rounded(0x29FFFFFF, dp(18)));
    undo.setOnClickListener(v -> undo());
    bar.addView(undo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
    mUndo = bar;
    return bar;
  }

  /** Un tocco: segna subito, poi 5 secondi per ripensarci. */
  private void report(@NonNull Kind kind)
  {
    final Report r = EuriseGuideReports.add(this, kind);
    if (r == null)
    {
      Toast.makeText(this, R.string.eurise_guide_no_position, Toast.LENGTH_LONG).show();
      return;
    }
    mLast = r;
    final SpannableStringBuilder s = new SpannableStringBuilder(getString(kind.label));
    s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    s.append(' ').append(EuriseGuideReports.placed(this, r));
    mUndoText.setText(s);
    mUndo.setVisibility(View.VISIBLE);
    mUndo.announceForAccessibility(s);
    mUndoUntil = SystemClock.elapsedRealtime() + UNDO_SECONDS * 1000L;
    mMain.removeCallbacksAndMessages(null);
    tick();
  }

  private void tick()
  {
    final long left = mUndoUntil - SystemClock.elapsedRealtime();
    if (left <= 0)
    {
      finish();
      return;
    }
    mCountdown.set(left / (UNDO_SECONDS * 1000f), (int) Math.ceil(left / 1000.0));
    mMain.postDelayed(this::tick, 100);
  }

  private void undo()
  {
    mMain.removeCallbacksAndMessages(null);
    if (mLast != null)
      EuriseGuideReports.remove(this, mLast.id);
    mLast = null;
    mUndo.setVisibility(View.GONE);
    Toast.makeText(this, R.string.eurise_guide_undone, Toast.LENGTH_SHORT).show();
  }

  @NonNull
  private TextView caption(@NonNull String s, int color)
  {
    final TextView t = text(s, 11, true, color);
    t.setLetterSpacing(0.08f);
    return t;
  }

  @NonNull
  private TextView text(@NonNull CharSequence s, float size, boolean bold, int color)
  {
    final TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTextColor(color);
    if (bold)
      t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  @NonNull
  private LinearLayout.LayoutParams lp(int top)
  {
    final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                      ViewGroup.LayoutParams.WRAP_CONTENT);
    p.topMargin = top;
    return p;
  }

  private int dp(float v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
  }

  @NonNull
  static GradientDrawable rounded(int color, int radius)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  @NonNull
  private static GradientDrawable roundedTop(int color, int radius)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
    return d;
  }

  /** Il tondo che si svuota con i secondi rimasti dentro. */
  private static final class Countdown extends View
  {
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBox = new RectF();
    private float mFraction = 1f;
    private int mSeconds = UNDO_SECONDS;

    Countdown(Context c)
    {
      super(c);
      final float density = c.getResources().getDisplayMetrics().density;
      mTrack.setStyle(Paint.Style.STROKE);
      mTrack.setStrokeWidth(3 * density);
      mTrack.setColor(0x59FFFFFF);
      mArc.set(mTrack);
      mArc.setColor(Color.WHITE);
      mArc.setStrokeCap(Paint.Cap.ROUND);
      mText.setColor(Color.WHITE);
      mText.setTypeface(Typeface.DEFAULT_BOLD);
      mText.setTextAlign(Paint.Align.CENTER);
      mText.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void set(float fraction, int seconds)
    {
      mFraction = fraction;
      mSeconds = seconds;
      invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas)
    {
      final float inset = mTrack.getStrokeWidth() / 2 + 1;
      mBox.set(inset, inset, getWidth() - inset, getHeight() - inset);
      canvas.drawOval(mBox, mTrack);
      canvas.drawArc(mBox, -90, 360 * mFraction, false, mArc);
      final float y = getHeight() / 2f - (mText.descent() + mText.ascent()) / 2;
      canvas.drawText(String.valueOf(mSeconds), getWidth() / 2f, y, mText);
    }
  }
}
