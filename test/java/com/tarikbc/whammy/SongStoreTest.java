package com.tarikbc.whammy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
class SongStoreTest {
    private Chart c(String a,String n,String ch){ return new Chart("d".repeat(32),n,a,ch,null,java.util.Collections.emptyList(),false,0,false,false); }
    @Test void normal() { assertEquals("Dire Straits - Sultans of Swing (Harmonix).sng",
        SongStore.sanitizeFilename(c("Dire Straits","Sultans of Swing","Harmonix"))); }
    @Test void stripsIllegalChars() { assertEquals("ACDC - TNT (X).sng",
        SongStore.sanitizeFilename(c("AC/DC","TN:T","X"))); }
    @Test void nullCharterOmitsParen() { assertEquals("A - B.sng",
        SongStore.sanitizeFilename(c("A","B",null))); }
    @Test void allNullFallsBackToMd5() { assertEquals("d".repeat(32)+".sng",
        SongStore.sanitizeFilename(c(null,null,null))); }

    // -- list()/delete() (DESIGN.md §7.12 Library screen) --------------
    // Always a fresh JUnit temp dir (Files.createTempDirectory), never
    // the real /sdcard path -- SongStore.list/delete are pure java.io.

    private static void writeBytes(File f, int n) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(new byte[n]);
        }
    }

    @Test void listReturnsSngFilesAndFoldersSortedByName() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            writeBytes(new File(dir, "Beta Song.sng"), 100);
            writeBytes(new File(dir, "Alpha Song.sng"), 250);
            File folder = new File(dir, "Zeta Folder");
            assertTrue(folder.mkdir());
            writeBytes(new File(folder, "notes.chart"), 40);
            writeBytes(new File(folder, "song.ogg"), 60);
            // A non-.sng loose file must never surface as its own item.
            writeBytes(new File(dir, "readme.txt"), 5);

            List<SongStore.LibraryItem> items = SongStore.list(dir);
            assertEquals(3, items.size());

            assertEquals("Alpha Song", items.get(0).name);
            assertFalse(items.get(0).isDir);
            assertEquals(250, items.get(0).sizeBytes);

            assertEquals("Beta Song", items.get(1).name);
            assertFalse(items.get(1).isDir);
            assertEquals(100, items.get(1).sizeBytes);

            assertEquals("Zeta Folder", items.get(2).name);
            assertTrue(items.get(2).isDir);
            assertEquals(100, items.get(2).sizeBytes); // 40 + 60, recursive
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void listNeverThrowsOnMissingOrNullDir() {
        assertTrue(SongStore.list((File) null).isEmpty());
        assertTrue(SongStore.list(new File("/no/such/whammy-dir-xyz")).isEmpty());
    }

    @Test void folderItemsCarryTheirAlbumArt() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            File withArt = new File(dir, "Has Art");
            assertTrue(withArt.mkdir());
            writeBytes(new File(withArt, "notes.chart"), 10);
            File art = new File(withArt, "album.jpg");
            writeBytes(art, 20);

            File noArt = new File(dir, "No Art");
            assertTrue(noArt.mkdir());
            writeBytes(new File(noArt, "notes.chart"), 10);

            List<SongStore.LibraryItem> items = SongStore.list(dir);
            assertEquals(2, items.size());
            assertEquals(art, items.get(0).albumArt);   // "Has Art" sorts first
            assertNull(items.get(1).albumArt);           // "No Art"

            // Case-insensitive detection, png/jpeg too.
            assertEquals(art, SongStore.findAlbumArt(withArt));
            assertNull(SongStore.findAlbumArt(noArt));
            assertNull(SongStore.findAlbumArt(null));
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void dirSizeIsRecursive() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            writeBytes(new File(dir, "a.txt"), 30);
            File nested = new File(dir, "nested");
            assertTrue(nested.mkdir());
            writeBytes(new File(nested, "b.txt"), 20);
            assertEquals(50, SongStore.dirSize(dir));
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void deleteRemovesAFile() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            File f = new File(dir, "solo.sng");
            writeBytes(f, 10);
            assertTrue(f.exists());
            assertTrue(SongStore.delete(f));
            assertFalse(f.exists());
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void deleteRecursivelyRemovesAFolder() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            File folder = new File(dir, "chart-folder");
            assertTrue(folder.mkdir());
            writeBytes(new File(folder, "a.txt"), 5);
            File nested = new File(folder, "nested");
            assertTrue(nested.mkdir());
            writeBytes(new File(nested, "b.txt"), 5);

            assertTrue(SongStore.delete(folder));
            assertFalse(folder.exists());
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void deleteGuardsNullAndNonexistent() {
        assertTrue(SongStore.delete(null));
        assertTrue(SongStore.delete(new File("/no/such/whammy-file-xyz")));
    }

    // -- existingChartKeys()/keyFor() (task-search-screen-features: "already in your library") --

    @Test void existingChartKeysIncludesSngBasenamesAndFolderNamesLowercased() throws IOException {
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            writeBytes(new File(dir, "Dire Straits - Sultans Of Swing (Harmonix).sng"), 10);
            File folder = new File(dir, "AC-DC - TNT (Someone)");
            assertTrue(folder.mkdir());
            writeBytes(new File(folder, "notes.chart"), 5);
            // A non-.sng loose file must never contribute a key.
            writeBytes(new File(dir, "readme.txt"), 5);

            java.util.Set<String> keys = SongStore.existingChartKeys(dir);
            assertEquals(2, keys.size());
            assertTrue(keys.contains("dire straits - sultans of swing (harmonix)"));
            assertTrue(keys.contains("ac-dc - tnt (someone)"));
        } finally {
            SongStore.delete(dir);
        }
    }

    @Test void existingChartKeysEmptyOnMissingOrNullDir() {
        assertTrue(SongStore.existingChartKeys((File) null).isEmpty());
        assertTrue(SongStore.existingChartKeys(new File("/no/such/whammy-dir-xyz")).isEmpty());
    }

    @Test void keyForMatchesSanitizeFilenameMinusExtensionLowercased() {
        Chart chart = c("Dire Straits", "Sultans Of Swing", "Harmonix");
        assertEquals("dire straits - sultans of swing (harmonix)", SongStore.keyFor(chart));
        assertEquals(SongStore.sanitizeFilename(chart).toLowerCase(java.util.Locale.ROOT),
            SongStore.keyFor(chart) + ".sng");
    }

    @Test void keyForMatchesExistingChartKeysForAWhammyDownloadedFile() throws IOException {
        // A chart Whammy itself downloaded lands on disk under exactly
        // sanitizeFilename(chart) -- keyFor(chart) must line up with what
        // existingChartKeys() derives from that same file so a re-search
        // recognizes it as already downloaded.
        File dir = Files.createTempDirectory("whammy-lib-test").toFile();
        try {
            Chart chart = c("AC/DC", "TN:T", "X");
            writeBytes(new File(dir, SongStore.sanitizeFilename(chart)), 10);
            assertTrue(SongStore.existingChartKeys(dir).contains(SongStore.keyFor(chart)));
        } finally {
            SongStore.delete(dir);
        }
    }
}
