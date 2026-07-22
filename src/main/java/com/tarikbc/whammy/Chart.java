package com.tarikbc.whammy;
import org.json.JSONObject;
public class Chart {
    public final String md5, name, artist, charter, albumArtMd5;
    public Chart(String md5, String name, String artist, String charter, String albumArtMd5) {
        this.md5 = md5; this.name = name; this.artist = artist; this.charter = charter; this.albumArtMd5 = albumArtMd5;
    }
    public static Chart fromJson(JSONObject o) {
        return new Chart(o.optString("md5", null), s(o,"name"), s(o,"artist"), s(o,"charter"), s(o,"albumArtMd5"));
    }
    private static String s(JSONObject o, String k) { return o.isNull(k) ? null : o.optString(k, null); }
}
