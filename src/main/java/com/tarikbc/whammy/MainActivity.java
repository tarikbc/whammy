package com.tarikbc.whammy;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  /** Infinite-scroll trigger: start loading the next page once the last
   *  visible row is within this many rows of the end (DESIGN.md
   *  task-pagination-filters). */
  private static final int SCROLL_LOAD_MORE_THRESHOLD = 5;
  /** Bound on consecutive auto-fetched pages that come back with zero
   *  results AFTER the client-side video filter — protects against a
   *  video-poor query spinning through many pages unattended before
   *  either finding a match or hitting a real end-of-results. The user
   *  can always keep scrolling manually past this to try further pages. */
  private static final int MAX_AUTO_CHAIN_PAGES = 5;

  private RecyclerView rv;
  private View emptyState;
  private TextView emptyHeadline;
  private TextView emptySub;
  private ArtLoader artLoader;

  // --- Pagination + filter state (DESIGN.md §7.11 / infinite scroll) ---
  private ResultsAdapter adapter;
  private String currentQuery;
  private String currentInstrument; // null = no instrument filter (server-side)
  private boolean videoOnly;        // client-side filter
  private int currentPage;
  private boolean loadingMore;
  private boolean reachedEnd;
  /** Every raw chart fetched for the current query+instrument, BEFORE the
   *  client-side video filter — kept so toggling "Video" can recompute the
   *  visible list without re-hitting the network. */
  private final List<Chart> loadedRaw = new ArrayList<>();

  private HorizontalScrollView filterRail;
  private final Map<String, TextView> instrumentChips = new LinkedHashMap<>();
  private TextView videoChip;

  @Override protected void onCreate(Bundle s) {
    super.onCreate(s);
    setContentView(R.layout.activity_main);

    setUpLibraryButton();

    // RecyclerView is instantiated in *code*, not declared as a
    // <...RecyclerView> tag in any XML layout — this app never inflates
    // one from XML and never overrides the `recyclerViewStyle` theme
    // attribute. build.sh vendors the res/ + AndroidManifest.xml of the
    // AAR jars whose classes reference their own R (recyclerview,
    // androidx.core, customview-poolingcontainer, lifecycle-runtime) and
    // links them in via aapt2's --extra-packages, so real R classes
    // exist for all of them at runtime — see build.sh for the full
    // mechanism.
    FrameLayout container = findViewById(R.id.results_container);
    emptyState = findViewById(R.id.empty_state);
    emptyHeadline = findViewById(R.id.empty_headline);
    emptySub = findViewById(R.id.empty_sub);

    artLoader = new ArtLoader(this);

    rv = new RecyclerView(this);
    rv.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    rv.setLayoutManager(new LinearLayoutManager(this));
    rv.setClipToPadding(false);
    rv.setVisibility(View.GONE);

    int padH = dp(20), padTop = dp(16), padBottom = dp(20);
    rv.setPadding(padH, padTop, padH, padBottom);

    // Row gap 12dp (DESIGN.md §6) between rows, none after the last.
    final int gap = dp(12);
    rv.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos != state.getItemCount() - 1) outRect.bottom = gap;
      }
    });

    container.addView(rv);

    // Infinite-scroll pagination: once the last visible row is within
    // SCROLL_LOAD_MORE_THRESHOLD of the end of the CURRENTLY bound
    // adapter, and we're not already loading and haven't hit the end,
    // fetch the next page and append it. `adapter` is read fresh off the
    // field each time (not captured), so this one listener stays correct
    // across every adapter swap a new search/filter change makes.
    rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (dy <= 0 || adapter == null || loadingMore || reachedEnd) return;
        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (lm == null) return;
        int lastVisible = lm.findLastVisibleItemPosition();
        if (lastVisible >= adapter.getItemCount() - SCROLL_LOAD_MORE_THRESHOLD) {
          fetchPage(currentPage + 1, 0);
        }
      }
    });

    setUpSearchField();
    setUpFilterRail();

    // First-launch state (DESIGN.md §7.5): shown until the first search
    // resolves. activity_main.xml's empty_state already carries this
    // copy as its default text. Runs after setUpFilterRail() since it
    // hides filterRail, which must already be found-by-id by then.
    showFirstLaunchState();
  }

  /** Wires the search field: clear button, and running a live
   *  {@link EncoreApi#search} on IME "search"/done (or a hardware/adb
   *  Enter keypress, which doesn't always surface as an editor action). */
  private void setUpSearchField() {
    EditText input = findViewById(R.id.search_input);
    ImageView clear = findViewById(R.id.search_clear);

    input.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
        clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
      }
      @Override public void afterTextChanged(Editable s) {}
    });

    input.setOnEditorActionListener((v, actionId, event) -> {
      boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
          || actionId == EditorInfo.IME_ACTION_DONE
          || actionId == EditorInfo.IME_NULL; // fired for a raw hardware/adb Enter keypress
      boolean isEnterKeyDown = event != null
          && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
          && event.getAction() == KeyEvent.ACTION_DOWN;
      if (isSearchAction || isEnterKeyDown) {
        runSearch(v.getText().toString());
        return true;
      }
      return false;
    });

    clear.setOnClickListener(v -> {
      input.setText("");
      currentQuery = null; // filter-chip taps become a no-op again until the next search
      showFirstLaunchState();
    });
  }

  /** Wires the filter chip rail (DESIGN.md §7.11): instrument chips are
   *  single-select and server-side (re-run the current query at page 1
   *  with the newly selected instrument, or null on deselect); the Video
   *  chip is a client-side toggle that re-filters already-fetched pages
   *  without a new network call. No-op taps while there's no active
   *  search context — the rail is hidden then anyway. */
  private void setUpFilterRail() {
    filterRail = findViewById(R.id.filter_rail);

    instrumentChips.put("guitar", (TextView) findViewById(R.id.chip_instrument_guitar));
    instrumentChips.put("bass", (TextView) findViewById(R.id.chip_instrument_bass));
    instrumentChips.put("drums", (TextView) findViewById(R.id.chip_instrument_drums));
    instrumentChips.put("keys", (TextView) findViewById(R.id.chip_instrument_keys));
    instrumentChips.put("vocals", (TextView) findViewById(R.id.chip_instrument_vocals));
    videoChip = findViewById(R.id.chip_video);

    for (Map.Entry<String, TextView> entry : instrumentChips.entrySet()) {
      String instrument = entry.getKey();
      TextView chip = entry.getValue();
      chip.setOnClickListener(v -> {
        if (currentQuery == null) return;
        String newInstrument = instrument.equals(currentInstrument) ? null : instrument;
        animateChipTap(chip);
        startNewSearch(currentQuery, newInstrument);
      });
    }

    videoChip.setOnClickListener(v -> {
      if (currentQuery == null) return;
      videoOnly = !videoOnly;
      animateChipTap(videoChip);
      updateFilterChipStyles();
      applyVideoFilterAndRefresh();
    });
  }

  /** Reflects {@link #currentInstrument}/{@link #videoOnly} onto the chip
   *  backgrounds/label colors (DESIGN.md §7.11: selected = star 1.5dp
   *  stroke + text_hi label; unselected = text label). */
  private void updateFilterChipStyles() {
    for (Map.Entry<String, TextView> entry : instrumentChips.entrySet()) {
      setChipSelected(entry.getValue(), entry.getKey().equals(currentInstrument));
    }
    setChipSelected(videoChip, videoOnly);
  }

  private void setChipSelected(TextView chip, boolean selected) {
    chip.setBackgroundResource(selected ? R.drawable.bg_chip_filter_selected : R.drawable.bg_chip_filter);
    chip.setTextColor(getColor(selected ? R.color.text_hi : R.color.text));
  }

  /** DESIGN.md §8.1: dur_2 (160ms) ease_standard — a quick, cheap
   *  selection pulse rather than a full crossfade/transition-drawable
   *  (background res + text color already swap instantly in
   *  updateFilterChipStyles(); this just gives the tap a bit of life). */
  private void animateChipTap(View chip) {
    ValueAnimator anim = ValueAnimator.ofFloat(0.92f, 1f);
    anim.setDuration(160);
    anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
    anim.addUpdateListener(a -> {
      float v = (float) a.getAnimatedValue();
      chip.setScaleX(v);
      chip.setScaleY(v);
    });
    anim.start();
  }

  /** Starts a brand-new search: resets pagination (page/reachedEnd/raw
   *  cache) and replaces the adapter — the "new adapter per search" path
   *  the append() path (subsequent pages of the SAME search) is kept
   *  separate from. A blank (post-trim) query is a no-op. */
  private void runSearch(String query) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.isEmpty()) return;
    hideKeyboard();
    startNewSearch(trimmed, currentInstrument);
  }

  private void startNewSearch(String query, String instrument) {
    currentQuery = query;
    currentInstrument = instrument;
    currentPage = 0;
    reachedEnd = false;
    loadingMore = false;
    loadedRaw.clear();
    adapter = null;

    filterRail.setVisibility(View.VISIBLE);
    updateFilterChipStyles();
    showSearchingState();
    fetchPage(1, 0);
  }

  /** Recomputes the visible list from {@link #loadedRaw} for a Video
   *  toggle flip — no network call, since every raw page fetched so far
   *  is already cached. If nothing currently loaded matches, chain-fetches
   *  further pages (bounded by {@link #MAX_AUTO_CHAIN_PAGES}) looking for
   *  a match before falling back to the empty state. */
  private void applyVideoFilterAndRefresh() {
    List<Chart> filtered = applyVideoFilter(loadedRaw);
    if (!filtered.isEmpty()) {
      loadResults(filtered);
      return;
    }
    if (reachedEnd) {
      showEmptyState("No charts found", "Try a different song or artist name.");
      return;
    }
    adapter = null;
    showSearchingState();
    fetchPage(currentPage + 1, 0);
  }

  private List<Chart> applyVideoFilter(List<Chart> raw) {
    if (!videoOnly) return new ArrayList<>(raw);
    List<Chart> out = new ArrayList<>();
    for (Chart c : raw) if (c.hasVideoBackground) out.add(c);
    return out;
  }

  /** Fetches one page of {@link #currentQuery}/{@link #currentInstrument}
   *  on the background executor. {@code chainDepth} bounds automatic
   *  re-fetching when a page comes back empty AFTER the client-side video
   *  filter (see {@link #onPageLoaded}) — it's 0 for any user-triggered
   *  fetch (scroll, new search, filter toggle) and only increments across
   *  the auto-chain itself. */
  private void fetchPage(int page, int chainDepth) {
    loadingMore = true;
    final String query = currentQuery;
    final String instrument = currentInstrument;
    executor.execute(() -> {
      try {
        List<Chart> raw = EncoreApi.search(query, page, instrument);
        mainHandler.post(() -> onPageLoaded(page, raw, chainDepth));
      } catch (IOException e) {
        mainHandler.post(() -> onPageLoadFailure(page));
      } catch (Exception e) {
        // Defensive: a malformed response, etc. — same user-facing outcome as IOException.
        mainHandler.post(() -> onPageLoadFailure(page));
      }
    });
  }

  private void onPageLoadFailure(int page) {
    loadingMore = false;
    if (page == 1 && loadedRaw.isEmpty()) {
      showFirstLaunchState();
      Toast.makeText(this, "Search failed — check your connection", Toast.LENGTH_SHORT).show();
    } else {
      // A load-more page failed: leave whatever's already shown in place
      // (loadingMore is already reset above) and let the user retry by
      // scrolling again, rather than blowing away good results.
      Toast.makeText(this, "Couldn't load more — check your connection", Toast.LENGTH_SHORT).show();
    }
  }

  /** Common landing point for every page fetch (a brand-new search's page
   *  1, an infinite-scroll page N, or a Video-toggle backfill page). An
   *  EMPTY page is the sole end-of-results signal (DESIGN.md
   *  task-pagination-filters: the API's total count is unreliable, but an
   *  empty page reliably means "no more"). Non-empty raw pages are cached
   *  into {@link #loadedRaw}, then run through the client-side video
   *  filter before being shown/appended. */
  private void onPageLoaded(int page, List<Chart> raw, int chainDepth) {
    currentPage = page;

    if (raw.isEmpty()) {
      reachedEnd = true;
      loadingMore = false;
      // adapter == null means nothing is on screen yet — either a brand
      // new search came back with nothing at all, or (Video toggle /
      // auto-chain case) we ran out of pages without ever finding a page
      // that survives the client-side video filter.
      if (adapter == null) {
        showEmptyState("No charts found", "Try a different song or artist name.");
      }
      return;
    }

    loadedRaw.addAll(raw);
    List<Chart> filtered = applyVideoFilter(raw);

    if (filtered.isEmpty()) {
      loadingMore = false;
      if (chainDepth < MAX_AUTO_CHAIN_PAGES) {
        fetchPage(page + 1, chainDepth + 1);
      } else if (adapter == null) {
        showEmptyState("No charts found", "Try a different song or artist name.");
      }
      return;
    }

    if (adapter == null) {
      loadResults(filtered);
    } else {
      adapter.append(filtered);
    }
    loadingMore = false;
  }

  /** Builds a fresh adapter for {@code results} — a clean set of IDLE
   *  states for every row, no stale bleed-over from a previous search's
   *  adapter — wires it up, and shows the list. Used for the first page
   *  of a new search AND for a Video-toggle recompute (both replace what
   *  the user sees wholesale); subsequent pages of the SAME search go
   *  through {@link ResultsAdapter#append} instead, on this same instance. */
  private void loadResults(List<Chart> results) {
    ResultsAdapter newAdapter = new ResultsAdapter(results, artLoader);
    // Whole-row tap opens the detail screen (DESIGN.md §7.13), passing
    // the already-fetched Chart via Serializable extra — no extra
    // network call needed on the detail screen.
    newAdapter.onRowClick = (pos, c) ->
        startActivity(new Intent(this, SongDetailActivity.class).putExtra("chart", c));
    newAdapter.onDownload = (pos, c) -> startDownload(pos, c, newAdapter);
    adapter = newAdapter;
    rv.setAdapter(newAdapter);
    showResults();
  }

  /** Download flow shared by every row's note button (DESIGN.md §7.4/§7.8):
   *  permission gate first (a missing grant must never silently no-op the
   *  tap), then background download -> place into the Songs folder ->
   *  DONE + a scan-hint toast (§7.9), or ERROR with the temp file cleaned up. */
  private void startDownload(int pos, Chart chart, ResultsAdapter adapter) {
    if (!Permissions.hasAllFiles()) {
      Toast.makeText(this,
          "Whammy needs storage access to save charts — opening settings…",
          Toast.LENGTH_LONG).show();
      Permissions.requestAllFiles(this);
      return;
    }

    adapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, -1);
    DownloadHelper.start(this, chart, new DownloadHelper.Callback() {
      @Override public void onProgress(int percent) {
        adapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, percent);
      }
      @Override public void onDone(File placed) {
        adapter.setState(pos, ResultsAdapter.DownloadState.DONE, 100);
        Toast.makeText(MainActivity.this,
            "Added: " + chart.name + " · Scan your library in Clone Hero",
            Toast.LENGTH_LONG).show();
      }
      @Override public void onError(Exception e) {
        adapter.setState(pos, ResultsAdapter.DownloadState.ERROR, 0);
      }
    });
  }

  /** Wires the app-bar library glyph (DESIGN.md §7.1b) to open the
   *  Library screen (DESIGN.md §7.12). */
  private void setUpLibraryButton() {
    ImageView library = findViewById(R.id.library_button);
    library.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
  }

  private void showFirstLaunchState() {
    filterRail.setVisibility(View.GONE);
    showEmptyState("Find your setlist",
        "Search the Encore database and drop songs straight into Clone Hero.");
  }

  /** Lightweight in-progress affordance while a search is in flight. */
  private void showSearchingState() {
    rv.setVisibility(View.GONE);
    emptyHeadline.setText("Searching…");
    emptySub.setText("");
    emptyState.setVisibility(View.VISIBLE);
  }

  private void showEmptyState(String headline, String sub) {
    rv.setVisibility(View.GONE);
    emptyHeadline.setText(headline);
    emptySub.setText(sub);
    emptyState.setVisibility(View.VISIBLE);
  }

  private void showResults() {
    emptyState.setVisibility(View.GONE);
    rv.setVisibility(View.VISIBLE);
  }

  private void hideKeyboard() {
    View focus = getCurrentFocus();
    if (focus == null) return;
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}
