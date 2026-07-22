package com.tarikbc.whammy;
import org.json.JSONObject;
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
}
