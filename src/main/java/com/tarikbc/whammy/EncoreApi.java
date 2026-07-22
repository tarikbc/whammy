package com.tarikbc.whammy;
import org.json.JSONObject;
import org.json.JSONArray;
public class EncoreApi {
    public static String buildSearchBody(String query, int page, int perPage) {
        JSONObject b = new JSONObject();
        b.put("search", query == null ? "" : query);
        b.put("per_page", perPage);
        b.put("page", page);
        b.put("instrument", JSONObject.NULL);
        b.put("difficulty", JSONObject.NULL);
        b.put("drumType", JSONObject.NULL);
        b.put("drumsReviewed", true);
        b.put("sort", JSONObject.NULL);
        b.put("source", "bridge");
        return b.toString();
    }
    public static java.util.List<Chart> parseSearchResults(String json) {
        java.util.List<Chart> out = new java.util.ArrayList<>();
        JSONArray data = new JSONObject(json).optJSONArray("data");
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            Chart c = Chart.fromJson(data.getJSONObject(i));
            if (c.md5 != null && c.md5.matches("[a-fA-F0-9]{32}")) out.add(c);
        }
        return out;
    }
}
