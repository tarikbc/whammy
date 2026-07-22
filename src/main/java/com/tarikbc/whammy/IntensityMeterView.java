package com.tarikbc.whammy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * The detail screen's per-instrument intensity meter (DESIGN.md §7.13):
 * a fixed 6-segment bar. {@link #setFilledSegments(int)} sets how many
 * of the 6 segments render filled ({@code star}) vs. empty
 * ({@code surface_top}) — the caller is responsible for capping the raw
 * intensity value at 6 (the number itself, which can exceed 6, is
 * printed separately by the caller).
 *
 * Deliberately a tiny custom View rather than 6 stacked drawables/state
 * selectors — one {@code onDraw} loop is simpler to keep correct.
 */
public class IntensityMeterView extends View {

  private static final int SEGMENTS = 6;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int filledColor;
  private final int emptyColor;
  private final float gapPx;
  private int filled = 0;

  public IntensityMeterView(Context ctx) {
    this(ctx, null);
  }

  public IntensityMeterView(Context ctx, AttributeSet attrs) {
    super(ctx, attrs);
    filledColor = ctx.getColor(R.color.star);
    emptyColor = ctx.getColor(R.color.surface_top);
    gapPx = 3f * ctx.getResources().getDisplayMetrics().density;
  }

  /** Clamped to [0, 6]; the caller passes {@code min(intensity, 6)}. */
  public void setFilledSegments(int n) {
    int clamped = Math.max(0, Math.min(SEGMENTS, n));
    if (clamped != filled) {
      filled = clamped;
      invalidate();
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;

    float totalGap = gapPx * (SEGMENTS - 1);
    float segW = (w - totalGap) / SEGMENTS;
    float radius = h / 2f;

    for (int i = 0; i < SEGMENTS; i++) {
      float left = i * (segW + gapPx);
      paint.setColor(i < filled ? filledColor : emptyColor);
      canvas.drawRoundRect(left, 0, left + segW, h, radius, radius, paint);
    }
  }
}
