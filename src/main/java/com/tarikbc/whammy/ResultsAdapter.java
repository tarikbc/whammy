package com.tarikbc.whammy;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.List;

/**
 * DESIGN.md §7.3-§7.4: result row + note-button state machinery.
 *
 * Task 8 scope: idle/downloading(placeholder)/done/error rendering and the
 * full state API. The DOWNLOADING row is a neutral placeholder (the idle
 * ring, tinted to the row's fret color) — Task 9's ProgressRingView owns
 * the actual cyan->blue sweep-arc visual and is added as a further child
 * of the note_button FrameLayout without any restructuring here.
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.RowHolder> {

  public enum DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

  public interface OnDownload {
    void onDownload(int pos, Chart c);
  }

  // Row -> fret color, DESIGN.md §3.5: position % 5 -> 0 green . 1 red . 2 yellow . 3 blue . 4 orange
  private static final int[] FRET_COLORS = {
      R.color.fret_green, R.color.fret_red, R.color.fret_yellow, R.color.fret_blue, R.color.fret_orange
  };

  private final List<Chart> charts;
  private final DownloadState[] states;
  private final int[] percents;

  /** Set by the host to receive note-button taps. */
  public OnDownload onDownload;

  public ResultsAdapter(List<Chart> charts) {
    this.charts = charts;
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
    android.content.Context ctx = h.itemView.getContext();

    h.title.setText(c.name);

    int fretColor = ctx.getColor(FRET_COLORS[position % 5]);
    h.laneLight.setBackgroundTintList(ColorStateList.valueOf(fretColor));

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
        // Task 9: ProgressRingView renders DOWNLOADING here. Task 8 placeholder
        // below is the neutral idle ring in the row's fret color — no arc.
        showMeta(h, c);
        h.noteButton.setBackgroundResource(R.drawable.note_ring);
        h.noteButton.setBackgroundTintList(ColorStateList.valueOf(fretColor));
        h.noteIcon.setImageResource(R.drawable.ic_download);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(fretColor));
        break;

      case IDLE:
      default:
        showMeta(h, c);
        h.noteButton.setBackgroundResource(R.drawable.note_ring);
        h.noteButton.setBackgroundTintList(ColorStateList.valueOf(fretColor));
        h.noteIcon.setImageResource(R.drawable.ic_download);
        h.noteIcon.setImageTintList(ColorStateList.valueOf(fretColor));
        break;
    }

    final int pos = position;
    h.noteButton.setOnClickListener(v -> {
      if (onDownload != null) onDownload.onDownload(pos, c);
    });
  }

  private void showMeta(RowHolder h, Chart c) {
    h.metaRow.setVisibility(View.VISIBLE);
    h.errorText.setVisibility(View.GONE);
    h.artist.setText(c.artist);
    h.charter.setText(h.itemView.getContext().getString(R.string.charted_by_fmt, c.charter));
  }

  private void showError(RowHolder h) {
    h.metaRow.setVisibility(View.GONE);
    h.errorText.setVisibility(View.VISIBLE);
  }

  @Override public int getItemCount() {
    return charts.size();
  }

  static class RowHolder extends RecyclerView.ViewHolder {
    final View laneLight;
    final TextView title, artist, charter, errorText;
    final View metaRow;
    final FrameLayout noteButton;
    final ImageView noteIcon;

    RowHolder(View v) {
      super(v);
      laneLight = v.findViewById(R.id.lane_light);
      title = v.findViewById(R.id.title);
      metaRow = v.findViewById(R.id.meta_row);
      artist = v.findViewById(R.id.artist);
      charter = v.findViewById(R.id.charter);
      errorText = v.findViewById(R.id.error_text);
      noteButton = v.findViewById(R.id.note_button);
      noteIcon = v.findViewById(R.id.note_icon);
    }
  }
}
