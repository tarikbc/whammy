package com.tarikbc.whammy;
public class SongStore {
    public static String sanitizeFilename(Chart c) {
        String artist = clean(c.artist), name = clean(c.name), charter = clean(c.charter);
        String base;
        if (artist.isEmpty() && name.isEmpty()) return c.md5 + ".sng";
        if (artist.isEmpty()) base = name;
        else if (name.isEmpty()) base = artist;
        else base = artist + " - " + name;
        if (!charter.isEmpty()) base = base + " (" + charter + ")";
        if (base.length() > 120) base = base.substring(0, 120).trim();
        return base + ".sng";
    }
    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "")
                .replaceAll("\\s+", " ").trim();
    }
}
