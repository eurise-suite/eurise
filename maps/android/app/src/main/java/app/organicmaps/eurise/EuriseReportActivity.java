package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.graphics.PathParser;
import app.organicmaps.R;
import app.organicmaps.sdk.editor.Editor;
import app.organicmaps.sdk.editor.OsmOAuth;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Eurise Mappe: «Segnala qui» (tavola MapSegnala). Sei segnalazioni con un simbolo nei colori della suite; vanno su
 * OpenStreetMap come nota. Con l'account OSM collegato passa dall'editor di CoMaps (che la carica con il tuo nome);
 * senza account diventa una nota anonima (l'API di OSM lo permette). Esce solo il punto e il testo.
 */
public class EuriseReportActivity extends Activity
{
  public static final String EXTRA_LAT = "eu.eurise.maps.LAT";
  public static final String EXTRA_LON = "eu.eurise.maps.LON";
  public static final String EXTRA_PLACE = "eu.eurise.maps.PLACE";

  /** Una segnalazione: etichetta, tinta della suite, disegno 24×24, testo per i volontari di OSM. */
  private static final class Kind
  {
    final int label;
    final int tint;
    final String path;
    final String extra;
    final String osm;
    Kind(int label, int tint, String path, String extra, String osm)
    {
      this.label = label; this.tint = tint; this.path = path; this.extra = extra; this.osm = osm;
    }
  }

  private static final Kind[] KINDS = {
      new Kind(R.string.eurise_report_works, 0xFF85672B, "M12 3.5 L17.5 19 H6.5 Z M9.2 11.5 H14.8 M7.9 15.3 H16.1 M4 19 H20", null,
               "Lavori in corso (roadworks)"),
      new Kind(R.string.eurise_report_closed, 0xFF8C2F39, "M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0 M7.5 12 H16.5", null,
               "Strada chiusa (road closed)"),
      new Kind(R.string.eurise_report_obstacle, 0xFFA0522D,
               "M3.5 8 H20.5 V13 H3.5 Z M7 8 L4.5 13 M12 8 L9.5 13 M17 8 L14.5 13 M6 13 V19 M18 13 V19", null,
               "Ostacolo fisso (permanent obstacle)"),
      new Kind(R.string.eurise_report_camera, 0xFF3E5C8A,
               "M4 6 H15 V13 H4 Z M15 8.5 L19.5 6.5 V12.5 L15 10.5 M9.5 13 V20 M6.5 20 H12.5 M7.3 9.5 a2.2 2.2 0 1 0 4.4 0 a2.2 2.2 0 1 0 -4.4 0",
               null, "Autovelox fisso (fixed speed camera)"),
      new Kind(R.string.eurise_report_limit, 0xFF6B4A7A, "M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0", "50",
               "Limite di velocità sbagliato (wrong speed limit)"),
      new Kind(R.string.eurise_report_other, 0xFF55585E,
               "M12 21 c0 0 -6.5 -5.6 -6.5 -11 a6.5 6.5 0 0 1 13 0 c0 5.4 -6.5 11 -6.5 11 z M10 8.3 a2 2 0 1 1 2.6 1.9 c-0.4 0.2 -0.6 0.5 -0.6 1 v0.6 M12 14 h0.01",
               null, "Errore sulla mappa (map error)"),
  };

  private int mChosen = 0;
  private double mLat;
  private double mLon;
  private final View[] mTiles = new View[KINDS.length];
  private Button mSend;
  private EditText mText;

  public static void open(@NonNull Activity from, double lat, double lon, String place)
  {
    from.startActivity(new Intent(from, EuriseReportActivity.class)
                           .putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LON, lon).putExtra(EXTRA_PLACE, place));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    mLat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
    mLon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
    if (Double.isNaN(mLat) || Double.isNaN(mLon))
    {
      finish();
      return;
    }
    final boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                         == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    final int ground = dark ? 0xFF161A21 : 0xFFF4F4F1;
    final int card = dark ? 0xFF222833 : 0xFFFFFFFF;
    final int ink = dark ? 0xFFE6E9F0 : 0xFF1E1D1B;
    final int muted = dark ? 0xFFA9AEB7 : 0xFF56564F;

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(0x33000000);
    root.setOnClickListener(v -> finish());

    LinearLayout sheet = new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(dp(16), dp(12), dp(16), dp(24));
    sheet.setBackground(roundedTop(ground, dp(26)));
    sheet.setClickable(true);

    View handle = new View(this);
    handle.setBackground(rounded(0x33888880, dp(2)));
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
    hp.gravity = Gravity.CENTER_HORIZONTAL;
    sheet.addView(handle, hp);

    sheet.addView(text(getString(R.string.eurise_report_title), 20, true, ink), lp(dp(12)));
    String place = getIntent().getStringExtra(EXTRA_PLACE);
    String where = String.format(Locale.ROOT, "%.5f, %.5f", mLat, mLon);
    sheet.addView(text((place == null || place.isEmpty()) ? where : place + " · " + where, 12.5f, false, muted), lp(dp(2)));

    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(3);
    for (int i = 0; i < KINDS.length; i++)
    {
      final int index = i;
      LinearLayout tile = new LinearLayout(this);
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setGravity(Gravity.CENTER);
      tile.setClickable(true);
      tile.setContentDescription(getString(KINDS[i].label));
      tile.setOnClickListener(v -> choose(index, card));
      SymbolView symbol = new SymbolView(this, KINDS[i], dark);
      tile.addView(symbol, new LinearLayout.LayoutParams(dp(42), dp(42)));
      TextView name = text(getString(KINDS[i].label), 12, true, ink);
      name.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      np.topMargin = dp(6);
      tile.addView(name, np);
      GridLayout.LayoutParams gp = new GridLayout.LayoutParams(GridLayout.spec(i / 3, 1f), GridLayout.spec(i % 3, 1f));
      gp.width = 0;
      gp.height = dp(92);
      gp.setMargins(dp(4), dp(4), dp(4), dp(4));
      grid.addView(tile, gp);
      mTiles[i] = tile;
    }
    sheet.addView(grid, lp(dp(10)));

    mText = new EditText(this);
    mText.setHint(R.string.eurise_report_hint);
    mText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    mText.setMaxLines(3);
    mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    mText.setTextColor(ink);
    mText.setHintTextColor(muted);
    mText.setBackground(rounded(card, dp(16)));
    mText.setPadding(dp(14), dp(10), dp(14), dp(10));
    sheet.addView(mText, lp(dp(8)));

    LinearLayout info = new LinearLayout(this);
    info.setOrientation(LinearLayout.VERTICAL);
    info.setBackground(rounded(card, dp(18)));
    info.setPadding(dp(14), dp(11), dp(14), dp(11));
    info.addView(text(getString(R.string.eurise_report_osm_title), 14, true, ink));
    info.addView(text(getString(OsmOAuth.isAuthorized() ? R.string.eurise_report_osm_account : R.string.eurise_report_osm_anon), 13, false, ink), lp(dp(4)));
    info.addView(text(getString(R.string.eurise_report_privacy), 12.5f, false, muted), lp(dp(4)));
    sheet.addView(info, lp(dp(10)));

    TextView later = text(getString(R.string.eurise_report_live_later), 12.5f, false, muted);
    later.setBackground(rounded(dark ? 0xFF1E232C : 0xFFECECE7, dp(14)));
    later.setPadding(dp(14), dp(9), dp(14), dp(9));
    sheet.addView(later, lp(dp(8)));

    mSend = new Button(this);
    mSend.setAllCaps(false);
    mSend.setTextColor(Color.WHITE);
    mSend.setTypeface(Typeface.DEFAULT_BOLD);
    mSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    mSend.setBackground(rounded(0xFF1F2A44, dp(25)));
    mSend.setOnClickListener(v -> send());
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
    bp.topMargin = dp(12);
    sheet.addView(mSend, bp);

    ScrollView scroll = new ScrollView(this);
    scroll.addView(sheet);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    root.addView(scroll, sp);
    setContentView(root);
    choose(0, card);
  }

  private void choose(int index, int card)
  {
    mChosen = index;
    for (int i = 0; i < mTiles.length; i++)
    {
      Kind k = KINDS[i];
      GradientDrawable bg = rounded(i == index ? mix(k.tint, Color.WHITE, 0.88f) : card, dp(16));
      if (i == index)
        bg.setStroke(dp(2), k.tint);
      mTiles[i].setBackground(bg);
    }
    mSend.setText(getString(R.string.eurise_report_send, getString(KINDS[index].label)));
  }

  private void send()
  {
    final Kind k = KINDS[mChosen];
    final String extra = mText.getText().toString().trim();
    final String body = k.osm + (extra.isEmpty() ? "" : ": " + extra) + "\n#EuriseMappe";
    mSend.setEnabled(false);
    if (OsmOAuth.isAuthorized())
    {
      Editor.nativeCreateStandaloneNote(mLat, mLon, body);
      new Thread(Editor::uploadChanges).start();
      done(true);
      return;
    }
    new Thread(() -> {
      // Il centro privacy dell'app Eurise (servizio MapReports) può aver spento le note a OpenStreetMap.
      final boolean blocked = Boolean.FALSE.equals(EuriseGuideReports.hub(getApplicationContext(), "status"));
      final boolean ok = !blocked && anonymousNote(mLat, mLon, body);
      if (ok)
        EuriseGuideReports.hub(getApplicationContext(), "log");
      runOnUiThread(() -> {
        if (blocked)
        {
          Toast.makeText(this, R.string.eurise_review_blocked, Toast.LENGTH_LONG).show();
          mSend.setEnabled(true);
        }
        else
          done(ok);
      });
    }).start();
  }

  private void done(boolean ok)
  {
    Toast.makeText(this, ok ? R.string.eurise_report_sent : R.string.eurise_report_failed, Toast.LENGTH_LONG).show();
    if (ok)
      finish();
    else
      mSend.setEnabled(true);
  }

  /** Nota anonima con l'API 0.6 di OpenStreetMap (POST /notes): niente account, solo punto e testo. */
  static boolean anonymousNote(double lat, double lon, String text)
  {
    HttpURLConnection c = null;
    try
    {
      String form = "lat=" + String.format(Locale.ROOT, "%.6f", lat) + "&lon=" + String.format(Locale.ROOT, "%.6f", lon)
                    + "&text=" + URLEncoder.encode(text, "UTF-8");
      c = (HttpURLConnection) new URL("https://api.openstreetmap.org/api/0.6/notes").openConnection();
      c.setRequestMethod("POST");
      c.setDoOutput(true);
      c.setConnectTimeout(15000);
      c.setReadTimeout(15000);
      c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      c.setRequestProperty("User-Agent", "EuriseMappe");
      try (OutputStream out = c.getOutputStream())
      {
        out.write(form.getBytes("UTF-8"));
      }
      int code = c.getResponseCode();
      return code >= 200 && code < 300;
    }
    catch (Exception e)
    {
      return false;
    }
    finally
    {
      if (c != null)
        c.disconnect();
    }
  }

  private TextView text(String s, float size, boolean bold, int color)
  {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTextColor(color);
    if (bold)
      t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  private LinearLayout.LayoutParams lp(int top)
  {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    p.topMargin = top;
    return p;
  }

  private int dp(float v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
  }

  private static GradientDrawable rounded(int color, int radius)
  {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  private static GradientDrawable roundedTop(int color, int radius)
  {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
    return d;
  }

  static int mix(int a, int b, float t)
  {
    int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
    int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
    int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
    return Color.rgb(r, g, bl);
  }

  /** Il simbolo della segnalazione: tondo chiaro nella tinta, disegno nella tinta piena (come «Eurise a colori»). */
  private static final class SymbolView extends View
  {
    private final Kind mKind;
    private final boolean mDark;
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath;

    SymbolView(Context context, Kind kind, boolean dark)
    {
      super(context);
      mKind = kind;
      mDark = dark;
      Path p;
      try { p = PathParser.createPathFromPathData(kind.path); }
      catch (RuntimeException e) { p = new Path(); }
      mPath = p;
      int ink = dark ? mix(kind.tint, Color.WHITE, 0.45f) : kind.tint;
      mStroke.setStyle(Paint.Style.STROKE);
      mStroke.setStrokeCap(Paint.Cap.ROUND);
      mStroke.setStrokeJoin(Paint.Join.ROUND);
      mStroke.setColor(ink);
      mFill.setColor(dark ? mix(kind.tint, 0xFF161A21, 0.55f) : mix(kind.tint, Color.WHITE, 0.84f));
      mText.setColor(ink);
      mText.setTypeface(Typeface.DEFAULT_BOLD);
      mText.setTextAlign(Paint.Align.CENTER);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas)
    {
      float w = getWidth();
      float h = getHeight();
      canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2, mFill);
      float scale = Math.min(w, h) / 24f * 0.58f;
      canvas.save();
      canvas.translate(w / 2 - 12 * scale, h / 2 - 12 * scale);
      canvas.scale(scale, scale);
      mStroke.setStrokeWidth(1.9f);
      canvas.drawPath(mPath, mStroke);
      if (mKind.extra != null)
      {
        mText.setTextSize(9f);
        canvas.drawText(mKind.extra, 12f, 15.3f, mText);
      }
      canvas.restore();
    }
  }
}
