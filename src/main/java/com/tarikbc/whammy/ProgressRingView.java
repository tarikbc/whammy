package com.tarikbc.whammy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/**
 * DESIGN.md §7.4/§9: the one custom-drawn piece of the design — the
 * download note button's determinate cyan->blue sweep arc
 * ({@code star} -> {@code star_deep}), plus an indeterminate spinning
 * 90° arc for the "no progress yet" case ({@code percent == -1}).
 *
 * Not a stock drawable: {@link Canvas#drawArc} + {@link SweepGradient}
 * is the only way to get the rotating cyan->blue sweep the mockup
 * calls for (~40 lines per §9).
 *
 * Recycle-safety: {@link ResultsAdapter} is responsible for hiding this
 * view (GONE) whenever the row isn't DOWNLOADING, and for calling
 * {@link #setPercent(int)} fresh on every DOWNLOADING bind — that reset
 * is what stops a stale ring (or a lingering indeterminate spin) from
 * surviving into a recycled row. The spinner loop here is self-limiting:
 * it reads {@code percent} on every tick and simply stops posting itself
 * once percent is no longer negative.
 */
public class ProgressRingView extends View {

  private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF box = new RectF();

  private int percent = 0;
  private float spin = 0f;
  private boolean trackVisible = true;

  public ProgressRingView(Context c, AttributeSet a) {
    super(c, a);
    float w = 4f * getResources().getDisplayMetrics().density;
    track.setStyle(Paint.Style.STROKE);
    track.setStrokeWidth(w);
    track.setColor(0x1AFFFFFF);
    arc.setStyle(Paint.Style.STROKE);
    arc.setStrokeWidth(w);
    arc.setStrokeCap(Paint.Cap.ROUND);
  }

  /** 0-100 = determinate sweep; -1 = indeterminate spinning 90° arc. */
  public void setPercent(int p) {
    percent = p;
    if (p < 0) {
      startSpin();
    } else {
      spin = 0;
    }
    invalidate();
  }

  /** Shows/hides the faint edge_hair track ring behind the arc. */
  public void setTrackVisible(boolean visible) {
    trackVisible = visible;
    invalidate();
  }

  private void startSpin() {
    removeCallbacks(spinner);
    post(spinner);
  }

  private final Runnable spinner = new Runnable() {
    @Override public void run() {
      if (percent < 0) {
        spin = (spin + 6) % 360;
        invalidate();
        postDelayed(this, 16);
      }
    }
  };

  @Override protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    removeCallbacks(spinner);
  }

  /** Built once per size change (not per frame — the 16ms indeterminate spin
   *  would otherwise allocate a shader every tick). */
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    float inset = arc.getStrokeWidth();
    box.set(inset, inset, w - inset, h - inset);
    arc.setShader(new SweepGradient(box.centerX(), box.centerY(),
        new int[]{0xFF35E6E1, 0xFF3B9DFF, 0xFF35E6E1}, null));
  }

  @Override protected void onDraw(Canvas cv) {
    if (getWidth() == 0 || getHeight() == 0) return;
    if (trackVisible) cv.drawOval(box, track);
    if (percent < 0) {
      cv.drawArc(box, spin - 90, 90, false, arc);
    } else {
      cv.drawArc(box, -90, 360f * percent / 100f, false, arc);
    }
  }
}
