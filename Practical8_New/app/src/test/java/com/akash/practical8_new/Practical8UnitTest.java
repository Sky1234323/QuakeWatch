package com.akash.practical8_new;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.akash.practical8_new.model.LocationModel;

import org.junit.Test;

import java.util.Map;

/**
 * Unit tests for Practical 8 models and location data structures.
 */
public class Practical8UnitTest {

    @Test
    public void testLocationModelCreationAndGetters() {
        LocationModel model = new LocationModel(
                "loc_12345",
                16.8524,
                74.5815,
                4.5f,
                "Sangli, Maharashtra, India",
                1700000000000L,
                "Google Pixel 8"
        );

        assertEquals("loc_12345", model.getId());
        assertEquals(16.8524, model.getLatitude(), 0.0001);
        assertEquals(74.5815, model.getLongitude(), 0.0001);
        assertEquals(4.5f, model.getAccuracy(), 0.01f);
        assertEquals("Sangli, Maharashtra, India", model.getAddress());
        assertEquals(1700000000000L, model.getTimestamp());
        assertEquals("Google Pixel 8", model.getDeviceName());
    }

    @Test
    public void testLocationModelToMapConversion() {
        LocationModel model = new LocationModel(
                "doc_test",
                18.5204,
                73.8567,
                3.2f,
                "Pune, Maharashtra",
                1700000050000L,
                "Emulator"
        );

        Map<String, Object> map = model.toMap();
        assertNotNull(map);
        assertEquals("doc_test", map.get("id"));
        assertEquals(18.5204, (Double) map.get("latitude"), 0.0001);
        assertEquals(73.8567, (Double) map.get("longitude"), 0.0001);
        assertEquals(3.2f, (Float) map.get("accuracy"), 0.01f);
        assertEquals("Pune, Maharashtra", map.get("address"));
        assertEquals(1700000050000L, map.get("timestamp"));
        assertEquals("Emulator", map.get("deviceName"));
    }

    @Test
    public void testLocationModelNullHandling() {
        LocationModel model = new LocationModel(
                "doc_null",
                0.0,
                0.0,
                0.0f,
                null,
                System.currentTimeMillis(),
                null
        );

        assertNotNull(model.getAddress());
        assertEquals("Unknown Location", model.getAddress());
        assertNotNull(model.getDeviceName());
        assertEquals("Android Device", model.getDeviceName());
    }

    @Test
    public void testCoordinatesBoundaryCheck() {
        double lat = 16.8524;
        double lng = 74.5815;

        assertTrue("Latitude must be between -90 and 90", lat >= -90.0 && lat <= 90.0);
        assertTrue("Longitude must be between -180 and 180", lng >= -180.0 && lng <= 180.0);
    }
}
