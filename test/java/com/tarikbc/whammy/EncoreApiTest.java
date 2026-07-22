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
        assertEquals("bridge", b.getString("source"));
    }
    @Test void chart_fromJson_readsFieldsAndTolueratesNulls() {
        JSONObject o = new JSONObject("{\"md5\":\""+"a".repeat(32)+"\",\"name\":\"Sultans of Swing\",\"artist\":\"Dire Straits\",\"charter\":\"Harmonix\",\"album\":null}");
        Chart c = Chart.fromJson(o);
        assertEquals("a".repeat(32), c.md5);
        assertEquals("Sultans of Swing", c.name);
        assertEquals("Dire Straits", c.artist);
        assertEquals("Harmonix", c.charter);
    }
}
