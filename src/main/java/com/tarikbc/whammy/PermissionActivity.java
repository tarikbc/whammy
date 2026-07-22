package com.tarikbc.whammy;

import android.app.Activity;
import android.os.Bundle;

/**
 * Permission rationale screen (DESIGN.md §7.6): shown by MainActivity /
 * SongDetailActivity in place of the old "toast + jump straight to
 * settings" flow whenever a download is attempted without all-files
 * access. Explains what the access is for and why before asking, then
 * either opens the system grant screen ({@link Permissions#requestAllFiles})
 * or lets the user back out with "Not now".
 *
 * <p>Does NOT auto-resume the download that triggered it -- there is no
 * result contract back to the caller. {@link Permissions#hasAllFiles()}
 * simply reflects whatever the user decided once they're back; if
 * granted, {@link #onResume} finishes this screen automatically (nothing
 * useful left for a rationale screen to say once the rationale has been
 * acted on) and the user just re-taps download on the screen underneath.
 */
public class PermissionActivity extends Activity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_permission);

    findViewById(R.id.grant_button).setOnClickListener(v -> Permissions.requestAllFiles(this));
    findViewById(R.id.not_now_button).setOnClickListener(v -> finish());
  }

  @Override protected void onResume() {
    super.onResume();
    // Covers both "granted it in Settings and came straight back" and
    // "already had it granted somehow" -- either way there's nothing
    // left for this screen to ask.
    if (Permissions.hasAllFiles()) finish();
  }
}
