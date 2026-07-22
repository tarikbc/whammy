package com.tarikbc.whammy;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;

class EncoreApiTest {
    @Test void searchBody_hasRequiredFields() {
        JSONObject b = new JSONObject(EncoreApi.buildSearchBody("dire straits", 1, 25));
        assertEquals("dire straits", b.getString("search"));
        assertEquals(1, b.getInt("page"));
        assertEquals(25, b.getInt("per_page"));
        assertTrue(b.getBoolean("drumsReviewed"));
        assertTrue(b.isNull("instrument"));
        assertTrue(b.isNull("difficulty"));
        assertTrue(b.isNull("drumType"));
        assertTrue(b.isNull("sort"));
        assertEquals("bridge", b.getString("source"));
    }
    @Test void searchBody_fourArg_defaultsInstrumentNullWhenOmitted() {
        // 3-arg overload must keep delegating with instrument=null so
        // existing callers/tests compile and behave unchanged.
        JSONObject b = new JSONObject(EncoreApi.buildSearchBody("dire straits", 1, 25));
        assertTrue(b.isNull("instrument"));
    }
    @Test void searchBody_instrumentGoesIntoBody() {
        JSONObject b = new JSONObject(EncoreApi.buildSearchBody("metallica", 1, 25, "drums"));
        assertEquals("drums", b.getString("instrument"));
    }
    @Test void searchBody_nullInstrumentStaysJsonNull() {
        JSONObject b = new JSONObject(EncoreApi.buildSearchBody("metallica", 1, 25, null));
        assertTrue(b.isNull("instrument"));
    }
    @Test void chart_fromJson_readsFieldsAndToleratesNulls() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"Sultans of Swing\",\"artist\":\"Dire Straits\",\"charter\":\"Harmonix\",\"album\":null}");
        Chart c = Chart.fromJson(o);
        assertEquals("a".repeat(32), c.md5);
        assertEquals("Sultans of Swing", c.name);
        assertEquals("Dire Straits", c.artist);
        assertEquals("Harmonix", c.charter);
    }
    @Test void chart_fromJson_readsAlbumArtMd5() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"Sultans of Swing\",\"artist\":\"Dire Straits\",\"charter\":\"Harmonix\",\"albumArtMd5\":\""+"e".repeat(32)+"\"}");
        Chart c = Chart.fromJson(o);
        assertEquals("e".repeat(32), c.albumArtMd5);
    }
    @Test void chart_fromJson_missingAlbumArtMd5IsNull() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"Sultans of Swing\",\"artist\":\"Dire Straits\",\"charter\":\"Harmonix\"}");
        Chart c = Chart.fromJson(o);
        assertNull(c.albumArtMd5);
    }
    @Test void artUrl_buildsCoverImageUrl() {
        assertEquals("https://files.enchor.us/abc123.jpg", EncoreApi.artUrl("abc123"));
    }
    @Test void chart_fromJson_nullOwnFieldsBecomeJavaNull() {
        JSONObject o = new JSONObject("{\"md5\":\""+"c".repeat(32)+"\",\"name\":null,\"artist\":null}");
        Chart c = Chart.fromJson(o);
        assertEquals("c".repeat(32), c.md5);
        assertNull(c.name);
        assertNull(c.artist);
        assertNull(c.charter);
    }
    @Test void parse_returnsChartsAndSkipsBadRows() {
        String json = "{\"found\":2,\"data\":[" +
          "{\"md5\":\""+"b".repeat(32)+"\",\"name\":\"N1\",\"artist\":\"A1\",\"charter\":\"C1\"}," +
          "{\"name\":\"no md5\"}]}";
        java.util.List<Chart> r = EncoreApi.parseSearchResults(json);
        assertEquals(1, r.size());
        assertEquals("N1", r.get(0).name);
    }
    @Test void chart_fromJson_readsMultiInstrumentNotesData() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"notesData\":{\"instruments\":[\"guitar\",\"bass\",\"drums\"]}}");
        Chart c = Chart.fromJson(o);
        assertEquals(java.util.List.of("guitar","bass","drums"), c.instruments);
    }
    @Test void chart_fromJson_missingNotesDataYieldsEmptyInstruments() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertNotNull(c.instruments);
        assertTrue(c.instruments.isEmpty());
    }
    @Test void chart_fromJson_notesDataWithoutInstrumentsYieldsEmptyInstruments() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\",\"notesData\":{}}");
        Chart c = Chart.fromJson(o);
        assertNotNull(c.instruments);
        assertTrue(c.instruments.isEmpty());
    }
    @Test void chart_fromJson_readsVideoDrumsModchartTrue() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"hasVideoBackground\":true,\"pro_drums\":true,\"modchart\":true}");
        Chart c = Chart.fromJson(o);
        assertTrue(c.hasVideoBackground);
        assertTrue(c.proDrums);
        assertTrue(c.modchart);
    }
    @Test void chart_fromJson_defaultsVideoDrumsModchartFalseWhenAbsent() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertFalse(c.hasVideoBackground);
        assertFalse(c.proDrums);
        assertFalse(c.modchart);
    }
    @Test void chart_fromJson_readsSongLengthMs() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\",\"song_length\":336355}");
        Chart c = Chart.fromJson(o);
        assertEquals(336355, c.songLengthMs);
    }
    @Test void chart_fromJson_missingSongLengthDefaultsZero() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertEquals(0, c.songLengthMs);
    }
    @Test void formatDuration_variousValues() {
        assertEquals("5:36", Chart.formatDuration(336355));
        assertEquals("1:05", Chart.formatDuration(65000));
        assertEquals("0:05", Chart.formatDuration(5000));
        assertEquals("", Chart.formatDuration(0));
        assertEquals("60:00", Chart.formatDuration(3600000));
    }
    @Test void chart_fromJson_readsAlbumGenreYear() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"album\":\"Making Movies\",\"genre\":\"Rock\",\"year\":\"2020\"}");
        Chart c = Chart.fromJson(o);
        assertEquals("Making Movies", c.album);
        assertEquals("Rock", c.genre);
        assertEquals("2020", c.year);
    }
    @Test void chart_fromJson_missingAlbumGenreYearAreNull() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertNull(c.album);
        assertNull(c.genre);
        assertNull(c.year);
    }
    @Test void chart_fromJson_difficultiesIncludesOnlyNonNegativeInOrder() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"diff_guitar\":4,\"diff_bass\":-1,\"diff_drums\":2}");
        Chart c = Chart.fromJson(o);
        assertEquals(java.util.List.of("Guitar","Drums"), new java.util.ArrayList<>(c.difficulties.keySet()));
        assertEquals(Integer.valueOf(4), c.difficulties.get("Guitar"));
        assertEquals(Integer.valueOf(2), c.difficulties.get("Drums"));
        assertFalse(c.difficulties.containsKey("Bass"));
    }
    @Test void chart_fromJson_difficultiesAllInstrumentsMapped() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"diff_guitar\":1,\"diff_bass\":2,\"diff_drums\":3,\"diff_keys\":4,\"diff_vocals\":5,\"diff_rhythm\":6,\"diff_guitarghl\":0,\"diff_bassghl\":1}");
        Chart c = Chart.fromJson(o);
        assertEquals(java.util.List.of("Guitar","Bass","Drums","Keys","Vocals","Rhythm","Guitar (GHL)","Bass (GHL)"),
            new java.util.ArrayList<>(c.difficulties.keySet()));
    }
    @Test void chart_fromJson_missingDifficultiesYieldsEmptyMap() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertNotNull(c.difficulties);
        assertTrue(c.difficulties.isEmpty());
    }
    @Test void chart_fromJson_readsLyricsVocalsSoloFromNotesData() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"," +
          "\"notesData\":{\"hasLyrics\":true,\"hasVocals\":true,\"hasSoloSections\":true}}");
        Chart c = Chart.fromJson(o);
        assertTrue(c.hasLyrics);
        assertTrue(c.hasVocals);
        assertTrue(c.hasSoloSections);
    }
    @Test void chart_fromJson_missingNotesDataDefaultsLyricsVocalsSoloFalse() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"N\",\"artist\":\"A\",\"charter\":\"C\"}");
        Chart c = Chart.fromJson(o);
        assertFalse(c.hasLyrics);
        assertFalse(c.hasVocals);
        assertFalse(c.hasSoloSections);
    }
    @Test void chart_tenArgConstructor_defaultsNewDetailFields() {
        Chart c = new Chart("d".repeat(32), "N", "A", "C", null,
            java.util.Collections.emptyList(), false, 0, false, false);
        assertNull(c.album);
        assertNull(c.genre);
        assertNull(c.year);
        assertNotNull(c.difficulties);
        assertTrue(c.difficulties.isEmpty());
        assertFalse(c.hasLyrics);
        assertFalse(c.hasVocals);
        assertFalse(c.hasSoloSections);
        assertNull(c.loadingPhrase);
        assertFalse(c.isPack);
        assertFalse(c.isSetlist());
    }

    @Test void chart_fromJson_readsLoadingPhraseAndPackFlag() {
        JSONObject o = new JSONObject();
        o.put("name", "Some Song");
        o.put("loading_phrase", "Have fun with this one!");
        o.put("driveChartIsPack", true);
        Chart c = Chart.fromJson(o);
        assertEquals("Have fun with this one!", c.loadingPhrase);
        assertTrue(c.isPack);
        assertTrue(c.isSetlist());   // isPack ⇒ setlist
    }

    @Test void chart_isSetlist_detectsByNameWhenNotFlaggedPack() {
        JSONObject o = new JSONObject();
        o.put("name", "Children of Bodom Endless Setlist");
        Chart c = Chart.fromJson(o);
        assertFalse(c.isPack);
        assertTrue(c.isSetlist());   // name contains "setlist"

        JSONObject o2 = new JSONObject();
        o2.put("name", "Sultans of Swing");
        assertFalse(Chart.fromJson(o2).isSetlist());
    }
}
