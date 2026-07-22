package com.tarikbc.whammy;
import org.json.JSONObject;
public class Chart {
    public final String md5, name, artist, charter;
    public Chart(String md5, String name, String artist, String charter) {
        this.md5 = md5; this.name = name; this.artist = artist; this.charter = charter;
    }
    public static Chart fromJson(JSONObject o) {
        return new Chart(o.optString("md5", null), s(o,"name"), s(o,"artist"), s(o,"charter"));
    }
    private static String s(JSONObject o, String k) { return o.isNull(k) ? null : o.optString(k, null); }
}
