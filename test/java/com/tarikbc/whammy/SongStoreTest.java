package com.tarikbc.whammy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SongStoreTest {
    private Chart c(String a,String n,String ch){ return new Chart("d".repeat(32),n,a,ch); }
    @Test void normal() { assertEquals("Dire Straits - Sultans of Swing (Harmonix).sng",
        SongStore.sanitizeFilename(c("Dire Straits","Sultans of Swing","Harmonix"))); }
    @Test void stripsIllegalChars() { assertEquals("ACDC - TNT (X).sng",
        SongStore.sanitizeFilename(c("AC/DC","TN:T","X"))); }
    @Test void nullCharterOmitsParen() { assertEquals("A - B.sng",
        SongStore.sanitizeFilename(c("A","B",null))); }
    @Test void allNullFallsBackToMd5() { assertEquals("d".repeat(32)+".sng",
        SongStore.sanitizeFilename(c(null,null,null))); }
}
