package app.organicmaps.eurise;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;

/**
 * La bolla allungata a destra, in guida, con la musica che suona o la chiamata (tavole MapBollaAuto, MapBollaAutoChiamata,
 * MapBollaTelefono, approvate il 16 novembre). La stessa vista sul telefono (sotto il cartello della manovra) e sullo
 * schermo dell'auto (in alto a destra dell'area libera della mappa). Si legge e basta: i comandi della musica e della
 * chiamata restano quelli dell'auto e del telefono.
 *
 * <p>Musica: copertina con il simbolo dell'app, titolo, autore, album e anno (se l'app li dà), tempo passato, che manca e
 * durata, e la riga del testo con il servizio «Testi delle canzoni». In pausa sparisce dopo 10 s. Chiamata: al posto della
 * musica, con foto o iniziali, nome, da dove passa la voce, durata e l'eventuale impegno in comune; finita, dopo 5 s
 * torna la musica. Sul telefono un tocco apre l'app che suona (o la chiamata), la pressione lunga la nasconde per il
 * viaggio.
 */
public final class EuriseMediaBubble extends LinearLayout implements Runnable
{
  /** Chi dice se è notte: il tema dell'auto o quello del telefono. */
  public interface Night
  {
    boolean isNight();
  }

  private static final long PAUSE_HIDE_MS = 10_000;
  private static final long CALL_GRACE_MS = 5_000;
  private static boolean sHiddenThisTrip;

  private final boolean mCar;
  private final Night mNight;
  private final float mMaxW, mFraction;
  private final ImageView mArt, mBadge;
  private final TextView mInitials, mTitle, mSub, mSub2, mBig, mBigSub, mPos, mRem, mLyric, mLyricNext;
  private final LinearLayout mTimes;
  private final View mBarFill;
  private final FrameLayout mBar;
  private final View mLyricBox;
  private long mCallEndedAt;
  private boolean mHadCall;

  private EuriseMediaBubble(@NonNull Context c, boolean car, @NonNull Night night)
  {
    super(c);
    mCar = car;
    mNight = night;
    mMaxW = dp(car ? 440 : 300);
    mFraction = car ? 0.5f : 0.8f;
    setOrientation(VERTICAL);
    final int pad = dp(car ? 12 : 9);
    setPadding(pad, pad, dp(car ? 16 : 12), pad);
    setElevation(dp(4));
    setVisibility(GONE);

    final LinearLayout row = new LinearLayout(c);
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    final int artSize = dp(car ? 60 : 46);
    final FrameLayout artFrame = new FrameLayout(c);
    row.addView(artFrame, new LayoutParams(artSize + dp(4), artSize + dp(4)));
    mArt = new ImageView(c);
    mArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
    mArt.setClipToOutline(true);
    artFrame.addView(mArt, new FrameLayout.LayoutParams(artSize, artSize));
    mInitials = text(c, car ? 22 : 17, true);
    mInitials.setGravity(Gravity.CENTER);
    mInitials.setTextColor(0xFFFFFFFF);
    artFrame.addView(mInitials, new FrameLayout.LayoutParams(artSize, artSize));
    mBadge = new ImageView(c);
    final FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END | Gravity.BOTTOM);
    artFrame.addView(mBadge, badge);

    final LinearLayout col = new LinearLayout(c);
    col.setOrientation(VERTICAL);
    final LayoutParams colLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    colLp.leftMargin = dp(car ? 12 : 10);
    row.addView(col, colLp);
    mTitle = text(c, car ? 18 : 15, true);
    mSub = text(c, car ? 14.5f : 12.5f, false);
    mSub2 = text(c, car ? 12.5f : 11.5f, false);
    col.addView(mTitle);
    col.addView(mSub);
    col.addView(mSub2);

    mTimes = new LinearLayout(c);
    mTimes.setOrientation(VERTICAL);
    mTimes.setGravity(Gravity.END);
    final LayoutParams timesLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    timesLp.leftMargin = dp(10);
    row.addView(mTimes, timesLp);
    mBig = text(c, car ? 22 : 17, true);
    mBigSub = text(c, car ? 12.5f : 11, false);
    mTimes.addView(mBig);
    mTimes.addView(mBigSub);

    final LinearLayout bar = new LinearLayout(c);
    bar.setOrientation(HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    final LayoutParams barRowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    barRowLp.topMargin = dp(car ? 8 : 6);
    addView(bar, barRowLp);
    mPos = text(c, car ? 13 : 11.5f, true);
    bar.addView(mPos);
    mBar = new FrameLayout(c);
    final LayoutParams trackLp = new LayoutParams(0, dp(car ? 6 : 5), 1f);
    trackLp.leftMargin = dp(8);
    trackLp.rightMargin = dp(8);
    bar.addView(mBar, trackLp);
    mBarFill = new View(c);
    mBar.addView(mBarFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
    mRem = text(c, car ? 13 : 11.5f, true);
    bar.addView(mRem);

    final LinearLayout lyric = new LinearLayout(c);
    lyric.setOrientation(VERTICAL);
    final LayoutParams lyricLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    lyricLp.topMargin = dp(car ? 7 : 5);
    addView(lyric, lyricLp);
    mLyricBox = lyric;
    mLyric = text(c, car ? 15 : 13, true);
    mLyricNext = text(c, 13, false);
    lyric.addView(mLyric);
    if (car)
      lyric.addView(mLyricNext);

    if (!car)
    {
      setOnClickListener(v -> open());
      setOnLongClickListener(v -> {
        sHiddenThisTrip = true;
        refresh();
        Toast.makeText(getContext(), R.string.eurise_np_hidden, Toast.LENGTH_SHORT).show();
        return true;
      });
    }
  }

  // ---- Dove si aggancia ---------------------------------------------------------------------------------------------

  /** Sullo schermo dell'auto: nell'area libera della mappa (SurfaceCallback.initSpeedPill), in alto a destra. */
  @NonNull
  public static View car(@NonNull Context car, @NonNull Night night)
  {
    final EuriseMediaBubble b = new EuriseMediaBubble(car, true, night);
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     Gravity.TOP | Gravity.END);
    lp.topMargin = b.dp(8);
    lp.rightMargin = b.dp(8);
    b.setLayoutParams(lp);
    return b;
  }

  /**
   * Sul telefono, nella guida (NavigationController): a destra sotto il cartello della manovra (e corsie e meteo, se ci
   * sono); in orizzontale in alto, accanto a ciò che sta sul lato destro (barra o colonna della manovra).
   */
  public static void attachPhone(@Nullable View navFrame)
  {
    if (!(navFrame instanceof FrameLayout))
      return;
    final FrameLayout frame = (FrameLayout) navFrame;
    final EuriseMediaBubble b = new EuriseMediaBubble(frame.getContext(), false,
                                                      app.organicmaps.util.ThemeUtils::isNightTheme);
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     Gravity.TOP | Gravity.END);
    lp.rightMargin = b.dp(12);
    frame.addView(b, lp);
    frame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout()
      {
        if (!b.isAttachedToWindow())
        {
          frame.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          return;
        }
        b.placeOnPhone(frame);
      }
    });
  }

  private void placeOnPhone(@NonNull FrameLayout frame)
  {
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
    final int[] base = new int[2];
    frame.getLocationInWindow(base);
    int top = dp(14), right = dp(12);
    final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    if (land)
    {
      // Ciò che sta nella metà destra (barra della guida o colonna della manovra): la bolla gli sta accanto.
      for (int id : new int[] {R.id.eurise_guida_bar, R.id.eurise_guide_column})
      {
        final View v = frame.findViewById(id);
        if (v == null || v.getVisibility() != VISIBLE || v.getWidth() == 0)
          continue;
        final int[] at = new int[2];
        v.getLocationInWindow(at);
        final int left = at[0] - base[0];
        if (left > frame.getWidth() / 2)
          right = Math.max(right, frame.getWidth() - left + dp(12));
        top = Math.max(top, at[1] - base[1]);
      }
    }
    else
    {
      for (int id : new int[] {R.id.eurise_guide_card, R.id.lanes, R.id.eurise_route_weather})
      {
        final View v = frame.findViewById(id);
        if (v == null || v.getVisibility() != VISIBLE || v.getHeight() == 0)
          continue;
        final int[] at = new int[2];
        v.getLocationInWindow(at);
        top = Math.max(top, at[1] - base[1] + v.getHeight() + dp(8));
      }
    }
    if (lp.topMargin != top || lp.rightMargin != right)
    {
      lp.topMargin = top;
      lp.rightMargin = right;
      setLayoutParams(lp);
    }
  }

  // ---- Battito ------------------------------------------------------------------------------------------------------

  private final Runnable mChanged = this::refresh;

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    EuriseNowPlaying.listen(mChanged);
    refresh();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    EuriseNowPlaying.unlisten(mChanged);
    removeCallbacks(this);
    super.onDetachedFromWindow();
  }

  @Override
  public void run()
  {
    refresh();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec)
  {
    final int avail = MeasureSpec.getSize(widthSpec);
    final float frac = !mCar && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                       ? 0.42f : mFraction;
    final int w = (int) Math.min(mMaxW, avail > 0 ? avail * frac : mMaxW);
    super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightSpec);
  }

  /** Ridisegna e riprogramma il battito (mezzo secondo quando si mostra, per i tempi e il testo). */
  public void refresh()
  {
    removeCallbacks(this);
    final boolean guiding = RoutingController.get().isNavigating();
    if (!guiding)
      sHiddenThisTrip = false;
    final EuriseNowPlaying.Call call = EuriseNowPlaying.call();
    final long now = SystemClock.elapsedRealtime();
    if (call != null)
      mHadCall = true;
    else if (mHadCall)
    {
      mHadCall = false;
      mCallEndedAt = now;
    }
    final EuriseNowPlaying.Media media = EuriseNowPlaying.media();
    final boolean afterCall = mCallEndedAt > 0 && now - mCallEndedAt < CALL_GRACE_MS;
    final boolean mediaOk = media != null && !afterCall && (media.playing || now - media.pausedAt < PAUSE_HIDE_MS);
    if (!guiding || sHiddenThisTrip || (call == null && !mediaOk))
    {
      setVisibility(GONE);
      // Un controllo lento anche nascosta: la guida può cominciare con la musica che già suona, senza altri avvisi.
      postDelayed(this, guiding && (media != null || afterCall) ? 1_000 : 2_000);
      return;
    }
    style();
    if (call != null)
      showCall(call);
    else
      showMedia(media);
    setVisibility(VISIBLE);
    postDelayed(this, 500);
  }

  private void showMedia(@NonNull EuriseNowPlaying.Media m)
  {
    mInitials.setVisibility(GONE);
    mArt.setImageBitmap(m.art);
    mArt.setBackground(m.art == null ? round(0xFF3E5C8A, 12) : null);
    mBadge.setImageDrawable(m.appIcon);
    mBadge.setVisibility(m.appIcon == null ? GONE : VISIBLE);
    set(mTitle, m.title);
    set(mSub, m.artist);
    final String album = NowPlayingText.albumLine(m.album, m.year);
    final String from = m.appName == null ? null : getContext().getString(R.string.eurise_np_from, m.appName);
    set(mSub2, album.isEmpty() ? from : mCar && from != null ? album + " · " + from : album);
    mSub2.setTextColor(muted());
    mTimes.setVisibility(GONE);
    final long pos = m.position();
    final boolean hasDuration = m.durationMs > 0;
    ((View) mBar.getParent()).setVisibility(hasDuration ? VISIBLE : GONE);
    if (hasDuration)
    {
      mPos.setText(NowPlayingText.clock(pos));
      final String rem = NowPlayingText.remaining(pos, m.durationMs);
      mRem.setText(getContext().getString(R.string.eurise_np_remaining, rem, NowPlayingText.clock(m.durationMs)));
      final int w = mBar.getWidth() * NowPlayingText.progress(pos, m.durationMs) / 1000;
      final ViewGroup.LayoutParams lp = mBarFill.getLayoutParams();
      if (lp.width != w)
      {
        lp.width = w;
        mBarFill.setLayoutParams(lp);
      }
    }
    final NowPlayingText.Lyrics l = EuriseLyrics.of(m);
    final int i = l == null ? -1 : l.indexAt(pos);
    final String line = l == null ? null : l.line(i);
    mLyricBox.setVisibility(line == null ? GONE : VISIBLE);
    if (line != null)
    {
      mLyric.setText(line.isEmpty() ? "♪" : line);
      final String next = l.line(i + 1);
      set(mLyricNext, next == null || next.isEmpty() ? null : next);
    }
  }

  private void showCall(@NonNull EuriseNowPlaying.Call call)
  {
    mBadge.setVisibility(GONE);
    if (call.photo != null)
    {
      mArt.setImageBitmap(call.photo);
      mArt.setBackground(null);
      mInitials.setVisibility(GONE);
    }
    else
    {
      mArt.setImageDrawable(null);
      mArt.setBackground(round(0xFF2F6B45, 100));
      mInitials.setText(initials(call.name));
      mInitials.setVisibility(VISIBLE);
    }
    set(mTitle, call.incoming ? getContext().getString(R.string.eurise_np_calling, call.name) : call.name);
    final String route = EuriseNowPlaying.route(getContext());
    final String detail = call.detail;
    set(mSub, detail == null ? route : route == null ? detail : detail + " · " + route);
    set(mSub2, call.together == null ? null : getContext().getString(R.string.eurise_np_together, call.together));
    mSub2.setTextColor(mNight.isNight() ? 0xFF9CC3A9 : 0xFF2F6B45);
    ((View) mBar.getParent()).setVisibility(GONE);
    mLyricBox.setVisibility(GONE);
    mTimes.setVisibility(VISIBLE);
    if (call.incoming)
    {
      mBig.setText("");
      mBig.setVisibility(GONE);
      mBigSub.setText(R.string.eurise_np_incoming);
    }
    else
    {
      mBig.setVisibility(VISIBLE);
      mBig.setText(NowPlayingText.clock(System.currentTimeMillis() - call.start));
      mBigSub.setText(R.string.eurise_np_ongoing);
    }
  }

  private void open()
  {
    final EuriseNowPlaying.Call call = EuriseNowPlaying.call();
    try
    {
      if (call != null && call.open != null)
      {
        call.open.send();
        return;
      }
      final EuriseNowPlaying.Media m = EuriseNowPlaying.media();
      if (m == null)
        return;
      final Intent i = getContext().getPackageManager().getLaunchIntentForPackage(m.pkg);
      if (i != null)
        getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    catch (PendingIntent.CanceledException | RuntimeException ignored)
    {
      // Chiamata finita o app tolta nel frattempo.
    }
  }

  // ---- Aspetto ------------------------------------------------------------------------------------------------------

  private void style()
  {
    final boolean night = mNight.isNight();
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(mCar ? 22 : 20));
    bg.setColor(night ? 0xF01C2333 : 0xF0F8F7F4);
    bg.setStroke(Math.max(1, dp(1) / 2), night ? 0x29FFFFFF : 0x1F1E1D1B);
    setBackground(bg);
    final int ink = night ? 0xFFE8ECF4 : 0xFF1E1D1B;
    mTitle.setTextColor(ink);
    mBig.setTextColor(ink);
    mSub.setTextColor(night ? 0xFFC9D2E0 : 0xFF3B3A36);
    mPos.setTextColor(night ? 0xFFC9D2E0 : 0xFF3B3A36);
    mRem.setTextColor(night ? 0xFFC9D2E0 : 0xFF3B3A36);
    mBigSub.setTextColor(muted());
    mLyric.setTextColor(night ? 0xFFA9C1F2 : 0xFF1F2A44);
    mLyricNext.setTextColor(muted());
    mBar.setBackground(round(night ? 0x33FFFFFF : 0xFFDAD7CF, 3));
    mBarFill.setBackground(round(night ? 0xFFA9C1F2 : 0xFF3E5C8A, 3));
    final float r = dp(12);
    mArt.setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline(View view, Outline outline)
      {
        final boolean round = EuriseNowPlaying.call() != null;
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), round ? view.getWidth() / 2f : r);
      }
    });
    mArt.invalidateOutline();
  }

  private int muted()
  {
    return mNight.isNight() ? 0xFFA9B4C4 : 0xFF56564F;
  }

  @NonNull
  private GradientDrawable round(int color, int radiusDp)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radiusDp));
    return d;
  }

  @NonNull
  private static String initials(@NonNull String name)
  {
    final StringBuilder b = new StringBuilder();
    for (String part : name.trim().split("\\s+"))
    {
      if (!part.isEmpty() && Character.isLetter(part.codePointAt(0)))
        b.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
      if (b.length() >= 2)
        break;
    }
    return b.length() == 0 ? "#" : b.toString();
  }

  private static void set(@NonNull TextView t, @Nullable String s)
  {
    if (s == null || s.isEmpty())
    {
      t.setVisibility(GONE);
      return;
    }
    if (!s.contentEquals(t.getText()))
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
