package com.tarikbc.whammy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DESIGN.md §7.3-§7.4: result row + note-button state machinery.
 *
 * Rows lead with album art (loaded async via {@link ArtLoader}) instead
 * of a per-row fret lane-light — the fret rainbow is icon-only now
 * (DESIGN.md §1.1). The note button is a single neutral accent (`star`
 * idle/downloading, `fret_green` done, `fret_red` error) with no
 * per-row tinting. The DOWNLOADING row still renders the neutral
 * placeholder ring — Task 9's ProgressRingView owns the cyan->blue
 * sweep arc and is added as a further child of the note_button
 * FrameLayout without any restructuring here.
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.RowHolder> {

  public enum DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

  public interface OnDownload {
    void onDownload(int pos, Chart c);
  }

  /** Instrument -> badge icon (DESIGN.md §7.10/§7.7). Instruments not in
   *  this map (e.g. "band") render no badge — presence of the icon is
   *  the signal, absent ones are simply skipped. "rhythm"/"guitarcoop"
   *  are second-guitar parts and share the guitar mark; both GHL
   *  variants share the single "6" mark per §7.7. */
  private static final Map<String, Integer> INSTRUMENT_ICONS = new HashMap<>();
  static {
    INSTRUMENT_ICONS.put("guitar", R.drawable.ic_inst_guitar);
    INSTRUMENT_ICONS.put("rhythm", R.drawable.ic_inst_guitar);
    INSTRUMENT_ICONS.put("guitarcoop", R.drawable.ic_inst_guitar);
    INSTRUMENT_ICONS.put("bass", R.drawable.ic_inst_bass);
    INSTRUMENT_ICONS.put("drums", R.drawable.ic_inst_drums);
    INSTRUMENT_ICONS.put("keys", R.drawable.ic_inst_keys);
    INSTRUMENT_ICONS.put("vocals", R.drawable.ic_inst_vocals);
    INSTRUMENT_ICONS.put("guitarghl", R.drawable.ic_ghl);
    INSTRUMENT_ICONS.put("bassghl", R.drawable.ic_ghl);
  }
  /** DESIGN.md §7.10: "show up to ~4" instrument badges. */
  private static final int MAX_INSTRUMENT_BADGES = 4;

  private final List<Chart> charts;
  private final DownloadState[] states;
  private final int[] percents;
  private final ArtLoader artLoader;

  /** Set by the host to receive note-button taps. */
  public OnDownload onDownload;

  public ResultsAdapter(List<Chart> charts, ArtLoader artLoader) {
    this.charts = charts;
    this.artLoader = artLoader;
    int n = charts.size();
    this.states = new DownloadState[n];
    this.percents = new int[n];
    Arrays.fill(states, DownloadState.IDLE);
  }

  /** Update one row's download state/progress and re-bind it. */
  public void setState(int pos, DownloadState s, int percent) {
    states[pos] = s;
    percents[pos] = percent;
    notifyItemChanged(pos);
  }

  @Override public RowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_result, parent, false);
    return new RowHolder(v);
  }

  @Override public void onBindViewHolder(RowHolder h, int position) {
    Chart c = charts.get(position);
    Context ctx = h.itemView.getContext();

    h.title.setText(c.name);
    artLoader.load(h.art, c.albumArtMd5);

    DownloadState state = states[position] == null ? DownloadState.IDLE : states[position];

    switch (state) {
      case DONE:
        showMeta(h, c);
        h.noteButton.setBackgroundResource(R.drawable.note_done);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_check);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.on_accent)));
        break;

      case ERROR:
        showError(h);
        h.noteButton.setBackgroundResource(R.drawable.note_error);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_retry);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.fret_red)));
        break;

      case DOWNLOADING:
        // Task 9: ProgressRingView renders DOWNLOADING here. This is the
        // neutral star-ring placeholder — no arc yet.
        showMeta(h, c);
        h.noteButton.setBackgroundResource(R.drawable.note_ring);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_download);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.star)));
        break;

      case IDLE:
      default:
        showMeta(h, c);
        h.noteButton.setBackgroundResource(R.drawable.note_ring);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_download);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.star)));
        break;
    }

    final int pos = position;
    h.noteButton.setOnClickListener(v -> {
      if (onDownload != null) onDownload.onDownload(pos, c);
    });
  }

  private void showMeta(RowHolder h, Chart c) {
    h.meta.setVisibility(View.VISIBLE);
    h.errorText.setVisibility(View.GONE);
    h.meta.setText(buildMeta(h.itemView.getContext(), c));
    buildBadges(h, c);
  }

  private void showError(RowHolder h) {
    h.meta.setVisibility(View.GONE);
    h.errorText.setVisibility(View.VISIBLE);
    // Badges are a "third line under the meta" (§7.10) — with meta
    // replaced by the error line, drop them too so the row stays
    // focused on the failure message.
    h.badges.setVisibility(View.GONE);
    h.badges.removeAllViews();
  }

  /**
   * Builds the badge row (DESIGN.md §7.10) fresh on every bind: clears
   * whatever the recycled view previously held, then adds only the
   * badges this chart actually has (instruments -> icons, capped at
   * {@link #MAX_INSTRUMENT_BADGES}; video; duration; PRO/MOD tags).
   * Never leaves stale children from a different row behind.
   */
  private void buildBadges(RowHolder h, Chart c) {
    Context ctx = h.itemView.getContext();
    h.badges.removeAllViews();

    int shown = 0;
    for (String instrument : c.instruments) {
      if (shown >= MAX_INSTRUMENT_BADGES) break;
      Integer icon = instrument == null ? null : INSTRUMENT_ICONS.get(instrument.toLowerCase(Locale.ROOT));
      if (icon == null) continue; // no mark for this instrument — skip, never an empty slot
      addIconBadge(h.badges, ctx, icon);
      shown++;
    }

    if (c.hasVideoBackground) addIconBadge(h.badges, ctx, R.drawable.ic_video);

    String duration = Chart.formatDuration(c.songLengthMs);
    if (!duration.isEmpty()) addTextBadge(h.badges, ctx, duration, R.style.Text_Badge);

    if (c.proDrums) addTextBadge(h.badges, ctx, ctx.getString(R.string.badge_pro), R.style.Text_Charter);
    if (c.modchart) addTextBadge(h.badges, ctx, ctx.getString(R.string.badge_mod), R.style.Text_Charter);

    boolean any = h.badges.getChildCount() > 0;
    h.badges.setVisibility(any ? View.VISIBLE : View.GONE);
  }

  private void addIconBadge(LinearLayout row, Context ctx, int drawableRes) {
    ImageView icon = new ImageView(ctx);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        (int) ctx.getResources().getDimension(R.dimen.badge_icon_size),
        (int) ctx.getResources().getDimension(R.dimen.badge_icon_size));
    if (row.getChildCount() > 0) lp.leftMargin = (int) ctx.getResources().getDimension(R.dimen.badge_gap);
    icon.setLayoutParams(lp);
    icon.setImageResource(drawableRes);
    icon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.text_lo)));
    row.addView(icon);
  }

  private void addTextBadge(LinearLayout row, Context ctx, String text, int styleRes) {
    TextView tv = new TextView(ctx, null, 0, styleRes);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    if (row.getChildCount() > 0) lp.leftMargin = (int) ctx.getResources().getDimension(R.dimen.badge_gap);
    tv.setLayoutParams(lp);
    tv.setText(text);
    row.addView(tv);
  }

  /**
   * Builds "Artist · CHARTED BY XXX" as a single Spannable so the meta
   * TextView (maxLines=1, ellipsize=end) ellipsizes the whole line as
   * one unit — a long artist name eats into the ellipsis before the
   * charter tag is ever squeezed or clipped. Artist keeps the
   * TextView's own Text.Artist appearance (text color/size); the
   * charter portion is a smaller, all-caps, `text_lo` span.
   */
  private CharSequence buildMeta(Context ctx, Chart c) {
    String artist = c.artist == null ? "" : c.artist;
    String charter = c.charter == null ? "" : c.charter;

    SpannableStringBuilder sb = new SpannableStringBuilder();
    sb.append(artist);

    if (!charter.isEmpty()) {
      sb.append("  ·  ");
      int charterStart = sb.length();
      String label = ctx.getString(R.string.charted_by_fmt, charter).toUpperCase(Locale.getDefault());
      sb.append(label);
      int charterEnd = sb.length();

      sb.setSpan(new RelativeSizeSpan(11f / 14f), charterStart, charterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      sb.setSpan(new ForegroundColorSpan(ctx.getColor(R.color.text_lo)), charterStart, charterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    return sb;
  }

  @Override public int getItemCount() {
    return charts.size();
  }

  static class RowHolder extends RecyclerView.ViewHolder {
    final ImageView art;
    final TextView title, meta, errorText;
    final LinearLayout badges;
    final FrameLayout noteButton;
    final ImageView noteIcon;

    RowHolder(View v) {
      super(v);
      art = v.findViewById(R.id.art);
      title = v.findViewById(R.id.title);
      meta = v.findViewById(R.id.meta);
      errorText = v.findViewById(R.id.error_text);
      badges = v.findViewById(R.id.badges);
      noteButton = v.findViewById(R.id.note_button);
      noteIcon = v.findViewById(R.id.note_icon);

      // Corner-box fix: force-clip both the row and the album art to
      // their rounded outlines, independent of how the platform
      // resolves the layer-list backgrounds' own outlines.
      final float rowRadius = v.getResources().getDimension(R.dimen.r_lg);
      v.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rowRadius);
        }
      });
      v.setClipToOutline(true);

      final float artRadius = v.getResources().getDimension(R.dimen.r_sm);
      art.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artRadius);
        }
      });
      art.setClipToOutline(true);
    }
  }
}
