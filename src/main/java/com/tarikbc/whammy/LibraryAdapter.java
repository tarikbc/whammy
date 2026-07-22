package com.tarikbc.whammy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DESIGN.md §7.12: Library row + non-blocking inline delete-confirm.
 * Echoes {@code ResultsAdapter}'s album-row rhythm (56dp art tile,
 * title, a secondary text line) but the trailing control is a trash
 * glyph instead of the download note button, and there is no download
 * state machine to drive.
 *
 * Deleting never opens a system dialog: tapping trash swaps that row's
 * trailing control for an inline {@code Cancel}/{@code Delete} pair
 * (library_row.xml's confirm_actions). Confirming actually removes the
 * file/folder via {@link SongStore#delete} and removes the row --
 * RecyclerView's default item animator handles the fade/slide-out for
 * free since nothing here overrides it.
 *
 * "Confirming" state is keyed by the row's {@code File}, not its
 * adapter position: positions shift on every delete, so a
 * position-indexed array (as {@code ResultsAdapter} uses for its
 * download states) would let a confirm bubble jump onto the wrong row
 * mid-list.
 */
public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.RowHolder> {

  /** Fired after the list is (re)loaded or a row is deleted, so the
   *  host (LibraryActivity) can refresh the header count/size and the
   *  empty-state visibility without re-querying SongStore itself. */
  public interface OnListChanged {
    void onListChanged(int itemCount, long totalBytes);
  }

  public OnListChanged onListChanged;

  private final List<SongStore.LibraryItem> items = new ArrayList<>();
  private final Set<java.io.File> confirming = new HashSet<>();

  public LibraryAdapter(List<SongStore.LibraryItem> initial) {
    items.addAll(initial);
  }

  /** Replaces the full list (LibraryActivity.onResume's refresh) and
   *  clears any pending confirm state -- a row that isn't on-screen
   *  anymore shouldn't come back mid-confirm. Fires onListChanged once
   *  so the header updates from the fresh data. */
  public void submit(List<SongStore.LibraryItem> newItems) {
    items.clear();
    items.addAll(newItems);
    confirming.clear();
    notifyDataSetChanged();
    notifyListChanged();
  }

  @Override public RowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.library_row, parent, false);
    return new RowHolder(v);
  }

  @Override public void onBindViewHolder(RowHolder h, int position) {
    SongStore.LibraryItem item = items.get(position);
    Context ctx = h.itemView.getContext();

    h.title.setText(item.name);
    h.size.setText(formatBytes(item.sizeBytes));

    boolean isConfirming = confirming.contains(item.file);
    h.trashButton.setVisibility(isConfirming ? View.GONE : View.VISIBLE);
    h.confirmActions.setVisibility(isConfirming ? View.VISIBLE : View.GONE);
    h.trashButton.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.text_lo)));

    // Press feedback only (DESIGN.md §7.12: "turning fret_red on
    // press") -- returns false so the click listener below still fires
    // the normal way; ACTION_UP/CANCEL always restore text_lo even if
    // the press is dragged off the icon.
    h.trashButton.setOnTouchListener((view, ev) -> {
      int action = ev.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        h.trashButton.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.fret_red)));
      } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        h.trashButton.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.text_lo)));
      }
      return false;
    });

    h.trashButton.setOnClickListener(v -> {
      confirming.add(item.file);
      int pos = h.getBindingAdapterPosition();
      if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
    });

    h.cancelButton.setOnClickListener(v -> {
      confirming.remove(item.file);
      int pos = h.getBindingAdapterPosition();
      if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
    });

    h.deleteButton.setOnClickListener(v -> {
      int pos = h.getBindingAdapterPosition();
      if (pos == RecyclerView.NO_POSITION) return;
      confirming.remove(item.file);
      SongStore.delete(item.file);
      items.remove(pos);
      notifyItemRemoved(pos);
      notifyListChanged();
    });
  }

  private void notifyListChanged() {
    if (onListChanged == null) return;
    long total = 0;
    for (SongStore.LibraryItem it : items) total += it.sizeBytes;
    onListChanged.onListChanged(items.size(), total);
  }

  @Override public int getItemCount() {
    return items.size();
  }

  /** DESIGN.md §7.12's "N charts · 312 MB" -- picks whichever of
   *  KB/MB/GB reads most sensibly at the given size. */
  public static String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return Math.round(kb) + " KB";
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
    double gb = mb / 1024.0;
    return String.format(Locale.US, "%.2f GB", gb);
  }

  static class RowHolder extends RecyclerView.ViewHolder {
    final ImageView art;
    final TextView title, size;
    final ImageView trashButton;
    final LinearLayout confirmActions;
    final TextView cancelButton, deleteButton;

    RowHolder(View v) {
      super(v);
      art = v.findViewById(R.id.art);
      title = v.findViewById(R.id.title);
      size = v.findViewById(R.id.size);
      trashButton = v.findViewById(R.id.trash_button);
      confirmActions = v.findViewById(R.id.confirm_actions);
      cancelButton = v.findViewById(R.id.confirm_cancel);
      deleteButton = v.findViewById(R.id.confirm_delete);

      // Corner-box fix, same technique as ResultsAdapter.RowHolder: force
      // -clip the row and the art tile to their rounded outlines.
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
