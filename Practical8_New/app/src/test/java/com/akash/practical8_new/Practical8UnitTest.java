package com.akash.practical8_new;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.akash.practical8_new.model.LocationModel;

import org.junit.Test;
import org.osmdroid.util.MapTileIndex;

import java.util.Map;

/**
 * Unit tests for Practical 8 models and map layer sources.
 */
public class Practical8UnitTest {

    @Test
    public void testLocationModelCreationAndGetters() {
        LocationModel model = new LocationModel(
                "loc_12345",
                18.67226,
                73.88818,
                4.5f,
                "Alandi, Maharashtra, India",
                1700000000000L,
                "Google Pixel 8"
        );

        assertEquals("loc_12345", model.getId());
        assertEquals(18.67226, model.getLatitude(), 0.0001);
        assertEquals(73.88818, model.getLongitude(), 0.0001);
        assertEquals(4.5f, model.getAccuracy(), 0.01f);
        assertEquals("Alandi, Maharashtra, India", model.getAddress());
        assertEquals(1700000000000L, model.getTimestamp());
        assertEquals("Google Pixel 8", model.getDeviceName());
    }

    @Test
    public void testLocationModelToMapConversion() {
        LocationModel model = new LocationModel(
                "doc_test",
                18.67226,
                73.88818,
                3.2f,
                "Alandi, Pune, Maharashtra",
                1700000050000L,
                "Device"
        );

        Map<String, Object> map = model.toMap();
        assertNotNull(map);
        assertEquals("doc_test", map.get("id"));
        assertEquals(18.67226, (Double) map.get("latitude"), 0.0001);
        assertEquals(73.88818, (Double) map.get("longitude"), 0.0001);
        assertEquals(3.2f, (Float) map.get("accuracy"), 0.01f);
        assertEquals("Alandi, Pune, Maharashtra", map.get("address"));
    }

    @Test
    public void testEsriSatelliteUrlFormatting() {
        long tileIndex = MapTileIndex.getTileIndex(10, 722, 469);
        String url = MainActivity.ESRI_SATELLITE.getTileURLString(tileIndex);
        assertEquals("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/10/469/722", url);
    }

    @Test
    public void testCartoDarkUrlFormatting() {
        long tileIndex = MapTileIndex.getTileIndex(10, 722, 469);
        String url = MainActivity.CARTO_DARK.getTileURLString(tileIndex);
        assertTrue(url.contains("cartocdn.com/dark_all/10/722/469.png"));
    }
}
