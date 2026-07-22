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

    /**
     * One entry in the Library screen (DESIGN.md §7.12): either a
     * single {@code .sng} chart file (name = filename with the
     * extension stripped) or a chart subfolder (name = the folder's own
     * name, size = its recursive total). Pure data holder -- no
     * android.* dependency, so it stays usable from the JVM test suite.
     */
    public static final class LibraryItem {
        public final java.io.File file;
        public final String name;
        public final long sizeBytes;
        public final boolean isDir;
        /** Local album-art image inside a chart folder (album.png/jpg/jpeg), or null. */
        public final java.io.File albumArt;

        public LibraryItem(java.io.File file, String name, long sizeBytes, boolean isDir, java.io.File albumArt) {
            this.file = file;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.isDir = isDir;
            this.albumArt = albumArt;
        }
    }

    /** The album-art image in a chart folder: album.png/jpg/jpeg (case-insensitive), else null. */
    public static java.io.File findAlbumArt(java.io.File chartDir) {
        if (chartDir == null || !chartDir.isDirectory()) return null;
        java.io.File[] kids = chartDir.listFiles();
        if (kids == null) return null;
        for (java.io.File k : kids) {
            String n = k.getName().toLowerCase(java.util.Locale.ROOT);
            if (k.isFile() && (n.equals("album.png") || n.equals("album.jpg") || n.equals("album.jpeg"))) {
                return k;
            }
        }
        return null;
    }

    /**
     * Enumerates {@code dir} for the Library screen: every {@code *.sng}
     * file becomes an item (name with the extension stripped), every
     * subdirectory becomes an item (name = its own name, size =
     * {@link #dirSize}). Sorted by name, case-insensitive. Never
     * throws -- a null/missing/non-directory {@code dir} (or a
     * {@code listFiles()} I/O hiccup) simply yields an empty list.
     */
    public static java.util.List<LibraryItem> list(java.io.File dir) {
        java.util.List<LibraryItem> out = new java.util.ArrayList<>();
        if (dir == null || !dir.isDirectory()) return out;
        java.io.File[] files = dir.listFiles();
        if (files == null) return out;
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                out.add(new LibraryItem(f, f.getName(), dirSize(f), true, findAlbumArt(f)));
            } else if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".sng")) {
                String n = f.getName();
                // .sng bundles art inside the SNGPKG container (not a loose file); no cheap cover yet.
                out.add(new LibraryItem(f, n.substring(0, n.length() - 4), f.length(), false, null));
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** {@link #list(java.io.File)} over the real Clone Hero songs folder. */
    public static java.util.List<LibraryItem> list() {
        return list(songsDir());
    }

    /** Recursive byte size: a file's own length, or a directory's children summed. */
    public static long dirSize(java.io.File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        java.io.File[] children = f.listFiles();
        if (children != null) {
            for (java.io.File c : children) total += dirSize(c);
        }
        return total;
    }

    /**
     * Deletes a file, or recursively deletes a directory and everything
     * under it. Returns whether {@code f} is gone afterward (so a
     * null/already-nonexistent {@code f} returns true -- there's
     * nothing left to delete).
     */
    public static boolean delete(java.io.File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            java.io.File[] children = f.listFiles();
            if (children != null) {
                for (java.io.File c : children) delete(c);
            }
        }
        f.delete();
        return !f.exists();
    }
}
