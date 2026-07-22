package com.tarikbc.whammy;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class Chart {
    public final String md5, name, artist, charter, albumArtMd5;
    public final List<String> instruments;
    public final boolean hasVideoBackground;
    public final int songLengthMs;
    public final boolean proDrums;
    public final boolean modchart;
    public Chart(String md5, String name, String artist, String charter, String albumArtMd5,
                 List<String> instruments, boolean hasVideoBackground, int songLengthMs,
                 boolean proDrums, boolean modchart) {
        this.md5 = md5; this.name = name; this.artist = artist; this.charter = charter; this.albumArtMd5 = albumArtMd5;
        this.instruments = instruments == null ? Collections.emptyList() : Collections.unmodifiableList(instruments);
        this.hasVideoBackground = hasVideoBackground;
        this.songLengthMs = songLengthMs;
        this.proDrums = proDrums;
        this.modchart = modchart;
    }
    public static Chart fromJson(JSONObject o) {
        List<String> instruments = new ArrayList<>();
        JSONObject nd = o.optJSONObject("notesData");
        if (nd != null) {
            JSONArray ia = nd.optJSONArray("instruments");
            if (ia != null) {
                for (int i = 0; i < ia.length(); i++) {
                    String v = ia.optString(i);
                    if (v != null && !v.isEmpty()) instruments.add(v);
                }
            }
        }
        return new Chart(
            o.optString("md5", null), s(o,"name"), s(o,"artist"), s(o,"charter"), s(o,"albumArtMd5"),
            instruments,
            o.optBoolean("hasVideoBackground", false),
            o.optInt("song_length", 0),
            o.optBoolean("pro_drums", false),
            o.optBoolean("modchart", false)
        );
    }
    private static String s(JSONObject o, String k) { return o.isNull(k) ? null : o.optString(k, null); }

    public static String formatDuration(int ms) {
        if (ms <= 0) return "";
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" + seconds : String.valueOf(seconds));
    }
}
