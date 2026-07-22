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
 * On any failure the temp file is deleted before {@link Callback#onError}
 * fires. All callback methods are invoked on the main thread.
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
            percent -> main.post(() -> cb.onProgress(percent)));
        File placed = SongStore.place(tmp, chart);
        main.post(() -> cb.onDone(placed));
      } catch (Exception e) {
        tmp.delete();
        main.post(() -> cb.onError(e));
      }
    });
    return true;
  }
}
