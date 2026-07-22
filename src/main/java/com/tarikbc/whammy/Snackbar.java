package com.tarikbc.whammy;

import android.app.Activity;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.TextView;
import java.util.WeakHashMap;

/**
 * Reusable styled snackbar (DESIGN.md §7.8): {@code surface_top} fill,
 * {@code r_md} corners, {@code edge_top}+{@code edge_hair} outline, a
 * 3dp {@code star} accent bar on the left, and an optional {@code star}
 * UPPER action label. Slides up 16dp + fades in ({@code dur_3} /
 * {@code ease_emphasized}), auto-dismisses after ~3s.
 *
 * <p>Anchored to the bottom of the activity's own content view
 * ({@code android.R.id.content} — already a plain framework
 * {@code FrameLayout}, so no per-activity XML changes are needed to
 * host it: see {@code view_snackbar.xml}). At most one snackbar is
 * shown per activity at a time — calling {@link #show} again
 * cancels/removes whatever is currently up, per DESIGN.md §7.8's
 * "auto-dismiss 3s (cancellable if a new one shows)".
 *
 * <p>No-ops entirely (never inflates/attaches anything) when {@code
 * activity} is null, finishing, or destroyed — the same "a slow
 * callback landing after the user left must not touch dead views" rule
 * the rest of this task's robustness pass applies everywhere else.
 */
public final class Snackbar {
  private Snackbar() {}

  public interface OnAction {
    void onAction();
  }

  private static final int ENTER_DURATION_MS = 240; // dur_3
  private static final int AUTO_DISMISS_MS = 3000;
  private static final float SLIDE_DP = 16f;

  private static final WeakHashMap<Activity, View> CURRENT = new WeakHashMap<>();
  private static final WeakHashMap<Activity, Runnable> DISMISSERS = new WeakHashMap<>();

  /** Plain message, no action. */
  public static void show(Activity activity, String message) {
    show(activity, message, null, null);
  }

  /**
   * Shows a snackbar with an optional action (e.g. "DOWNLOAD AGAIN").
   * Pass both {@code actionLabel} and {@code onAction} non-null to show
   * the action, or either/both null for a plain message-only snackbar.
   */
  public static void show(Activity activity, String message, String actionLabel, OnAction onAction) {
    if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
    ViewGroup content = activity.findViewById(android.R.id.content);
    if (content == null) return;

    dismiss(activity); // replace whatever's currently up, per §7.8

    View bar = LayoutInflater.from(activity).inflate(R.layout.view_snackbar, content, false);
    TextView label = bar.findViewById(R.id.snackbar_label);
    TextView action = bar.findViewById(R.id.snackbar_action);
    label.setText(message);

    if (actionLabel != null && onAction != null) {
      action.setText(actionLabel);
      action.setVisibility(View.VISIBLE);
      action.setOnClickListener(v -> {
        dismiss(activity);
        onAction.onAction();
      });
    } else {
      action.setVisibility(View.GONE);
    }

    // Corner-box fix (same technique ResultsAdapter/LibraryAdapter use for
    // their rows): force-clip the card to its own rounded outline so the
    // straight-edged accent bar child never pokes out past the r_md corners.
    final float radius = activity.getResources().getDimension(R.dimen.r_md);
    bar.setOutlineProvider(new ViewOutlineProvider() {
      @Override public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
      }
    });
    bar.setClipToOutline(true);

    content.addView(bar);
    CURRENT.put(activity, bar);

    float slidePx = SLIDE_DP * activity.getResources().getDisplayMetrics().density;
    bar.setTranslationY(slidePx);
    bar.setAlpha(0f);
    bar.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(ENTER_DURATION_MS)
        .setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f)) // ease_emphasized
        .start();

    Runnable dismisser = () -> dismiss(activity);
    DISMISSERS.put(activity, dismisser);
    bar.postDelayed(dismisser, AUTO_DISMISS_MS);
  }

  /**
   * Removes whatever snackbar is currently shown for {@code activity}
   * (a no-op if none is up, or if {@code activity} is null). Safe to
   * call redundantly — e.g. an action tap calls this before running its
   * handler, and the auto-dismiss timer calls this too; whichever fires
   * first wins and the other becomes a no-op.
   */
  public static void dismiss(Activity activity) {
    if (activity == null) return;
    View bar = CURRENT.remove(activity);
    Runnable dismisser = DISMISSERS.remove(activity);
    if (bar != null && dismisser != null) bar.removeCallbacks(dismisser);
    if (bar != null && bar.getParent() instanceof ViewGroup) {
      ((ViewGroup) bar.getParent()).removeView(bar);
    }
  }
}
