package com.tarikbc.whammy;

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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private RecyclerView rv;
  private View emptyState;
  private TextView emptyHeadline;
  private TextView emptySub;
  private ArtLoader artLoader;

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

    // First-launch state (DESIGN.md §7.5): shown until the first search
    // resolves. activity_main.xml's empty_state already carries this
    // copy as its default text.
    showFirstLaunchState();

    setUpSearchField();
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
      showFirstLaunchState();
    });
  }

  /** Runs {@link EncoreApi#search} on a background thread and swaps the
   *  results in on the main thread. A blank (post-trim) query is a
   *  no-op — nothing to search for. */
  private void runSearch(String query) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.isEmpty()) return;

    hideKeyboard();
    showSearchingState();

    executor.execute(() -> {
      try {
        List<Chart> results = EncoreApi.search(trimmed, 1);
        mainHandler.post(() -> onSearchSuccess(results));
      } catch (IOException e) {
        mainHandler.post(this::onSearchFailure);
      } catch (Exception e) {
        // Defensive: a malformed response, etc. — same user-facing outcome as IOException.
        mainHandler.post(this::onSearchFailure);
      }
    });
  }

  private void onSearchSuccess(List<Chart> results) {
    if (results.isEmpty()) {
      showEmptyState("No charts found", "Try a different song or artist name.");
      return;
    }
    loadResults(results);
  }

  private void onSearchFailure() {
    showFirstLaunchState();
    Toast.makeText(this, "Search failed — check your connection", Toast.LENGTH_SHORT).show();
  }

  /** Builds a fresh adapter for {@code results} — a clean set of IDLE
   *  states for every row, no stale bleed-over from a previous search's
   *  adapter — wires it up, and shows the list. */
  private void loadResults(List<Chart> results) {
    ResultsAdapter newAdapter = new ResultsAdapter(results, artLoader);
    // Whole-row tap opens the detail screen (DESIGN.md §7.13), passing
    // the already-fetched Chart via Serializable extra — no extra
    // network call needed on the detail screen.
    newAdapter.onRowClick = (pos, c) ->
        startActivity(new Intent(this, SongDetailActivity.class).putExtra("chart", c));
    newAdapter.onDownload = (pos, c) -> startDownload(pos, c, newAdapter);
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
