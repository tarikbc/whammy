package com.tarikbc.whammy;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle s) {
    super.onCreate(s);
    setContentView(R.layout.activity_main);

    // TEMP Task 8 demo — replaced by live wiring in Task 10.
    //
    // RecyclerView is instantiated in *code*, not declared as a
    // <...RecyclerView> tag in any XML layout — this app never inflates
    // one from XML and never overrides the `recyclerViewStyle` theme
    // attribute, so RecyclerView's (always-null) AttributeSet resolution
    // never needs to match a real declared attribute. The
    // androidx.recyclerview.R class it still unconditionally references
    // at construction time (for R.attr.recyclerViewStyle /
    // R.styleable.RecyclerView, read regardless of XML vs. code) is a
    // hand-written stub at
    // src/main/java/androidx/recyclerview/R.java — see that file and
    // task-8-report.md for the full reasoning and verification (this
    // build.sh does not merge AAR resources, so aapt2 never generates a
    // real one).
    FrameLayout container = findViewById(R.id.results_container);

    RecyclerView rv = new RecyclerView(this);
    rv.setLayoutParams(new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    rv.setLayoutManager(new LinearLayoutManager(this));
    rv.setClipToPadding(false);

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

    // 6 fake rows, states IDLE/DOWNLOADING(63)/DONE/ERROR/IDLE/IDLE — mirrors
    // mockup/search-screen.html so the row + note-button states can be
    // eyeballed against it.
    List<Chart> fake = new ArrayList<>();
    fake.add(new Chart("1", "Sultans of Swing", "Dire Straits", "Harmonix"));
    fake.add(new Chart("2", "Sultans of the Night", "Sabaton", "FretMaster"));
    fake.add(new Chart("3", "Sultan's Curse", "Mastodon", "Nyxion"));
    fake.add(new Chart("4", "Sultans (Live in Koln)", "Dire Straits", "Encore"));
    fake.add(new Chart("5", "Sultan Groove", "The Aristocrats", "BlueGoo"));
    fake.add(new Chart("6", "Desert Sultans", "Karnivool", "EZ-Chart"));

    ResultsAdapter adapter = new ResultsAdapter(fake);
    adapter.setState(1, ResultsAdapter.DownloadState.DOWNLOADING, 63);
    adapter.setState(2, ResultsAdapter.DownloadState.DONE, 100);
    adapter.setState(3, ResultsAdapter.DownloadState.ERROR, 0);
    rv.setAdapter(adapter);

    container.addView(rv);
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}
