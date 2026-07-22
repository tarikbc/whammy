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

    public static List<Chart> search(String query, int page) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(SEARCH_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "Whammy/1.0");
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(buildSearchBody(query, page, 25).getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readAll(in);
        // Live api.enchor.us/search returns HTTP 201 (not 200) on success as of 2026-07-21
        // (verified via curl -D-); accept any 2xx rather than exactly 200.
        if (code < 200 || code >= 300) throw new IOException("search http " + code + ": " + body);
        return parseSearchResults(body);
    }

    public static void downloadSng(String md5, File dest, ProgressListener cb) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(fileUrl(md5)).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Whammy/1.0");
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
        try {
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
