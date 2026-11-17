package com.akash.quakewatch;

import com.akash.quakewatch.util.GeoUtils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for GeoUtils Haversine formula and distance formatting.
 */
public class GeoUtilsTest {

    @Test
    public void testDistanceKmSamePoint() {
        double dist = GeoUtils.distanceKm(19.0760, 72.8777, 19.0760, 72.8777);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    public void testDistanceKmMumbaiToPune() {
        // Mumbai (19.0760 N, 72.8777 E) to Pune (18.5204 N, 73.8567 E) ~ 120 km
        double dist = GeoUtils.distanceKm(19.0760, 72.8777, 18.5204, 73.8567);
        assertTrue("Distance should be between 110 and 130 km", dist > 110.0 && dist < 130.0);
    }

    @Test
    public void testFormatDistance() {
        String formatted = GeoUtils.formatDistance(123.4);
        assertEquals("123 km from you", formatted);
    }
}
