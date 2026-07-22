package com.tarikbc.whammy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A small left-to-right, top-to-bottom wrapping container — used by
 * {@link SongDetailActivity} to lay out the detail screen's feature
 * badge chips (DESIGN.md §7.13: "wrap to multiple lines is fine here,
 * unlike the row"). Children are measured at their own wrap_content
 * size and packed into rows; a child that doesn't fit the remaining
 * width starts a new row. No per-child margins are read — the fixed
 * {@link #hGapPx}/{@link #vGapPx} gaps below are the DESIGN.md §7.10
 * ~6dp inter-chip spacing.
 */
public class FlowLayout extends ViewGroup {

  private final int hGapPx;
  private final int vGapPx;

  public FlowLayout(Context ctx) {
    this(ctx, null);
  }

  public FlowLayout(Context ctx, AttributeSet attrs) {
    super(ctx, attrs);
    float density = ctx.getResources().getDisplayMetrics().density;
    hGapPx = Math.round(6 * density);
    vGapPx = Math.round(8 * density);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int availableWidth = width - getPaddingLeft() - getPaddingRight();

    int curLineWidth = 0;
    int curLineHeight = 0;
    int totalHeight = getPaddingTop() + getPaddingBottom();
    int childCount = getChildCount();

    for (int i = 0; i < childCount; i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      child.measure(
          MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      int childW = child.getMeasuredWidth();
      int childH = child.getMeasuredHeight();

      if (curLineWidth > 0 && curLineWidth + hGapPx + childW > availableWidth) {
        totalHeight += curLineHeight + vGapPx;
        curLineWidth = childW;
        curLineHeight = childH;
      } else {
        curLineWidth += (curLineWidth > 0 ? hGapPx : 0) + childW;
        curLineHeight = Math.max(curLineHeight, childH);
      }
    }
    totalHeight += curLineHeight;

    setMeasuredDimension(width, resolveSize(totalHeight, heightMeasureSpec));
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int paddingLeft = getPaddingLeft();
    int availableWidth = (r - l) - paddingLeft - getPaddingRight();

    int curX = paddingLeft;
    int curY = getPaddingTop();
    int curLineHeight = 0;
    int childCount = getChildCount();

    for (int i = 0; i < childCount; i++) {
      View child = getChildAt(i);
      if (child.getVisibility() == GONE) continue;
      int childW = child.getMeasuredWidth();
      int childH = child.getMeasuredHeight();

      if (curX > paddingLeft && (curX - paddingLeft) + childW > availableWidth) {
        curX = paddingLeft;
        curY += curLineHeight + vGapPx;
        curLineHeight = 0;
      }

      child.layout(curX, curY, curX + childW, curY + childH);
      curX += childW + hGapPx;
      curLineHeight = Math.max(curLineHeight, childH);
    }
  }
}
