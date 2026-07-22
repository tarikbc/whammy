package com.tarikbc.whammy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
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
 * per-row tinting. Task 9's {@link ProgressRingView} (progress_ring,
 * a further child of the note_button FrameLayout in row_result.xml)
 * owns the cyan->blue sweep arc: it's shown/percent-set in
 * DOWNLOADING and hidden (with note_icon shown instead) in every
 * other state, so a recycled row never carries a stale arc.
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.RowHolder> {

  public enum DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

  public interface OnDownload {
    void onDownload(int pos, Chart c);
  }

  /** Whole-row tap — the seam the detail screen (DESIGN.md §7.13) hangs
   *  off of. Fired from itemView's own click listener in
   *  onBindViewHolder; the note button has its own OnClickListener and,
   *  being a clickable child, consumes its own taps before they'd ever
   *  reach the row (standard Android view dispatch — no extra flag
   *  needed to keep the two from double-firing). */
  public interface OnRowClick {
    void onRowClick(int pos, Chart c);
  }

  /** Set by the host to receive whole-row taps. May be null. */
  public OnRowClick onRowClick;

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

  /** Chip geometry (DESIGN.md §7.10). These live here as raw dp rather
   *  than in dimens.xml/styles.xml — this task's file ownership is
   *  scoped to row_result.xml + ResultsAdapter.java + drawable/ only,
   *  values/ belongs to another agent — but the numbers themselves are
   *  exactly the spec's: "~22-24dp tall" (23dp, the midpoint), "8dp
   *  horizontal padding", "15dp icons". CHIP_ICON_GAP is the small
   *  internal gap between an icon and its label (or between clustered
   *  instrument icons) inside a single chip — distinct from
   *  R.dimen.badge_gap (6dp), which is the gap BETWEEN chips. */
  private static final float CHIP_HEIGHT_DP = 23f;
  private static final float CHIP_PADDING_H_DP = 8f;
  private static final float CHIP_ICON_SIZE_DP = 15f;
  private static final float CHIP_ICON_GAP_DP = 3f;

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
        hideProgressRing(h);
        h.noteButton.setBackgroundResource(R.drawable.note_done);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_check);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.on_accent)));
        break;

      case ERROR:
        showError(h);
        hideProgressRing(h);
        h.noteButton.setBackgroundResource(R.drawable.note_error);
        h.noteButton.setBackgroundTintList(null);
        h.noteIcon.setImageResource(R.drawable.ic_retry);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.fret_red)));
        break;

      case DOWNLOADING:
        // Task 9 (DESIGN.md §7.4): the cyan->blue sweep-arc ring takes
        // over from the star icon while a download is in flight.
        // percents[position] < 0 means "no progress yet" -> indeterminate
        // spin; ProgressRingView.setPercent handles both.
        showMeta(h, c);
        h.noteIcon.setVisibility(View.GONE);
        h.progressRing.setVisibility(View.VISIBLE);
        h.progressRing.setPercent(percents[position]);
        h.noteButton.setBackgroundResource(R.drawable.note_ring);
        h.noteButton.setBackgroundTintList(null);
        break;

      case IDLE:
      default:
        showMeta(h, c);
        hideProgressRing(h);
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

    h.itemView.setOnClickListener(v -> {
      if (onRowClick != null) onRowClick.onRowClick(pos, c);
    });
  }

  /**
   * Restores the plain note_icon ImageView and hides progress_ring for
   * every non-DOWNLOADING state. Called on every bind (including the
   * rebind {@link #setState} triggers via notifyItemChanged) so a row
   * recycled out of DOWNLOADING never leaves a stale ring visible —
   * note_icon's own image/tint is set right after by the caller.
   */
  private void hideProgressRing(RowHolder h) {
    h.progressRing.setVisibility(View.GONE);
    h.noteIcon.setVisibility(View.VISIBLE);
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
   * whatever the recycled view previously held (h.badges.removeAllViews
   * as the very first step — RecyclerView recycles rows, so a stale
   * chip from a different chart must never survive into this bind),
   * then adds only the chips this chart actually has: ONE combined
   * instrument-icon cluster chip, a duration chip, a star-tinted VIDEO
   * chip, and label-only PRO/MOD chips. Never renders an empty chip for
   * an absent/false attribute.
   */
  private void buildBadges(RowHolder h, Chart c) {
    Context ctx = h.itemView.getContext();
    h.badges.removeAllViews();

    buildInstrumentChip(h.badges, ctx, c);

    String duration = Chart.formatDuration(c.songLengthMs);
    if (!duration.isEmpty()) buildDurationChip(h.badges, ctx, duration);

    if (c.hasVideoBackground) buildVideoChip(h.badges, ctx);

    if (c.proDrums) buildLabelChip(h.badges, ctx, ctx.getString(R.string.badge_pro));
    if (c.modchart) buildLabelChip(h.badges, ctx, ctx.getString(R.string.badge_mod));

    boolean any = h.badges.getChildCount() > 0;
    h.badges.setVisibility(any ? View.VISIBLE : View.GONE);
  }

  /**
   * The instrument chip (DESIGN.md §7.10): ONE chip holding a tidy
   * cluster of charted-instrument icons (up to {@link
   * #MAX_INSTRUMENT_BADGES}, then a "+N" overflow label), rather than
   * five floating marks. Instruments with no mapped icon are skipped
   * silently (never counted, never leave a gap). Renders nothing when
   * the chart has no mappable instruments.
   */
  private void buildInstrumentChip(LinearLayout row, Context ctx, Chart c) {
    List<Integer> icons = new ArrayList<>();
    if (c.instruments != null) {
      for (String instrument : c.instruments) {
        Integer icon = instrument == null ? null : INSTRUMENT_ICONS.get(instrument.toLowerCase(Locale.ROOT));
        if (icon != null) icons.add(icon);
      }
    }
    if (icons.isEmpty()) return;

    LinearLayout chip = newChip(ctx, row, R.drawable.bg_chip);
    int shown = Math.min(icons.size(), MAX_INSTRUMENT_BADGES);
    for (int i = 0; i < shown; i++) {
      ImageView icon = new ImageView(ctx);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, CHIP_ICON_SIZE_DP), dp(ctx, CHIP_ICON_SIZE_DP));
      if (i > 0) lp.leftMargin = dp(ctx, CHIP_ICON_GAP_DP);
      icon.setLayoutParams(lp);
      icon.setImageResource(icons.get(i));
      icon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.text)));
      chip.addView(icon);
    }

    int overflow = icons.size() - shown;
    if (overflow > 0) {
      TextView tv = new TextView(ctx, null, 0, R.style.Text_Badge);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = dp(ctx, CHIP_ICON_GAP_DP);
      tv.setLayoutParams(lp);
      tv.setTextColor(ctx.getColor(R.color.text)); // chip contents are `text`, not the style's default text_lo
      tv.setText("+" + overflow);
      chip.addView(tv);
    }

    row.addView(chip);
  }

  /** Duration chip: {@code ic_clock} + {@code m:ss} (DESIGN.md §7.10). */
  private void buildDurationChip(LinearLayout row, Context ctx, String duration) {
    LinearLayout chip = newChip(ctx, row, R.drawable.bg_chip);

    ImageView icon = new ImageView(ctx);
    icon.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, CHIP_ICON_SIZE_DP), dp(ctx, CHIP_ICON_SIZE_DP)));
    icon.setImageResource(R.drawable.ic_clock);
    icon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.text)));
    chip.addView(icon);

    TextView tv = new TextView(ctx, null, 0, R.style.Text_Badge);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = dp(ctx, CHIP_ICON_GAP_DP);
    tv.setLayoutParams(lp);
    tv.setTextColor(ctx.getColor(R.color.text));
    tv.setText(duration);
    chip.addView(tv);

    row.addView(chip);
  }

  /**
   * Video chip: {@code ic_video} + {@code VIDEO}, given the row's one
   * bit of color (DESIGN.md §7.10) — star-tinted icon on a
   * star-hairlined chip shell ({@code bg_chip_video}). The label itself
   * stays `text` (not `star`) — only the icon + hairline carry the
   * accent, per spec.
   */
  private void buildVideoChip(LinearLayout row, Context ctx) {
    LinearLayout chip = newChip(ctx, row, R.drawable.bg_chip_video);

    ImageView icon = new ImageView(ctx);
    icon.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, CHIP_ICON_SIZE_DP), dp(ctx, CHIP_ICON_SIZE_DP)));
    icon.setImageResource(R.drawable.ic_video);
    icon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.star)));
    chip.addView(icon);

    TextView tv = new TextView(ctx, null, 0, R.style.Text_Charter); // +6% letter-spacing, UPPER
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = dp(ctx, CHIP_ICON_GAP_DP);
    tv.setLayoutParams(lp);
    tv.setTextColor(ctx.getColor(R.color.text));
    // No R.string.badge_video exists (strings.xml is outside this task's
    // file scope — values/ belongs to another agent); literal is safe
    // since Text.Charter already applies textAllCaps.
    tv.setText("VIDEO");
    chip.addView(tv);

    row.addView(chip);
  }

  /** Label-only text chip (PRO / MOD): {@code text}, +6% letter-spacing, UPPER (DESIGN.md §7.10). */
  private void buildLabelChip(LinearLayout row, Context ctx, String label) {
    LinearLayout chip = newChip(ctx, row, R.drawable.bg_chip);

    TextView tv = new TextView(ctx, null, 0, R.style.Text_Charter);
    tv.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    tv.setTextColor(ctx.getColor(R.color.text)); // Text.Charter defaults to text_lo — chips want `text`
    tv.setText(label);
    chip.addView(tv);

    row.addView(chip);
  }

  /**
   * Shared chip shell (DESIGN.md §7.10): {@code backgroundRes} fill
   * ({@code bg_chip} or the star-hairlined {@code bg_chip_video}),
   * ~23dp tall, 8dp horizontal padding, ~6dp ({@link R.dimen#badge_gap})
   * from the previous chip in {@code row} (no margin on the first).
   */
  private LinearLayout newChip(Context ctx, LinearLayout row, int backgroundRes) {
    LinearLayout chip = new LinearLayout(ctx);
    chip.setOrientation(LinearLayout.HORIZONTAL);
    chip.setGravity(Gravity.CENTER_VERTICAL);
    chip.setBackgroundResource(backgroundRes);
    int hPad = dp(ctx, CHIP_PADDING_H_DP);
    chip.setPadding(hPad, 0, hPad, 0);

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, CHIP_HEIGHT_DP));
    if (row.getChildCount() > 0) lp.leftMargin = (int) ctx.getResources().getDimension(R.dimen.badge_gap);
    chip.setLayoutParams(lp);
    return chip;
  }

  private static int dp(Context ctx, float valueDp) {
    return Math.round(valueDp * ctx.getResources().getDisplayMetrics().density);
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
    final ProgressRingView progressRing;

    RowHolder(View v) {
      super(v);
      art = v.findViewById(R.id.art);
      title = v.findViewById(R.id.title);
      meta = v.findViewById(R.id.meta);
      errorText = v.findViewById(R.id.error_text);
      badges = v.findViewById(R.id.badges);
      noteButton = v.findViewById(R.id.note_button);
      noteIcon = v.findViewById(R.id.note_icon);
      progressRing = v.findViewById(R.id.progress_ring);

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
