package com.tarikbc.whammy;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Song detail screen (DESIGN.md §7.13): opened by tapping a search
 * result row (the {@code ResultsAdapter.OnRowClick} seam wired in
 * MainActivity). Renders everything the search result already carries
 * — no extra network call — via the {@link Chart} passed in the
 * launching Intent.
 */
public class SongDetailActivity extends Activity {

  /** Mirrors {@link ResultsAdapter.DownloadState} but tracked locally —
   *  this screen has no adapter position to key off of, just the one
   *  pinned Download button. */
  private enum DlState { IDLE, DOWNLOADING, DONE, ERROR }

  private Chart chart;
  private DlState dlState = DlState.IDLE;
  private int dlPercent = -1;
  /** Human-readable size ("227 MB"), filled in once EncoreApi.contentLength
   *  resolves; null while pending or unknown (§10: "omit the size"). */
  private String sizeLabel;

  /** Background executor for the size (HEAD) and existing-library-keys
   *  fetches this screen kicks off -- a single shared field (task B1
   *  robustness) rather than ad-hoc {@code new Thread(...)} calls, so
   *  {@link #onDestroy} has something concrete to shut down. */
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private ArtLoader artLoader;

  /** Normalized "already in your library" keys (task B1 robustness,
   *  mirroring MainActivity#downloadedKeys), from {@link
   *  SongStore#existingChartKeys()} -- computed once in {@link #onCreate}
   *  and consulted by {@link #bindDownloadButton}'s tap handler to decide
   *  whether a duplicate-download confirm is needed. */
  private Set<String> existingKeys = Collections.emptySet();

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_song_detail);

    chart = (Chart) getIntent().getSerializableExtra("chart");
    if (chart == null) {
      // Defensive: this screen has nothing to show without a chart.
      finish();
      return;
    }

    artLoader = new ArtLoader(this);

    findViewById(R.id.back_button).setOnClickListener(v -> finish());

    bindHero(chart);
    bindCharter(chart);
    bindInstruments(chart);
    bindBadges(chart);
    bindDescription(chart);
    bindDownloadButton();
    refreshExistingKeys();
  }

  /** Recomputes {@link #existingKeys} from disk on the background executor
   *  (mirrors MainActivity#refreshDownloadedKeys) -- run once up front in
   *  {@link #onCreate} so the very first download tap already has an
   *  answer, with no per-tap thread hop. */
  private void refreshExistingKeys() {
    executor.execute(() -> {
      Set<String> keys = SongStore.existingChartKeys();
      runOnUiThreadSafe(() -> {
        existingKeys = keys;
        // The keys arrive async — refresh the button so an already-owned
        // chart shows "In your library" instead of a misleading "Download".
        updateDownloadButtonLabel(findViewById(R.id.download_button));
      });
    });
  }

  /** Posts {@code r} to the main thread, but only runs it once there if
   *  this activity is still alive -- guards every background callback
   *  that touches views (size fetch, existing-keys fetch) against
   *  finishing after the user has already left this screen. */
  private void runOnUiThreadSafe(Runnable r) {
    mainHandler.post(() -> {
      if (isFinishing() || isDestroyed()) return;
      r.run();
    });
  }

  /** Robustness (task B1): shuts down this screen's background executor
   *  and its {@link ArtLoader} so a slow size/art/existing-keys request
   *  in flight when the user leaves can't post back to (or crash
   *  touching) views that no longer exist. */
  @Override protected void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
    if (artLoader != null) artLoader.shutdown();
  }

  /** Hero header: 112dp cover (ArtLoader, r_md clip), title, artist,
   *  "album · year · genre" sub line, SETLIST marker + duration chip
   *  (DESIGN.md §7.13; setlist-awareness task adds the marker). */
  private void bindHero(Chart chart) {
    ImageView heroArt = findViewById(R.id.hero_art);
    final float artRadius = getResources().getDimension(R.dimen.r_md);
    heroArt.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artRadius);
      }
    });
    heroArt.setClipToOutline(true);
    artLoader.load(heroArt, chart.albumArtMd5);

    ((TextView) findViewById(R.id.song_title)).setText(chart.name);
    ((TextView) findViewById(R.id.song_artist)).setText(chart.artist);

    TextView sub = findViewById(R.id.song_sub);
    String subText = joinNonEmpty(" · ", chart.album, chart.year, chart.genre);
    if (subText.isEmpty()) {
      sub.setVisibility(View.GONE);
    } else {
      sub.setText(subText);
      sub.setVisibility(View.VISIBLE);
    }

    findViewById(R.id.setlist_chip).setVisibility(chart.isSetlist() ? View.VISIBLE : View.GONE);

    View durationChip = findViewById(R.id.duration_chip);
    String duration = Chart.formatDuration(chart.songLengthMs);
    if (duration.isEmpty()) {
      durationChip.setVisibility(View.GONE);
    } else {
      ((TextView) findViewById(R.id.duration_text)).setText(duration);
      durationChip.setVisibility(View.VISIBLE);
    }
  }

  /** "CHARTED BY XXX" — the §7.3 charter look (Text.Charter is already
   *  all-caps, so charted_by_fmt's mixed-case "Charted by %s" renders
   *  upper regardless). */
  private void bindCharter(Chart chart) {
    TextView charter = findViewById(R.id.charter);
    if (chart.charter == null || chart.charter.isEmpty()) {
      charter.setVisibility(View.GONE);
      return;
    }
    charter.setText(getString(R.string.charted_by_fmt, chart.charter));
    charter.setVisibility(View.VISIBLE);
  }

  /** Instruments & difficulty (DESIGN.md §7.13): one row per
   *  chart.difficulties entry — name, 6-segment meter (fill =
   *  min(intensity, 6)), raw number — or "No difficulty data" when the
   *  map is empty. */
  private void bindInstruments(Chart chart) {
    LinearLayout container = findViewById(R.id.instruments_container);
    container.removeAllViews();

    Map<String, Integer> difficulties = chart.difficulties;
    if (difficulties == null || difficulties.isEmpty()) {
      TextView empty = new TextView(this);
      empty.setText("No difficulty data");
      // TODO promote to strings.xml
      empty.setTextColor(getColor(R.color.text_lo));
      empty.setTextSize(14f);
      container.addView(empty);
      return;
    }

    LayoutInflater inflater = LayoutInflater.from(this);
    for (Map.Entry<String, Integer> entry : difficulties.entrySet()) {
      View row = inflater.inflate(R.layout.view_intensity_row, container, false);
      TextView name = row.findViewById(R.id.instrument_name);
      IntensityMeterView meter = row.findViewById(R.id.intensity_meter);
      TextView number = row.findViewById(R.id.intensity_number);

      int intensity = entry.getValue() == null ? 0 : entry.getValue();
      name.setText(entry.getKey());
      meter.setFilledSegments(Math.min(intensity, 6));
      number.setText(String.valueOf(intensity));

      container.addView(row);
    }
  }

  /** Feature badges (DESIGN.md §7.13 fuller set, §7.10 chip style): only
   *  the TRUE ones, wrapped via FlowLayout. */
  private void bindBadges(Chart chart) {
    FlowLayout badges = findViewById(R.id.badges_container);
    badges.removeAllViews();

    if (chart.hasVideoBackground) {
      badges.addView(iconChip(R.drawable.bg_chip_video, R.drawable.ic_video, R.color.star, "VIDEO"));
    }
    if (chart.proDrums) {
      badges.addView(labelChip("PRO DRUMS"));
    }
    if (chart.modchart) {
      badges.addView(labelChip("MOD"));
    }
    if (chart.hasLyrics) {
      badges.addView(iconChip(R.drawable.bg_chip, R.drawable.ic_lyrics, R.color.text, "LYRICS"));
    }
    if (chart.hasVocals) {
      badges.addView(labelChip("VOCALS"));
    }
    if (chart.hasSoloSections) {
      badges.addView(iconChip(R.drawable.bg_chip, R.drawable.ic_solo, R.color.text, "SOLOS"));
    }
    // TODO promote badge labels ("VIDEO"/"PRO DRUMS"/"MOD"/"LYRICS"/"VOCALS"/"SOLOS") to strings.xml
  }

  /** About / description blurb: {@code chart.loadingPhrase} verbatim
   *  (the charter-written in-game loading quip) under a quiet "About"
   *  label, quoted so it reads as a description rather than app copy.
   *  Applies to any chart with a phrase, not only setlists — but it's
   *  the closest thing to a track description the API provides, and
   *  the key payoff for setlists (e.g. "All ten main Children of Bodom
   *  studio albums... in one chart."). Whole section hidden when the
   *  phrase is null/empty. */
  private void bindDescription(Chart chart) {
    View container = findViewById(R.id.about_container);
    String phrase = chart.loadingPhrase;
    if (phrase == null || phrase.trim().isEmpty()) {
      container.setVisibility(View.GONE);
      return;
    }
    ((TextView) findViewById(R.id.about_text)).setText("“" + phrase.trim() + "”");
    container.setVisibility(View.VISIBLE);
  }

  /** Wires the full-width Download button (DESIGN.md §7.6/§7.13): the
   *  same permission-gate → background-download → place-in-Songs-folder
   *  flow the search rows use ({@link DownloadHelper}), reflected in the
   *  button's own label/state since this screen has no adapter row to
   *  drive a progress ring on. Also kicks off the "how heavy" size fetch
   *  (user-requested §10 feature) so the button's idle label reads
   *  "Download · 227 MB" once the HEAD request resolves. */
  private void bindDownloadButton() {
    TextView downloadButton = findViewById(R.id.download_button);
    updateDownloadButtonLabel(downloadButton);

    downloadButton.setOnClickListener(v -> {
      if (dlState == DlState.DOWNLOADING) return; // ignore taps mid-flight

      if (!Permissions.hasAllFiles()) {
        startActivity(new Intent(this, PermissionActivity.class));
        return;
      }

      // Duplicate handling (task B1 robustness, mirrors MainActivity):
      // never gate a genuine ERROR-state retry behind this confirm --
      // that download never actually made it into the library.
      boolean alreadyInLibrary = dlState != DlState.ERROR
          && existingKeys.contains(SongStore.keyFor(chart));
      if (alreadyInLibrary) {
        Snackbar.show(this, getString(R.string.already_in_library_fmt, chart.name),
            getString(R.string.download_again_action), () -> beginDownload(downloadButton));
        return;
      }

      beginDownload(downloadButton);
    });

    fetchSize(downloadButton);
  }

  private void beginDownload(TextView downloadButton) {
    dlState = DlState.DOWNLOADING;
    dlPercent = -1;
    updateDownloadButtonLabel(downloadButton);

    DownloadHelper.start(this, chart, new DownloadHelper.Callback() {
      @Override public void onProgress(int percent) {
        dlPercent = percent;
        updateDownloadButtonLabel(downloadButton);
      }
      @Override public void onDone(java.io.File placed) {
        dlState = DlState.DONE;
        updateDownloadButtonLabel(downloadButton);
        Snackbar.show(SongDetailActivity.this,
            getString(R.string.download_done_fmt, chart.name) + " · " + getString(R.string.scan_hint));
      }
      @Override public void onError(Exception e) {
        dlState = DlState.ERROR;
        updateDownloadButtonLabel(downloadButton);
      }
    });
  }

  /** HEAD-requests the .sng's byte size on the background executor;
   *  -1/failure is handled by simply leaving {@link #sizeLabel} null (no
   *  size shown). */
  private void fetchSize(TextView downloadButton) {
    executor.execute(() -> {
      long bytes = EncoreApi.contentLength(chart.md5);
      runOnUiThreadSafe(() -> {
        if (bytes > 0) sizeLabel = LibraryAdapter.formatBytes(bytes);
        updateDownloadButtonLabel(downloadButton);
      });
    });
  }

  /** Renders the button label for the current {@link #dlState}, folding
   *  in {@link #sizeLabel} on the idle label once/if it's known. */
  private void updateDownloadButtonLabel(TextView downloadButton) {
    switch (dlState) {
      case DOWNLOADING:
        downloadButton.setText(dlPercent >= 0 ? "Downloading… " + dlPercent + "%" : "Downloading…");
        break;
      case DONE:
        downloadButton.setText("Added ✓");
        break;
      case ERROR:
        downloadButton.setText("Retry download");
        break;
      case IDLE:
      default:
        if (existingKeys.contains(SongStore.keyFor(chart))) {
          // Already downloaded (consistent with the search row's "IN LIBRARY"
          // chip). Still tappable — the tap handler offers a re-download confirm.
          downloadButton.setText("✓ In your library");
        } else {
          downloadButton.setText(sizeLabel != null ? "Download · " + sizeLabel : "Download");
        }
    }
    // TODO promote "Download"/"Downloading…"/"Added ✓"/"Retry download"/"In your library" to strings.xml
  }

  /** Builds one label-only chip (PRO DRUMS / MOD / VOCALS): DESIGN.md
   *  §7.10 style — bg_chip shell, `text` +6% UPPER label
   *  (Text.Charter already applies both). */
  private LinearLayout labelChip(String label) {
    LinearLayout chip = newChip(R.drawable.bg_chip);
    TextView tv = new TextView(this, null, 0, R.style.Text_Charter);
    tv.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    tv.setTextColor(getColor(R.color.text)); // Text.Charter defaults to text_lo — chips want `text`
    tv.setText(label);
    chip.addView(tv);
    return chip;
  }

  /** Builds one icon+label chip (VIDEO / LYRICS / SOLOS). */
  private LinearLayout iconChip(int backgroundRes, int iconRes, int iconTintColorRes, String label) {
    LinearLayout chip = newChip(backgroundRes);

    ImageView icon = new ImageView(this);
    int iconSizePx = dp(15);
    icon.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
    icon.setImageResource(iconRes);
    icon.setImageTintList(ColorStateList.valueOf(getColor(iconTintColorRes)));
    chip.addView(icon);

    TextView tv = new TextView(this, null, 0, R.style.Text_Charter);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = dp(3);
    tv.setLayoutParams(lp);
    tv.setTextColor(getColor(R.color.text));
    tv.setText(label);
    chip.addView(tv);

    return chip;
  }

  /** Shared chip shell (DESIGN.md §7.10): {@code backgroundRes} fill
   *  (bg_chip or the star-hairlined bg_chip_video), ~23dp tall, 8dp
   *  horizontal padding. Inter-chip spacing (~6dp, matching §7.10) is
   *  owned entirely by the parent FlowLayout's fixed gaps, not by any
   *  margin here. */
  private LinearLayout newChip(int backgroundRes) {
    LinearLayout chip = new LinearLayout(this);
    chip.setOrientation(LinearLayout.HORIZONTAL);
    chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
    chip.setBackgroundResource(backgroundRes);
    int hPad = dp(8);
    chip.setPadding(hPad, 0, hPad, 0);
    chip.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, dp(23)));
    return chip;
  }

  private static String joinNonEmpty(String sep, String... parts) {
    List<String> nonEmpty = new ArrayList<>();
    for (String p : parts) {
      if (p != null && !p.isEmpty()) nonEmpty.add(p);
    }
    return String.join(sep, nonEmpty);
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}
