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
}
