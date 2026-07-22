package com.tarikbc.whammy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async album-art loader (DESIGN.md §7.3): fetches
 * {@code EncoreApi.artUrl(albumArtMd5)}, downsamples to the 56dp row
 * target, memory-caches by md5, and is safe to call from a recycled
 * RecyclerView row (a stale decode never clobbers a row that has since
 * been rebound to a different song).
 *
 * Failure policy: null/failed art always leaves the placeholder in
 * place. Never crashes, never shows a broken-image box.
 */
public class ArtLoader {

  /** Target on-screen size (dp) — art_placeholder / row_result use the same 56dp slot. */
  private static final int TARGET_DP = 56;

  private final LruCache<String, Bitmap> cache;
  private final ExecutorService executor = Executors.newFixedThreadPool(3);
  private final Handler main = new Handler(Looper.getMainLooper());
  private final int targetPx;

  public ArtLoader(android.content.Context ctx) {
    int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8); // ~1/8 of heap, in KB
    cache = new LruCache<String, Bitmap>(maxKb) {
      @Override protected int sizeOf(String key, Bitmap bmp) {
        return bmp.getByteCount() / 1024;
      }
    };
    targetPx = Math.round(TARGET_DP * ctx.getResources().getDisplayMetrics().density);
  }

  /**
   * Load album art for {@code albumArtMd5} into {@code target}. Sets the
   * placeholder immediately; if/when the async fetch succeeds, swaps in
   * the bitmap ONLY IF the target hasn't been recycled to a different
   * row in the meantime (checked via the view's tag).
   */
  public void load(ImageView target, String albumArtMd5) {
    target.setTag(albumArtMd5);
    target.setImageResource(R.drawable.art_placeholder);

    if (albumArtMd5 == null) return;

    Bitmap cached = cache.get(albumArtMd5);
    if (cached != null) {
      target.setImageBitmap(cached);
      return;
    }

    executor.execute(() -> {
      Bitmap bmp = fetch(albumArtMd5);
      if (bmp == null) return; // keep placeholder — never crash, never a broken box
      cache.put(albumArtMd5, bmp);
      main.post(() -> {
        // Row may have been recycled to a different song while this was
        // in flight; only apply if the tag still matches.
        if (albumArtMd5.equals(target.getTag())) {
          target.setImageBitmap(bmp);
        }
      });
    });
  }

  /**
   * Load a LOCAL album-art image file (e.g. a chart folder's album.jpg) into
   * {@code target}. Same placeholder / cache / recycle-safety contract as
   * {@link #load}. Null/unreadable file leaves the placeholder in place.
   */
  public void loadLocal(ImageView target, java.io.File artFile) {
    String key = artFile == null ? null : "file:" + artFile.getAbsolutePath();
    target.setTag(key);
    target.setImageResource(R.drawable.art_placeholder);
    if (artFile == null || !artFile.isFile()) return;

    Bitmap cached = cache.get(key);
    if (cached != null) { target.setImageBitmap(cached); return; }

    executor.execute(() -> {
      Bitmap bmp = decodeLocal(artFile);
      if (bmp == null) return; // keep placeholder
      cache.put(key, bmp);
      main.post(() -> {
        if (key.equals(target.getTag())) target.setImageBitmap(bmp);
      });
    });
  }

  /** Decodes a local image file downsampled to ~2x the target row size. */
  private Bitmap decodeLocal(java.io.File f) {
    try {
      String path = f.getAbsolutePath();
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
      int wantPx = targetPx * 2;
      int sample = 1;
      int w = bounds.outWidth, h = bounds.outHeight;
      while ((w / (sample * 2)) >= wantPx && (h / (sample * 2)) >= wantPx) sample *= 2;
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = sample;
      return BitmapFactory.decodeFile(path, opts);
    } catch (Exception e) {
      return null;
    }
  }

  /** GETs the art JPEG and decodes it downsampled to ~2x the target row size. */
  private Bitmap fetch(String albumArtMd5) {
    try {
      byte[] bytes = download(EncoreApi.artUrl(albumArtMd5));
      if (bytes == null) return null;

      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

      int wantPx = targetPx * 2;
      int sample = 1;
      int w = bounds.outWidth, h = bounds.outHeight;
      while ((w / (sample * 2)) >= wantPx && (h / (sample * 2)) >= wantPx) {
        sample *= 2;
      }

      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = sample;
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    } catch (Exception e) {
      // IOException, OutOfMemory-adjacent decode failures, etc. — swallow,
      // caller keeps the placeholder.
      return null;
    }
  }

  private byte[] download(String url) {
    HttpURLConnection c = null;
    try {
      c = (HttpURLConnection) new URL(url).openConnection();
      c.setConnectTimeout(15000);
      c.setReadTimeout(15000);
      c.setRequestProperty("User-Agent", "Whammy/1.0");
      int code = c.getResponseCode();
      if (code != 200) return null;
      try (InputStream in = c.getInputStream()) {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) buf.write(b, 0, n);
        return buf.toByteArray();
      }
    } catch (IOException e) {
      return null;
    } finally {
      if (c != null) c.disconnect();
    }
  }
}
