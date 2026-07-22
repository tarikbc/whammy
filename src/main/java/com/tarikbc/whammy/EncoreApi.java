package com.tarikbc.whammy;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.util.List;
public class EncoreApi {
    public interface ProgressListener { void onProgress(int percent); }

    public static final String SEARCH_URL = "https://api.enchor.us/search";
    public static String fileUrl(String md5) { return "https://files.enchor.us/" + md5 + ".sng"; }
    // Callers must check albumArtMd5 != null before calling (field may be absent from search results).
    public static String artUrl(String albumArtMd5) { return "https://files.enchor.us/" + albumArtMd5 + ".jpg"; }

    /**
     * Immutable bundle of every server-side search refinement beyond the
     * raw query text (DESIGN.md §7.11 + task-search-screen-features):
     * instrument, difficulty tier, and sort. Threading these through a
     * single small value type (rather than a growing pile of positional
     * String/String/String parameters) keeps {@link #search} / {@link
     * #buildSearchBody} readable as more filters land.
     *
     * <p>{@code sortType} is one of "name"/"artist"/"length" (or null for
     * relevance — the API's default, omitted/null sort); {@code
     * sortDirection} is "asc"/"desc" and is only meaningful when {@code
     * sortType} is set. {@code difficulty} is one of "easy"/"medium"/
     * "hard"/"expert" (a tier string, NOT a number — the live API errors
     * on numeric difficulty) or null for no difficulty filter.
     */
    public static final class SearchParams {
        /** No instrument/difficulty/sort filter — plain relevance search. */
        public static final SearchParams NONE = new SearchParams(null, null, null, null);

        public final String instrument;
        public final String difficulty;
        public final String sortType;
        public final String sortDirection;

        public SearchParams(String instrument, String difficulty, String sortType, String sortDirection) {
            this.instrument = instrument;
            this.difficulty = difficulty;
            this.sortType = sortType;
            this.sortDirection = sortDirection;
        }
    }

    public static List<Chart> search(String query, int page) throws IOException {
        return search(query, page, SearchParams.NONE);
    }

    /**
     * Same as {@link #search(String, int)} but narrows results server-side
     * to a single instrument (DESIGN.md §7.11) — one of "guitar", "bass",
     * "drums", "keys", "vocals" (lowercase), or null for no filter. Verified
     * live: setting instrument narrows the result count (e.g. metallica:
     * null→506, "drums"→9).
     */
    public static List<Chart> search(String query, int page, String instrument) throws IOException {
        return search(query, page, new SearchParams(instrument, null, null, null));
    }

    /**
     * Full-featured search: instrument + difficulty + sort, all via
     * {@link SearchParams} (task-search-screen-features). {@code params}
     * may be null, treated the same as {@link SearchParams#NONE}.
     */
    public static List<Chart> search(String query, int page, SearchParams params) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(SEARCH_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "Whammy/1.0");
        c.setDoOutput(true);
        try {
            try (OutputStream os = c.getOutputStream()) {
                os.write(buildSearchBody(query, page, 25, params).getBytes("UTF-8"));
            }
            int code = c.getResponseCode();
            String body;
            try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
                body = readAll(in);
            }
            // Live api.enchor.us/search returns HTTP 201 (not 200) on success as of 2026-07-21
            // (verified via curl -D-); accept any 2xx rather than exactly 200.
            if (code < 200 || code >= 300) throw new IOException("search http " + code + ": " + body);
            return parseSearchResults(body);
        } finally {
            c.disconnect();
        }
    }

    /**
     * HEAD request for the .sng's byte size (the "how heavy" indicator on
     * the detail screen) — never throws, returns -1 on any failure/unknown
     * length so callers can just omit the size rather than handle IOException.
     */
    public static long contentLength(String md5) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(fileUrl(md5)).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Whammy/1.0");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return -1;
            return c.getContentLengthLong();
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static void downloadSng(String md5, File dest, ProgressListener cb) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(fileUrl(md5)).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Whammy/1.0");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("download http " + code);
            int total = c.getContentLength();
            File part = new File(dest.getPath() + ".part");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[8192]; int n; long got = 0;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n); got += n;
                    if (cb != null) cb.onProgress(total > 0 ? (int)(got * 100 / total) : -1);
                }
            }
            if (!part.renameTo(dest)) { copy(part, dest); part.delete(); }
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }
    private static void copy(File a, File b) throws IOException {
        try (InputStream i=new FileInputStream(a); OutputStream o=new FileOutputStream(b)) {
            byte[] buf=new byte[8192]; int n; while((n=i.read(buf))!=-1) o.write(buf,0,n);
        }
    }

    public static String buildSearchBody(String query, int page, int perPage) {
        return buildSearchBody(query, page, perPage, SearchParams.NONE);
    }

    /** @param instrument lowercase instrument name ("guitar"/"bass"/"drums"/
     *  "keys"/"vocals") to filter server-side, or null for no filter
     *  (DESIGN.md §7.11). */
    public static String buildSearchBody(String query, int page, int perPage, String instrument) {
        return buildSearchBody(query, page, perPage, new SearchParams(instrument, null, null, null));
    }

    /**
     * Full-featured search body (task-search-screen-features): instrument,
     * difficulty tier, and sort, via {@link SearchParams} (null treated as
     * {@link SearchParams#NONE}). {@code sort} is emitted as the object
     * shape the live API expects — {@code {"type":..,"direction":..}} —
     * or {@code null} for relevance when {@code sortType} is unset;
     * {@code difficulty} is emitted as its raw tier string, or {@code null}
     * when unset (verified live: numeric difficulty errors out, the tier
     * string does not).
     */
    public static String buildSearchBody(String query, int page, int perPage, SearchParams params) {
        SearchParams p = params == null ? SearchParams.NONE : params;
        try {
            JSONObject b = new JSONObject();
            b.put("search", query == null ? "" : query);
            b.put("per_page", perPage);
            b.put("page", page);
            b.put("instrument", p.instrument == null ? JSONObject.NULL : p.instrument);
            b.put("difficulty", p.difficulty == null ? JSONObject.NULL : p.difficulty);
            b.put("drumType", JSONObject.NULL);
            b.put("drumsReviewed", true);
            if (p.sortType == null) {
                b.put("sort", JSONObject.NULL);
            } else {
                JSONObject sort = new JSONObject();
                sort.put("type", p.sortType);
                sort.put("direction", p.sortDirection == null ? "asc" : p.sortDirection);
                b.put("sort", sort);
            }
            b.put("source", "bridge");
            return b.toString();
        } catch (org.json.JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public static java.util.List<Chart> parseSearchResults(String json) {
        try {
            java.util.List<Chart> out = new java.util.ArrayList<>();
            JSONArray data = new JSONObject(json).optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                Chart c = Chart.fromJson(data.getJSONObject(i));
                if (c.md5 != null && c.md5.matches("[a-fA-F0-9]{32}")) out.add(c);
            }
            return out;
        } catch (org.json.JSONException e) {
            throw new RuntimeException("invalid search response", e);
        }
    }
}
