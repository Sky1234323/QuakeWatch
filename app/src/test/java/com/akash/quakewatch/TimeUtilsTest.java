package com.akash.quakewatch;

import com.akash.quakewatch.util.TimeUtils;

import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

/**
 * Unit tests for TimeUtils ISO-8601 parsing, formatting, and timeAgo relative descriptions.
 */
public class TimeUtilsTest {

    @Test
    public void testParseIsoUtcWithFractions() throws ParseException {
        // Sample timestamp from USGS: 2026-11-13T12:12:57.241Z
        String iso = "2026-11-13T12:12:57.241Z";
        long ms = TimeUtils.parseIsoUtc(iso);
        assertTrue(ms > 0);

        // Format back to ISO UTC base
        String formatted = TimeUtils.formatIsoUtc(ms);
        assertEquals("2026-11-13T12:12:57", formatted);
    }

    @Test
    public void testParseIsoUtcWithoutFractions() throws ParseException {
        String iso = "2026-11-13T12:12:57Z";
        long ms = TimeUtils.parseIsoUtc(iso);
        assertTrue(ms > 0);
        String formatted = TimeUtils.formatIsoUtc(ms);
        assertEquals("2026-11-13T12:12:57", formatted);
    }

    @Test
    public void testTimeAgo() {
        long now = 1700000000000L;

        // < 1 minute
        assertEquals("just now", TimeUtils.timeAgo(now - 30000L, now));

        // 15 minutes
        assertEquals("15 min ago", TimeUtils.timeAgo(now - (15L * 60000L), now));

        // 3 hours
        assertEquals("3 h ago", TimeUtils.timeAgo(now - (3L * 3600000L), now));

        // 1 day
        assertEquals("1 day ago", TimeUtils.timeAgo(now - (25L * 3600000L), now));

        // 5 days
        assertEquals("5 days ago", TimeUtils.timeAgo(now - (5L * 86400000L), now));
    }
}
