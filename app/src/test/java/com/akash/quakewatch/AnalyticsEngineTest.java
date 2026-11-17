package com.akash.quakewatch;

import com.akash.quakewatch.analytics.AnalyticsEngine;
import com.akash.quakewatch.data.model.Quake;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for AnalyticsEngine covering zero-filled days, magnitude bands,
 * focal depth classification, trend thresholds, and nearest quake calculation.
 */
public class AnalyticsEngineTest {

    private Quake createQuake(String id, double mag, double depthKm, double lat, double lon, long timeMs) {
        return new Quake(id, "Test Place " + id, mag, "mb", depthKm, lat, lon, timeMs);
    }

    @Test
    public void testEmptyList() {
        long now = 1700000000000L;
        AnalyticsEngine.AnalysisResult res = AnalyticsEngine.analyze(Collections.emptyList(), 7, now);

        assertEquals(0, res.total);
        assertEquals(0, res.todayCount);
        assertNull(res.strongest);
        assertEquals(0.0, res.avgMagnitude, 0.001);
        assertEquals(0.0, res.avgDepthKm, 0.001);
        assertEquals(7, res.perDay.length);
        assertEquals(7, res.dayLabels.length);
        for (int count : res.perDay) {
            assertEquals(0, count);
        }
        assertEquals(AnalyticsEngine.TrendDirection.STABLE, res.trendDirection);
        assertNotNull(res.summarySentence);
        assertTrue(res.summarySentence.contains("No earthquakes recorded"));
    }

    @Test
    public void testZeroFilledDailyCountsAndMovingAverage() {
        // Fix reference 'now' at 12:00:00 UTC on a known day
        long dayMs = 86400000L;
        // e.g. Day 6 is today, Day 5 is yesterday, Day 0 is 6 days ago (7 days total: index 0 to 6)
        long now = 1700000000000L;

        // Day boundaries in AnalyticsEngine are based on local Calendar midnight.
        // Let's create quakes at exact offsets from 'now'
        List<Quake> quakes = new ArrayList<>();
        // Two quakes on day 6 (today)
        quakes.add(createQuake("q1", 3.5, 10.0, 10.0, 20.0, now - 1000L));
        quakes.add(createQuake("q2", 4.2, 20.0, 10.0, 20.0, now - 5000L));
        // One quake 2 days ago
        quakes.add(createQuake("q3", 5.0, 50.0, 10.0, 20.0, now - (2L * dayMs)));

        AnalyticsEngine.AnalysisResult res = AnalyticsEngine.analyze(quakes, 7, now);

        assertEquals(3, res.total);
        assertEquals(7, res.perDay.length);
        assertEquals(7, res.dayLabels.length);
        assertEquals(7, res.movingAvg3.length);

        // Sum of perDay counts should match total
        int sumCounts = 0;
        for (int c : res.perDay) {
            sumCounts += c;
        }
        assertEquals(3, sumCounts);

        // Strongest should be q3 with mag 5.0
        assertNotNull(res.strongest);
        assertEquals("q3", res.strongest.getId());
        assertEquals(5.0, res.strongest.getMagnitude(), 0.001);
    }

    @Test
    public void testMagnitudeBands() {
        long now = 1700000000000L;
        List<Quake> quakes = new ArrayList<>();
        quakes.add(createQuake("b0", 2.4, 10, 0, 0, now - 1000)); // < 3.0
        quakes.add(createQuake("b1", 3.2, 10, 0, 0, now - 2000)); // 3.0 - 3.9
        quakes.add(createQuake("b2_1", 4.0, 10, 0, 0, now - 3000)); // 4.0 - 4.9
        quakes.add(createQuake("b2_2", 4.8, 10, 0, 0, now - 4000)); // 4.0 - 4.9
        quakes.add(createQuake("b3", 5.6, 10, 0, 0, now - 5000)); // 5.0 - 5.9
        quakes.add(createQuake("b4", 6.7, 10, 0, 0, now - 6000)); // >= 6.0

        AnalyticsEngine.AnalysisResult res = AnalyticsEngine.analyze(quakes, 7, now);

        assertEquals(6, res.total);
        assertEquals(1, res.magnitudeBands[0]); // < 3.0
        assertEquals(1, res.magnitudeBands[1]); // 3.0 - 3.9
        assertEquals(2, res.magnitudeBands[2]); // 4.0 - 4.9
        assertEquals(1, res.magnitudeBands[3]); // 5.0 - 5.9
        assertEquals(1, res.magnitudeBands[4]); // >= 6.0
    }

    @Test
    public void testFocalDepthClassification() {
        long now = 1700000000000L;
        List<Quake> quakes = new ArrayList<>();
        quakes.add(createQuake("d1", 4.0, 25.0, 0, 0, now - 1000));  // shallow (< 70 km)
        quakes.add(createQuake("d2", 4.0, 69.9, 0, 0, now - 2000));  // shallow (< 70 km)
        quakes.add(createQuake("d3", 4.0, 70.0, 0, 0, now - 3000));  // intermediate (70 - 300 km)
        quakes.add(createQuake("d4", 4.0, 300.0, 0, 0, now - 4000)); // intermediate (70 - 300 km)
        quakes.add(createQuake("d5", 4.0, 300.1, 0, 0, now - 5000)); // deep (> 300 km)
        quakes.add(createQuake("d6", 4.0, 550.0, 0, 0, now - 6000)); // deep (> 300 km)

        AnalyticsEngine.AnalysisResult res = AnalyticsEngine.analyze(quakes, 7, now);

        assertEquals(2, res.shallowCount);
        assertEquals(2, res.intermediateCount);
        assertEquals(2, res.deepCount);
    }

    @Test
    public void testTrendThresholds() {
        long now = 1700000000000L;
        long dayMs = 86400000L;

        // Test 1: RISING when previous 7 days was 0 and last 7 days has activity
        List<Quake> quakesRisingZeroPrev = new ArrayList<>();
        quakesRisingZeroPrev.add(createQuake("r1", 4.0, 10, 0, 0, now - (2 * dayMs)));
        AnalyticsEngine.AnalysisResult res1 = AnalyticsEngine.analyze(quakesRisingZeroPrev, 7, now);
        assertEquals(AnalyticsEngine.TrendDirection.RISING, res1.trendDirection);
        assertEquals(100.0, res1.trendPercent, 0.001);

        // Test 2: RISING with >= 10% increase (prev = 10, last = 12 -> +20%)
        List<Quake> quakesRising = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            quakesRising.add(createQuake("prev" + i, 4.0, 10, 0, 0, now - (8 * dayMs) - (i * 1000)));
        }
        for (int i = 0; i < 12; i++) {
            quakesRising.add(createQuake("last" + i, 4.0, 10, 0, 0, now - (2 * dayMs) - (i * 1000)));
        }
        AnalyticsEngine.AnalysisResult res2 = AnalyticsEngine.analyze(quakesRising, 7, now);
        assertEquals(AnalyticsEngine.TrendDirection.RISING, res2.trendDirection);
        assertEquals(20.0, res2.trendPercent, 0.001);

        // Test 3: FALLING with <= -10% decrease (prev = 10, last = 8 -> -20%)
        List<Quake> quakesFalling = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            quakesFalling.add(createQuake("prev" + i, 4.0, 10, 0, 0, now - (8 * dayMs) - (i * 1000)));
        }
        for (int i = 0; i < 8; i++) {
            quakesFalling.add(createQuake("last" + i, 4.0, 10, 0, 0, now - (2 * dayMs) - (i * 1000)));
        }
        AnalyticsEngine.AnalysisResult res3 = AnalyticsEngine.analyze(quakesFalling, 7, now);
        assertEquals(AnalyticsEngine.TrendDirection.FALLING, res3.trendDirection);
        assertEquals(-20.0, res3.trendPercent, 0.001);

        // Test 4: STABLE with |change| < 10% (prev = 10, last = 10 -> 0%)
        List<Quake> quakesStable = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            quakesStable.add(createQuake("prev" + i, 4.0, 10, 0, 0, now - (8 * dayMs) - (i * 1000)));
            quakesStable.add(createQuake("last" + i, 4.0, 10, 0, 0, now - (2 * dayMs) - (i * 1000)));
        }
        AnalyticsEngine.AnalysisResult res4 = AnalyticsEngine.analyze(quakesStable, 7, now);
        assertEquals(AnalyticsEngine.TrendDirection.STABLE, res4.trendDirection);
        assertEquals(0.0, res4.trendPercent, 0.001);
    }

    @Test
    public void testNearestQuake() {
        List<Quake> quakes = new ArrayList<>();
        // User at Mumbai (approx 19.0760 N, 72.8777 E)
        double userLat = 19.0760;
        double userLon = 72.8777;

        // Quake 1: New Delhi (28.6139 N, 77.2090 E) ~ 1150 km
        quakes.add(createQuake("delhi", 4.5, 10, 28.6139, 77.2090, 1000L));
        // Quake 2: Pune (18.5204 N, 73.8567 E) ~ 120 km (Nearest)
        quakes.add(createQuake("pune", 3.8, 10, 18.5204, 73.8567, 2000L));
        // Quake 3: Tokyo (35.6762 N, 139.6503 E) ~ 6700 km
        quakes.add(createQuake("tokyo", 6.2, 30, 35.6762, 139.6503, 3000L));

        Quake nearest = AnalyticsEngine.nearest(quakes, userLat, userLon);
        assertNotNull(nearest);
        assertEquals("pune", nearest.getId());

        // Null / empty check
        assertNull(AnalyticsEngine.nearest(null, userLat, userLon));
        assertNull(AnalyticsEngine.nearest(Collections.emptyList(), userLat, userLon));
    }
}
