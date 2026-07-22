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
    public static java.io.File songsDir() {
        java.io.File d = new java.io.File("/sdcard/Documents/Clone Hero/Songs");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    public static java.io.File place(java.io.File tempSng, Chart c) throws java.io.IOException {
        java.io.File dest = new java.io.File(songsDir(), sanitizeFilename(c));
        if (dest.exists()) dest.delete();
        if (!tempSng.renameTo(dest)) {
            try (java.io.InputStream i=new java.io.FileInputStream(tempSng);
                 java.io.OutputStream o=new java.io.FileOutputStream(dest)) {
                byte[] b=new byte[8192]; int n; while((n=i.read(b))!=-1) o.write(b,0,n);
            }
            tempSng.delete();
        }
        return dest;
    }
}
