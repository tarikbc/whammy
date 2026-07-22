package com.tarikbc.whammy;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle s) {
    super.onCreate(s);
    setContentView(R.layout.activity_main);

    setUpSearchField();
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

    ArtLoader artLoader = new ArtLoader(this);

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

    // TEMP demo — replaced by live wiring in Task 10. Real albumArtMd5
    // values (verified covers on files.enchor.us) so art actually loads
    // on-device; md5 (download id) is a placeholder — downloads aren't
    // exercised by this demo. Instrument/video/duration/pro/mod fields
    // are real too, so the DESIGN.md §7.10 badges render truthfully.
    // States IDLE/DOWNLOADING(63)/DONE/ERROR/IDLE/IDLE mirror
    // mockup/search-screen.html.
    List<Chart> fake = new ArrayList<>();
    // row 0 IDLE — has VIDEO badge
    fake.add(new Chart("m0", "Guitar Hero", "Khary", "BUNHEAD", "88e679b8f7f4d385de554a384786e7e1",
        java.util.Arrays.asList("guitar"), true, 251624, false, false));
    // row 1 DOWNLOADING — guitar/bass/rhythm
    fake.add(new Chart("m1", "Joe Perry Guitar Battle (Co-op)", "Joe Perry", "Neversoft", "d1dc634bc90b064ed16fdcbfcb7969d0",
        java.util.Arrays.asList("guitar", "bass", "rhythm"), false, 249215, false, false));
    // row 2 DONE
    fake.add(new Chart("m2", "Guitar Hero 1: Endless Setlist", "Various Artists", "Harmonix, Miscellany", "edf7d4c3aad4334c3f6452edc9186e45",
        java.util.Arrays.asList("guitar"), false, 11076561, false, false));
    // row 3 ERROR
    fake.add(new Chart("m3", "Guitar Hero: On Tour Endless Setlist", "Various Artists", "Vicarious Visions, Miscellany", "f3ece681106f6396107c3f96e05fd2a2",
        java.util.Arrays.asList("guitar", "bass"), false, 6872680, false, false));
    // row 4 IDLE — no duration
    fake.add(new Chart("m4", "Guitar Hero Hero", "LeetStreet Boys", "CyclopsDragon", "e7fc37da3303f7fdccfd3df8392709e1",
        java.util.Arrays.asList("guitar"), false, 0, false, false));
    // row 5 IDLE — PRO badge
    fake.add(new Chart("m5", "Guitar Hero (Sep 3, 2005 Prototype)", "Monkey Steals The Peach", "Harmonix", "09de3eed516f785920f5a6328e263cce",
        java.util.Arrays.asList("guitar"), false, 0, true, false));

    ResultsAdapter adapter = new ResultsAdapter(fake, artLoader);
    adapter.setState(1, ResultsAdapter.DownloadState.DOWNLOADING, 63);
    adapter.setState(2, ResultsAdapter.DownloadState.DONE, 100);
    adapter.setState(3, ResultsAdapter.DownloadState.ERROR, 0);
    rv.setAdapter(adapter);

    container.addView(rv);
  }

  /** Wires the search field's clear button; the search itself is Task 10. */
  private void setUpSearchField() {
    EditText input = findViewById(R.id.search_input);
    ImageView clear = findViewById(R.id.search_clear);

    input.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
        clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
      }
      @Override public void afterTextChanged(Editable s) {
        // Task 10: execute search on IME action / debounce.
      }
    });

    clear.setOnClickListener(v -> input.setText(""));
  }

  /** Wires the app-bar library glyph (DESIGN.md §7.1b). */
  private void setUpLibraryButton() {
    ImageView library = findViewById(R.id.library_button);
    library.setOnClickListener(v -> {
      // R4: open Library screen.
      Toast.makeText(this, R.string.library_coming_soon, Toast.LENGTH_SHORT).show();
    });
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}
