package com.tarikbc.whammy;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class Chart {
    public final String md5, name, artist, charter, albumArtMd5;
    public final List<String> instruments;
    public final boolean hasVideoBackground;
    public final int songLengthMs;
    public final boolean proDrums;
    public final boolean modchart;
    public final String album, genre, year;
    public final Map<String, Integer> difficulties;
    public final boolean hasLyrics, hasVocals, hasSoloSections;
    public Chart(String md5, String name, String artist, String charter, String albumArtMd5,
                 List<String> instruments, boolean hasVideoBackground, int songLengthMs,
                 boolean proDrums, boolean modchart) {
        this(md5, name, artist, charter, albumArtMd5, instruments, hasVideoBackground, songLengthMs,
             proDrums, modchart, null, null, null, Collections.emptyMap(), false, false, false);
    }
    private Chart(String md5, String name, String artist, String charter, String albumArtMd5,
                 List<String> instruments, boolean hasVideoBackground, int songLengthMs,
                 boolean proDrums, boolean modchart, String album, String genre, String year,
                 Map<String, Integer> difficulties, boolean hasLyrics, boolean hasVocals, boolean hasSoloSections) {
        this.md5 = md5; this.name = name; this.artist = artist; this.charter = charter; this.albumArtMd5 = albumArtMd5;
        this.instruments = instruments == null ? Collections.emptyList() : Collections.unmodifiableList(instruments);
        this.hasVideoBackground = hasVideoBackground;
        this.songLengthMs = songLengthMs;
        this.proDrums = proDrums;
        this.modchart = modchart;
        this.album = album; this.genre = genre; this.year = year;
        this.difficulties = difficulties == null ? Collections.emptyMap() : Collections.unmodifiableMap(difficulties);
        this.hasLyrics = hasLyrics; this.hasVocals = hasVocals; this.hasSoloSections = hasSoloSections;
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
        Map<String, Integer> difficulties = new LinkedHashMap<>();
        putDifficulty(difficulties, o, "diff_guitar", "Guitar");
        putDifficulty(difficulties, o, "diff_bass", "Bass");
        putDifficulty(difficulties, o, "diff_drums", "Drums");
        putDifficulty(difficulties, o, "diff_keys", "Keys");
        putDifficulty(difficulties, o, "diff_vocals", "Vocals");
        putDifficulty(difficulties, o, "diff_rhythm", "Rhythm");
        putDifficulty(difficulties, o, "diff_guitarghl", "Guitar (GHL)");
        putDifficulty(difficulties, o, "diff_bassghl", "Bass (GHL)");
        boolean hasLyrics = nd != null && nd.optBoolean("hasLyrics", false);
        boolean hasVocals = nd != null && nd.optBoolean("hasVocals", false);
        boolean hasSoloSections = nd != null && nd.optBoolean("hasSoloSections", false);
        return new Chart(
            o.optString("md5", null), s(o,"name"), s(o,"artist"), s(o,"charter"), s(o,"albumArtMd5"),
            instruments,
            o.optBoolean("hasVideoBackground", false),
            o.optInt("song_length", 0),
            o.optBoolean("pro_drums", false),
            o.optBoolean("modchart", false),
            s(o,"album"), s(o,"genre"), s(o,"year"),
            difficulties, hasLyrics, hasVocals, hasSoloSections
        );
    }
    private static void putDifficulty(Map<String, Integer> difficulties, JSONObject o, String key, String label) {
        int v = o.optInt(key, -1);
        if (v >= 0) difficulties.put(label, v);
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
