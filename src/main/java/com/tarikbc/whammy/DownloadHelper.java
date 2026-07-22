package com.tarikbc.whammy;

import android.app.Activity;
import android.os.Handler;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared "download this chart into the Clone Hero Songs folder" flow, used
 * by both {@link MainActivity} (per-row downloads) and
 * {@link SongDetailActivity} (the detail screen's Download button) so the
 * permission check / background threading / temp-file / SongStore.place
 * logic exists in exactly one place.
 *
 * Downloads to {@code <cacheDir>/<md5>.sng.tmp}, then hands the temp file to
 * {@link SongStore#place} to be moved+renamed into the real Songs folder.
 * On a failure that happens AFTER a successful download (i.e. {@link
 * SongStore#place} itself throws), the fully-downloaded temp file is
 * deleted before {@link Callback#onError} fires. A failure DURING the
 * download itself is deliberately left alone: {@link EncoreApi#downloadSng}
 * writes to a sibling {@code .part} file keyed by the same deterministic
 * {@code md5}-based path, and leaves it in place on failure so a later
 * retry for the same chart (same {@code activity.getCacheDir()}, same
 * {@code md5}) resumes from where it left off instead of restarting.
 *
 * <p>All callback methods are invoked on the main thread, and only if
 * {@code activity} is still alive at that point ({@code
 * !isFinishing() && !isDestroyed()}) -- a download that finishes after
 * the user has already left the screen simply never calls back, rather
 * than touching views on a dead activity.
 */
public class DownloadHelper {
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

  public interface Callback {
    /** percent may be -1 if the server didn't send a Content-Length. */
    void onProgress(int percent);
    void onDone(File placed);
    void onError(Exception e);
  }

  /**
   * Starts the download if (and only if) all-files access is already
   * granted. Returns false without starting anything if permission is
   * missing -- callers must check this and drive their own rationale +
   * {@link Permissions#requestAllFiles} in that case, so a download never
   * silently no-ops from the user's point of view.
   */
  public static boolean start(Activity activity, Chart chart, Callback cb) {
    if (!Permissions.hasAllFiles()) return false;

    File tmp = new File(activity.getCacheDir(), chart.md5 + ".sng.tmp");
    Handler main = new Handler(activity.getMainLooper());

    EXECUTOR.execute(() -> {
      try {
        EncoreApi.downloadSng(chart.md5, tmp,
            percent -> postIfAlive(main, activity, () -> cb.onProgress(percent)));
        File placed = SongStore.place(tmp, chart);
        postIfAlive(main, activity, () -> cb.onDone(placed));
      } catch (Exception e) {
        tmp.delete();
        postIfAlive(main, activity, () -> cb.onError(e));
      }
    });
    return true;
  }

  /** Posts {@code r} to the main thread, but only runs it once there if
   *  {@code activity} is still alive -- guards every callback that
   *  touches views (progress, done, error) against a download that
   *  finishes after the user has already left the screen. */
  private static void postIfAlive(Handler main, Activity activity, Runnable r) {
    main.post(() -> {
      if (activity.isFinishing() || activity.isDestroyed()) return;
      r.run();
    });
  }
}
