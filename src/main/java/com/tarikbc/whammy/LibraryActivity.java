package com.tarikbc.whammy;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

/**
 * Library screen (DESIGN.md §7.12): lists downloaded charts from
 * {@link SongStore#songsDir()} with a non-blocking inline delete-confirm
 * on each row ({@link LibraryAdapter}). Opened from the app bar
 * {@code library} glyph (§7.1b, {@code MainActivity#setUpLibraryButton}).
 *
 * The RecyclerView is built in *code*, exactly like MainActivity's
 * results_container -- this app never inflates a RecyclerView from XML
 * (see MainActivity's onCreate comment for why: build.sh vendors just
 * enough AAR res/ to give recyclerview's precompiled bytecode a real
 * R.attr.recyclerViewStyle to read at construction time, and that
 * plumbing is only exercised for RecyclerViews built programmatically).
 *
 * The list reloads fresh in {@link #onResume} so it reflects downloads
 * made on the search screen or deletes made on a previous visit to this
 * screen.
 */
public class LibraryActivity extends Activity {

  private LibraryAdapter adapter;
  private TextView countText;
  private View emptyState;
  private RecyclerView rv;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_library);

    findViewById(R.id.back_button).setOnClickListener(v -> finish());

    countText = findViewById(R.id.count_text);
    emptyState = findViewById(R.id.empty_state);
    FrameLayout listContainer = findViewById(R.id.list_container);

    adapter = new LibraryAdapter(Collections.emptyList(), new ArtLoader(this));
    adapter.onListChanged = this::updateHeader;

    rv = new RecyclerView(this);
    rv.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    rv.setLayoutManager(new LinearLayoutManager(this));
    rv.setClipToPadding(false);

    int padH = dp(20), padTop = dp(4), padBottom = dp(20);
    rv.setPadding(padH, padTop, padH, padBottom);

    // Row gap 12dp (DESIGN.md §6), same ItemDecoration MainActivity uses.
    final int gap = dp(12);
    rv.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos != state.getItemCount() - 1) outRect.bottom = gap;
      }
    });

    rv.setAdapter(adapter);
    listContainer.addView(rv);

    bindScanHint();
  }

  @Override protected void onResume() {
    super.onResume();
    refresh();
  }

  /** Reloads {@code SongStore.list()} -- picks up charts downloaded or
   *  deleted elsewhere since this screen was last on top. */
  private void refresh() {
    List<SongStore.LibraryItem> items = SongStore.list();
    adapter.submit(items);
  }

  /** Updates the "N charts · X MB" count sub and swaps between the list
   *  and the empty state (DESIGN.md §7.12). Wired as LibraryAdapter's
   *  onListChanged, so it fires both on the initial load and after
   *  every in-place delete. */
  private void updateHeader(int count, long totalBytes) {
    boolean empty = count == 0;
    rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

    String chartsWord = count == 1 ? "chart" : "charts";
    // TODO promote to strings.xml as a proper plural/format resource
    // (values/ belongs to another agent -- see LibraryAdapter/activity_library.xml TODOs).
    countText.setText(count + " " + chartsWord + "  ·  " + LibraryAdapter.formatBytes(totalBytes));
  }

  /** Scan-hint footer (DESIGN.md §7.9/§7.12): "Scan" emphasized in
   *  text_hi, the rest text_lo. */
  private void bindScanHint() {
    TextView scanText = findViewById(R.id.scan_hint_text);
    String full = "Scan your library in Clone Hero to see new charts.";
    SpannableString s = new SpannableString(full);
    s.setSpan(new ForegroundColorSpan(getColor(R.color.text_hi)), 0, "Scan".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    scanText.setText(s);
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}
