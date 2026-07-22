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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

  /**
   * Sort options offered on the Sort chip (task-search-screen-features):
   * {@code type}/{@code direction} feed {@link EncoreApi.SearchParams}
   * directly (null/null = relevance, the API's default when {@code sort}
   * is omitted); {@code chipLabel} is the short form shown on the chip
   * itself once selected ("Sort: Name"), {@code menuLabel} the fuller
   * text in the popup menu.
   */
  private enum SortOption {
    RELEVANCE(null, null, "Sort", "Relevance"),
    NAME("name", "asc", "Name", "Name (A–Z)"),
    ARTIST("artist", "asc", "Artist", "Artist (A–Z)"),
    LONGEST("length", "desc", "Longest", "Longest first"),
    SHORTEST("length", "asc", "Shortest", "Shortest first");

    final String type, direction, chipLabel, menuLabel;
    SortOption(String type, String direction, String chipLabel, String menuLabel) {
      this.type = type; this.direction = direction; this.chipLabel = chipLabel; this.menuLabel = menuLabel;
    }
  }

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
  private String currentDifficulty; // null = no difficulty filter (server-side); else "easy"/"medium"/"hard"/"expert"
  private SortOption currentSort = SortOption.RELEVANCE; // server-side
  private boolean videoOnly;        // client-side filter
  private int currentPage;
  private boolean loadingMore;
  private boolean reachedEnd;
  // Bumped on every brand-new search so an in-flight page from a previous
  // query can't land and pollute the current results (stale-search race).
  private int searchGeneration;
  /** Every raw chart fetched for the current query+instrument, BEFORE the
   *  client-side video filter — kept so toggling "Video" can recompute the
   *  visible list without re-hitting the network. */
  private final List<Chart> loadedRaw = new ArrayList<>();

  /** Normalized "already downloaded" keys (task-search-screen-features),
   *  from {@link SongStore#existingChartKeys()} — recomputed on the
   *  background executor after a page loads, after a successful
   *  download, and on {@link #onResume}, then pushed into {@link
   *  #adapter}. Cached here (rather than only living on the adapter) so
   *  a freshly-created adapter (a brand-new search) can be seeded with
   *  the latest known set immediately instead of starting blank. */
  private Set<String> downloadedKeys = Collections.emptySet();

  private HorizontalScrollView filterRail;
  private final Map<String, TextView> instrumentChips = new LinkedHashMap<>();
  private final Map<String, TextView> difficultyChips = new LinkedHashMap<>();
  private TextView videoChip;
  private TextView sortChip;

  // --- Multi-select batch download (task B2) ---
  private View selectionBar;
  private TextView selectionCount;
  private TextView selectionSelectAll;
  private TextView selectionDownload;
  /** The ordered list of positions the current batch is working through
   *  (null when no batch is running -- also doubles as the "is a batch in
   *  flight" flag). */
  private List<Integer> batchQueue;
  private int batchIndex;
  private int batchOk;
  private int batchFail;
  /** The adapter the running batch belongs to. Every batch callback
   *  re-checks {@code adapter == batchAdapter} before touching UI so a
   *  batch started against an adapter that's since been replaced (a brand
   *  new search) can't reach into the new adapter's rows. */
  private ResultsAdapter batchAdapter;
  /** Set by the Cancel/back-button exit path; checked before enqueueing
   *  each further item so a queue stops advancing once the user leaves
   *  selection mode mid-run (task B2 lifecycle: the in-flight item is
   *  simply let finish -- its own onDone/onError still updates that row's
   *  state -- but nothing further starts and no completion Snackbar
   *  fires for an aborted run). */
  private boolean batchCancelled;

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
    setUpSelectionBar();

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
    difficultyChips.put("easy", (TextView) findViewById(R.id.chip_difficulty_easy));
    difficultyChips.put("medium", (TextView) findViewById(R.id.chip_difficulty_medium));
    difficultyChips.put("hard", (TextView) findViewById(R.id.chip_difficulty_hard));
    difficultyChips.put("expert", (TextView) findViewById(R.id.chip_difficulty_expert));
    videoChip = findViewById(R.id.chip_video);
    sortChip = findViewById(R.id.chip_sort);

    for (Map.Entry<String, TextView> entry : instrumentChips.entrySet()) {
      String instrument = entry.getKey();
      TextView chip = entry.getValue();
      chip.setOnClickListener(v -> {
        if (currentQuery == null) return;
        currentInstrument = instrument.equals(currentInstrument) ? null : instrument;
        animateChipTap(chip);
        startNewSearch();
      });
    }

    for (Map.Entry<String, TextView> entry : difficultyChips.entrySet()) {
      String difficulty = entry.getKey();
      TextView chip = entry.getValue();
      chip.setOnClickListener(v -> {
        if (currentQuery == null) return;
        currentDifficulty = difficulty.equals(currentDifficulty) ? null : difficulty;
        animateChipTap(chip);
        startNewSearch();
      });
    }

    videoChip.setOnClickListener(v -> {
      if (currentQuery == null) return;
      videoOnly = !videoOnly;
      animateChipTap(videoChip);
      updateFilterChipStyles();
      applyVideoFilterAndRefresh();
    });

    sortChip.setOnClickListener(v -> {
      if (currentQuery == null) return;
      showSortMenu();
    });
  }

  /** Sort chip tap: a PopupMenu of every {@link SortOption} (Relevance
   *  first, so it always doubles as the "clear sort" affordance) — kept
   *  as one menu rather than five more chips so the rail stays close to
   *  DESIGN.md §7.11's "essential few" guidance. Selecting a DIFFERENT
   *  option than the current one re-runs the search from page 1
   *  (server-side sort); re-selecting the current option is a no-op. */
  private void showSortMenu() {
    PopupMenu menu = new PopupMenu(this, sortChip);
    for (SortOption opt : SortOption.values()) {
      menu.getMenu().add(0, opt.ordinal(), opt.ordinal(), opt.menuLabel);
    }
    menu.getMenu().setGroupCheckable(0, true, true);
    menu.getMenu().getItem(currentSort.ordinal()).setChecked(true);
    menu.setOnMenuItemClickListener(item -> {
      SortOption selected = SortOption.values()[item.getItemId()];
      if (selected != currentSort) {
        currentSort = selected;
        animateChipTap(sortChip);
        startNewSearch();
      }
      return true;
    });
    menu.show();
  }

  /** Reflects {@link #currentInstrument}/{@link #currentDifficulty}/
   *  {@link #currentSort}/{@link #videoOnly} onto the chip
   *  backgrounds/label colors (DESIGN.md §7.11: selected = star 1.5dp
   *  stroke + text_hi label; unselected = text label). The Sort chip's
   *  own text also reflects the current selection ("Sort" at the
   *  default Relevance, "Sort: Name" etc. once changed). */
  private void updateFilterChipStyles() {
    for (Map.Entry<String, TextView> entry : instrumentChips.entrySet()) {
      setChipSelected(entry.getValue(), entry.getKey().equals(currentInstrument));
    }
    for (Map.Entry<String, TextView> entry : difficultyChips.entrySet()) {
      setChipSelected(entry.getValue(), entry.getKey().equals(currentDifficulty));
    }
    setChipSelected(videoChip, videoOnly);

    boolean sortActive = currentSort != SortOption.RELEVANCE;
    setChipSelected(sortChip, sortActive);
    sortChip.setText(sortActive ? "Sort: " + currentSort.chipLabel : "Sort");
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

  /** Inflates the bottom batch action bar (task B2, DESIGN.md §7.8
   *  surface language) into the activity's own content FrameLayout --
   *  same host + technique {@link Snackbar} uses for its own card, so no
   *  activity_main.xml changes are needed to position it. Starts GONE;
   *  {@link #updateSelectionBar} drives visibility/content from
   *  {@link ResultsAdapter#onSelectionChanged} for whichever adapter is
   *  current. */
  private void setUpSelectionBar() {
    ViewGroup content = findViewById(android.R.id.content);
    selectionBar = LayoutInflater.from(this).inflate(R.layout.view_selection_bar, content, false);
    selectionBar.setVisibility(View.GONE);
    content.addView(selectionBar);

    ImageView cancel = selectionBar.findViewById(R.id.selection_cancel);
    selectionCount = selectionBar.findViewById(R.id.selection_count);
    selectionSelectAll = selectionBar.findViewById(R.id.selection_select_all);
    selectionDownload = selectionBar.findViewById(R.id.selection_download);

    cancel.setOnClickListener(v -> exitSelectionMode());
    selectionSelectAll.setOnClickListener(v -> {
      if (adapter != null) adapter.selectAll();
    });
    selectionDownload.setOnClickListener(v -> startBatchDownload());
  }

  /** Reflects the current adapter's selection state onto the bar --
   *  wired as every adapter's {@link ResultsAdapter#onSelectionChanged}
   *  in {@link #loadResults}. Re-enables Select all/Download on every
   *  call (a fresh selection-mode entry, or any further toggle) since the
   *  only thing that ever disables them is a batch actually running, and
   *  no further selection-changed events fire while one is (the queue
   *  drives row state via {@link ResultsAdapter#setState}, not the
   *  selection set). */
  private void updateSelectionBar(boolean selectionMode, int count) {
    if (selectionBar == null) return;
    selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
    if (!selectionMode) return;
    selectionCount.setText(getString(R.string.selection_count_fmt, count));
    selectionDownload.setText(getString(R.string.download_n_fmt, count));
    selectionSelectAll.setEnabled(true);
    selectionDownload.setEnabled(count > 0);
    selectionDownload.setAlpha(count > 0 ? 1f : 0.45f);
  }

  /** Cancel/✕ and the system back button (DESIGN.md task B2) both funnel
   *  here: aborts any in-flight batch from enqueueing further items (the
   *  one item already downloading is simply let finish -- see
   *  {@link #batchCancelled}'s doc) and clears/hides the selection UI via
   *  the current adapter. No-op if there's no current adapter. */
  private void exitSelectionMode() {
    batchCancelled = true;
    if (adapter != null) adapter.exitSelectionMode();
  }

  @Override public void onBackPressed() {
    if (adapter != null && adapter.isSelectionMode()) {
      exitSelectionMode();
      return;
    }
    super.onBackPressed();
  }

  /** `Download (N)` tap (task B2): permission-gates exactly like a single
   *  row's download (DESIGN.md §7.6 -- never silently no-op a missing
   *  grant), then drives every selected row through {@link DownloadHelper}
   *  ONE AT A TIME via {@link #runNextBatchItem} rather than firing N
   *  parallel connections. Deliberately skips the single-tap duplicate-
   *  download confirm ({@link #startDownload}) -- already-in-library
   *  selections are just re-downloaded, per spec. */
  private void startBatchDownload() {
    if (adapter == null || !adapter.isSelectionMode()) return;
    List<Integer> positions = adapter.getSelectedPositionsSorted();
    if (positions.isEmpty()) return;

    if (!Permissions.hasAllFiles()) {
      startActivity(new Intent(this, PermissionActivity.class));
      return;
    }

    batchQueue = positions;
    batchIndex = 0;
    batchOk = 0;
    batchFail = 0;
    batchAdapter = adapter;
    batchCancelled = false;

    // Prevent a second overlapping queue from a stray double-tap; Cancel
    // stays live throughout (it just flips batchCancelled).
    selectionSelectAll.setEnabled(false);
    selectionDownload.setEnabled(false);
    selectionDownload.setAlpha(0.45f);

    runNextBatchItem();
  }

  /** Sequential queue driver (task B2 "Constraints": reuse DownloadHelper
   *  per item, a small sequential driver here rather than rewriting the
   *  download internals). Each item runs to completion (onDone/onError)
   *  before the next one starts -- that chaining, not any explicit
   *  concurrency limit, is what keeps this to one connection at a time.
   *  Bails (without touching adapter rows) if the activity has been
   *  destroyed, the batch was cancelled, or {@link #adapter} no longer
   *  matches {@link #batchAdapter} (a new search replaced it out from
   *  under a running batch). */
  private void runNextBatchItem() {
    if (isFinishing() || isDestroyed() || batchCancelled || adapter != batchAdapter) {
      finishBatchQuietly();
      return;
    }
    if (batchIndex >= batchQueue.size()) {
      onBatchComplete();
      return;
    }

    final int pos = batchQueue.get(batchIndex);
    final ResultsAdapter forAdapter = batchAdapter;
    Chart chart = forAdapter.chartAt(pos);

    forAdapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, -1);
    DownloadHelper.start(this, chart, new DownloadHelper.Callback() {
      @Override public void onProgress(int percent) {
        if (adapter == forAdapter) forAdapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, percent);
      }
      @Override public void onDone(File placed) {
        if (adapter == forAdapter) forAdapter.setState(pos, ResultsAdapter.DownloadState.DONE, 100);
        if (batchCancelled) { finishBatchQuietly(); return; }
        batchOk++;
        batchIndex++;
        runNextBatchItem();
      }
      @Override public void onError(Exception e) {
        if (adapter == forAdapter) forAdapter.setState(pos, ResultsAdapter.DownloadState.ERROR, 0);
        if (batchCancelled) { finishBatchQuietly(); return; }
        batchFail++;
        batchIndex++;
        runNextBatchItem();
      }
    });
  }

  /** Every selected chart has been attempted: refreshes the "already
   *  downloaded" cross-ref, exits selection mode (hiding the batch bar),
   *  THEN shows the summary Snackbar -- that ordering is what keeps the
   *  bar and the Snackbar from ever visually overlapping (view_
   *  selection_bar.xml's elevation is also higher than the Snackbar's own
   *  as a belt-and-suspenders backstop). */
  private void onBatchComplete() {
    int ok = batchOk, fail = batchFail;
    finishBatchQuietly();
    if (adapter != null) adapter.exitSelectionMode();
    if (isFinishing() || isDestroyed()) return;
    String msg = fail == 0
        ? getString(R.string.batch_added_fmt, ok) + " · " + getString(R.string.scan_hint)
        : getString(R.string.batch_added_failed_fmt, ok, fail);
    Snackbar.show(this, msg);
  }

  /** Clears the batch-in-progress bookkeeping (used by both a normal
   *  completion and every early-abort path above) and refreshes the
   *  downloaded-keys cross-ref if a batch actually ran -- covers the
   *  cancelled-mid-run case too, where whatever finished before the
   *  cancel still belongs in the library cross-ref. */
  private void finishBatchQuietly() {
    boolean hadBatch = batchQueue != null;
    batchQueue = null;
    batchAdapter = null;
    if (hadBatch) refreshDownloadedKeys();
  }

  /** Starts a brand-new search: resets pagination (page/reachedEnd/raw
   *  cache) and replaces the adapter — the "new adapter per search" path
   *  the append() path (subsequent pages of the SAME search) is kept
   *  separate from. A blank (post-trim) query is a no-op. */
  private void runSearch(String query) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.isEmpty()) return;
    hideKeyboard();
    currentQuery = trimmed;
    startNewSearch();
  }

  /** Re-runs {@link #currentQuery} from page 1 with whatever {@link
   *  #currentInstrument}/{@link #currentDifficulty}/{@link #currentSort}
   *  are currently set to — the common path for the initial search AND
   *  every filter/sort change (each is a new search server-side, so it
   *  bumps {@link #searchGeneration} the same way). */
  private void startNewSearch() {
    searchGeneration++;   // invalidate any in-flight page from a prior search
    currentPage = 0;
    reachedEnd = false;
    loadingMore = false;
    loadedRaw.clear();
    adapter = null;

    // A brand-new search replaces the adapter wholesale (below), which
    // would otherwise leave a stale batch action bar on screen bound to
    // an adapter that's about to stop existing (task B2) -- abort any
    // running batch and hide the bar up front rather than relying on the
    // batch driver's own adapter!=batchAdapter guard to notice later.
    batchCancelled = true;
    finishBatchQuietly();
    if (selectionBar != null) selectionBar.setVisibility(View.GONE);

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
    final EncoreApi.SearchParams params = new EncoreApi.SearchParams(
        currentInstrument, currentDifficulty, currentSort.type, currentSort.direction);
    final int gen = searchGeneration;
    executor.execute(() -> {
      try {
        List<Chart> raw = EncoreApi.search(query, page, params);
        mainHandler.post(() -> onPageLoaded(gen, page, raw, chainDepth));
      } catch (IOException e) {
        mainHandler.post(() -> onPageLoadFailure(gen, page));
      } catch (Exception e) {
        // Defensive: a malformed response, etc. — same user-facing outcome as IOException.
        mainHandler.post(() -> onPageLoadFailure(gen, page));
      }
    });
  }

  private void onPageLoadFailure(int gen, int page) {
    if (isFinishing() || isDestroyed()) return; // user already left -- don't touch dead views
    if (gen != searchGeneration) return;   // a newer search superseded this one
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
  private void onPageLoaded(int gen, int page, List<Chart> raw, int chainDepth) {
    if (isFinishing() || isDestroyed()) return; // user already left -- don't touch dead views
    if (gen != searchGeneration) return;   // a newer search superseded this one
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
    // New rows (a first page or an appended page) need the "already
    // downloaded" cross-ref (task-search-screen-features) — recompute
    // from disk since a chart downloaded in another session since our
    // last check should now show as IN LIBRARY too.
    refreshDownloadedKeys();
  }

  /** Recomputes {@link #downloadedKeys} from {@link SongStore} on the
   *  background executor (disk I/O) and pushes the result into {@link
   *  #adapter} on the UI thread — called after a page loads, after a
   *  successful download, and from {@link #onResume} so deletions/
   *  downloads made elsewhere (the Library screen, Clone Hero itself)
   *  are reflected next time this screen is visible. */
  private void refreshDownloadedKeys() {
    executor.execute(() -> {
      final Set<String> keys = SongStore.existingChartKeys();
      mainHandler.post(() -> {
        if (isFinishing() || isDestroyed()) return; // user already left
        downloadedKeys = keys;
        if (adapter != null) adapter.setDownloadedKeys(keys);
      });
    });
  }

  @Override protected void onResume() {
    super.onResume();
    // Only meaningful once a search has actually run — no-op on the
    // first-launch empty state, where there's nothing to cross-reference
    // yet and filterRail/adapter aren't in a "search context" either.
    if (currentQuery != null) refreshDownloadedKeys();
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
    // Batch action bar (task B2): kept in lockstep with whichever adapter
    // is current, same pattern as onRowClick/onDownload above.
    newAdapter.onSelectionChanged = this::updateSelectionBar;
    // Seed with whatever's already known (task-search-screen-features) —
    // refreshDownloadedKeys() (called right after this in onPageLoaded)
    // will follow up with an up-to-date read, but there's no reason to
    // start every fresh adapter blank when we already have a cached set.
    newAdapter.setDownloadedKeys(downloadedKeys);
    adapter = newAdapter;
    rv.setAdapter(newAdapter);
    showResults();
  }

  /** Download flow shared by every row's note button (DESIGN.md §7.4/§7.8):
   *  permission gate first (DESIGN.md §7.6's rationale screen instead of
   *  jumping straight to settings -- a missing grant must never silently
   *  no-op the tap), then a duplicate-download confirm if the chart is
   *  already in the library, then background download -> place into the
   *  Songs folder -> DONE + a scan hint (§7.9), or ERROR with the temp
   *  file left in place for a resumed retry (see EncoreApi#downloadSng). */
  private void startDownload(int pos, Chart chart, ResultsAdapter adapter) {
    if (!Permissions.hasAllFiles()) {
      startActivity(new Intent(this, PermissionActivity.class));
      return;
    }

    // Duplicate handling (task B1 robustness): a chart already sitting in
    // the library (any row's tap, not just its own -- keyed by chart, not
    // position) gets a confirm rather than silently re-downloading over
    // it. Never gates a genuine ERROR-state retry: that row's own chart
    // never actually made it into the library the first time.
    boolean alreadyInLibrary = adapter.stateOf(pos) != ResultsAdapter.DownloadState.ERROR
        && downloadedKeys.contains(SongStore.keyFor(chart));
    if (alreadyInLibrary) {
      Snackbar.show(this, getString(R.string.already_in_library_fmt, chart.name),
          getString(R.string.download_again_action), () -> beginDownload(pos, chart, adapter));
      return;
    }

    beginDownload(pos, chart, adapter);
  }

  private void beginDownload(int pos, Chart chart, ResultsAdapter adapter) {
    adapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, -1);
    DownloadHelper.start(this, chart, new DownloadHelper.Callback() {
      @Override public void onProgress(int percent) {
        adapter.setState(pos, ResultsAdapter.DownloadState.DOWNLOADING, percent);
      }
      @Override public void onDone(File placed) {
        adapter.setState(pos, ResultsAdapter.DownloadState.DONE, 100);
        Snackbar.show(MainActivity.this,
            getString(R.string.download_done_fmt, chart.name) + " · " + getString(R.string.scan_hint));
        // The library folder just changed — refresh the "already
        // downloaded" cross-ref (task-search-screen-features) so other
        // rows for the same chart (e.g. a duplicate further down the
        // results, or after a later re-search) pick it up too.
        refreshDownloadedKeys();
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

  /** Robustness (task B1): shuts down this screen's background executor
   *  and its {@link ArtLoader}'s so a slow search/art request in flight
   *  when the user leaves can't post back to (or crash touching) views
   *  that no longer exist. {@link #onPageLoaded}/{@link
   *  #onPageLoadFailure}/{@link #refreshDownloadedKeys}'s own
   *  isFinishing()/isDestroyed() guards cover the narrow window between
   *  a callback already being posted and this running. */
  @Override protected void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
    if (artLoader != null) artLoader.shutdown();
  }
}
