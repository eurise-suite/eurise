package app.organicmaps.eurise;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import app.organicmaps.R;
import app.organicmaps.eurise.EuriseGuideReports.Report;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A fine viaggio (tavola MapSegnalaGuida): le segnalazioni per OpenStreetMap, un'occhiata e poi «Manda». Ognuna diventa
 * una nota anonima (punto, tipo, direzione, strada; niente di chi segnala). Quelle tolte dall'elenco si scartano;
 * «Più tardi» le tiene per la prossima volta. Il centro privacy dell'app Eurise può spegnere l'invio.
 */
public class EuriseReportReviewActivity extends Activity
{
  private final List<Report> mReports = new ArrayList<>();
  private final List<CheckBox> mChecks = new ArrayList<>();
  private Button mSend;
  private TextView mBlocked;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    mReports.addAll(EuriseGuideReports.pending(this));
    if (mReports.isEmpty())
    {
      EuriseGuideReports.reviewed(this);
      finish();
      return;
    }
    final boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                         == Configuration.UI_MODE_NIGHT_YES;
    final int ground = dark ? 0xFF161A21 : 0xFFF4F4F1;
    final int card = dark ? 0xFF222833 : 0xFFFFFFFF;
    final int ink = dark ? 0xFFE6E9F0 : 0xFF1E1D1B;
    final int muted = dark ? 0xFFA9AEB7 : 0xFF56564F;
    final int accent = dark ? 0xFFA9C1F2 : 0xFF1F2A44;

    final FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(0x33000000);

    final LinearLayout sheet = new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(dp(16), dp(12), dp(16), dp(22));
    sheet.setBackground(roundedTop(ground, dp(28)));
    sheet.setClickable(true);

    sheet.addView(text(getString(R.string.eurise_review_title), 20, true, ink), lp(0));
    sheet.addView(text(getString(R.string.eurise_review_lead), 13, false, muted), lp(dp(4)));

    final DateFormat time = DateFormat.getTimeInstance(DateFormat.SHORT);
    for (Report r : mReports)
    {
      final LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(12), dp(10), dp(6), dp(10));
      row.setBackground(EuriseGuideReportActivity.rounded(card, dp(16)));

      final ImageView icon = new ImageView(this);
      icon.setImageResource(r.kind.icon);
      icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

      final LinearLayout words = new LinearLayout(this);
      words.setOrientation(LinearLayout.VERTICAL);
      String title = getString(r.kind.label);
      if (r.kind == EuriseGuideReports.Kind.LIMIT && r.limit > 0)
        title = getString(R.string.eurise_review_limit, title, r.limit);
      words.addView(text(title, 14.5f, true, ink));
      final StringBuilder where = new StringBuilder(time.format(new Date(r.time)));
      if (!TextUtils.isEmpty(r.road))
        where.append(" · ").append(r.road);
      final String dir = EuriseGuideReports.compass(this, r.bearing);
      if (dir != null)
        where.append(" · ").append(getString(R.string.eurise_review_heading, dir));
      words.addView(text(where, 12, false, muted));
      final LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      wp.setMargins(dp(12), 0, dp(6), 0);
      row.addView(words, wp);

      final CheckBox check = new CheckBox(this);
      check.setChecked(true);
      check.setContentDescription(getString(R.string.eurise_review_include, getString(r.kind.label)));
      check.setOnCheckedChangeListener((b, on) -> refresh());
      row.addView(check);
      row.setOnClickListener(v -> check.toggle());
      mChecks.add(check);
      sheet.addView(row, lp(dp(8)));
    }

    final LinearLayout info = new LinearLayout(this);
    info.setOrientation(LinearLayout.VERTICAL);
    info.setPadding(dp(14), dp(11), dp(14), dp(11));
    info.setBackground(EuriseGuideReportActivity.rounded(card, dp(16)));
    info.addView(text(getString(R.string.eurise_review_osm_title), 13.5f, true, ink));
    final TextView what = text(getString(R.string.eurise_review_osm_body), 12.5f, false, muted);
    what.setLineSpacing(0, 1.15f);
    info.addView(what, lp(dp(4)));
    sheet.addView(info, lp(dp(12)));

    mBlocked = text(getString(R.string.eurise_review_blocked), 12.5f, true, 0xFFC8102E);
    mBlocked.setVisibility(View.GONE);
    sheet.addView(mBlocked, lp(dp(8)));

    mSend = new Button(this);
    mSend.setAllCaps(false);
    mSend.setTextColor(dark ? 0xFF14192A : Color.WHITE);
    mSend.setTypeface(Typeface.DEFAULT_BOLD);
    mSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    mSend.setBackground(EuriseGuideReportActivity.rounded(accent, dp(25)));
    mSend.setOnClickListener(v -> send());
    final LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
    bp.topMargin = dp(12);
    sheet.addView(mSend, bp);

    final Button later = new Button(this);
    later.setAllCaps(false);
    later.setText(R.string.eurise_review_later);
    later.setTextColor(accent);
    later.setTypeface(Typeface.DEFAULT_BOLD);
    later.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    later.setBackgroundColor(Color.TRANSPARENT);
    later.setOnClickListener(v -> {
      EuriseGuideReports.reviewed(this);
      finish();
    });
    sheet.addView(later, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

    final ScrollView scroll = new ScrollView(this);
    scroll.addView(sheet);
    root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                      ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    setContentView(root);
    refresh();

    // Il centro privacy dell'app Eurise può aver spento le note a OpenStreetMap.
    new Thread(() -> {
      final Boolean on = EuriseGuideReports.hub(getApplicationContext(), "status");
      runOnUiThread(() -> {
        if (Boolean.FALSE.equals(on) && !isFinishing())
        {
          mBlocked.setVisibility(View.VISIBLE);
          refresh();
        }
      });
    }).start();
  }

  @Override
  public void onBackPressed()
  {
    EuriseGuideReports.reviewed(this);
    super.onBackPressed();
  }

  private int checked()
  {
    int n = 0;
    for (CheckBox c : mChecks)
      if (c.isChecked())
        n++;
    return n;
  }

  private void refresh()
  {
    final int n = checked();
    final boolean blocked = mBlocked.getVisibility() == View.VISIBLE;
    mSend.setText(n == 0 ? getString(R.string.eurise_review_discard) : getString(R.string.eurise_review_send, n));
    mSend.setEnabled(n == 0 || !blocked);
    mSend.setAlpha(mSend.isEnabled() ? 1f : 0.45f);
  }

  /** Manda le spuntate (una nota ciascuna), scarta le altre; quelle non partite restano per la prossima volta. */
  private void send()
  {
    final List<Report> go = new ArrayList<>();
    for (int i = 0; i < mReports.size(); i++)
    {
      if (mChecks.get(i).isChecked())
        go.add(mReports.get(i));
      else
        EuriseGuideReports.remove(this, mReports.get(i).id);
    }
    if (go.isEmpty())
    {
      EuriseGuideReports.reviewed(this);
      finish();
      return;
    }
    mSend.setEnabled(false);
    mSend.setText(R.string.eurise_review_sending);
    new Thread(() -> {
      // Ultimo controllo: il centro privacy può averle spente mentre l'elenco era aperto.
      if (Boolean.FALSE.equals(EuriseGuideReports.hub(getApplicationContext(), "status")))
      {
        runOnUiThread(() -> {
          mBlocked.setVisibility(View.VISIBLE);
          refresh();
        });
        return;
      }
      int sent = 0;
      for (Report r : go)
      {
        if (EuriseReportActivity.anonymousNote(r.lat, r.lon, EuriseGuideReports.noteText(r)))
        {
          EuriseGuideReports.remove(getApplicationContext(), r.id);
          EuriseGuideReports.hub(getApplicationContext(), "log");
          sent++;
        }
      }
      final int ok = sent;
      runOnUiThread(() -> {
        // Quelle non partite (niente rete) restano: l'elenco torna a fine del prossimo viaggio.
        Toast.makeText(this, ok == go.size() ? getString(R.string.eurise_review_sent, ok)
                                              : getString(R.string.eurise_review_partly, ok, go.size()),
                       Toast.LENGTH_LONG).show();
        EuriseGuideReports.reviewed(this);
        finish();
      });
    }).start();
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
  private static GradientDrawable roundedTop(int color, int radius)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
    return d;
  }
}
