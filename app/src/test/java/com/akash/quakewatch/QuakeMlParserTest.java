package com.akash.quakewatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.data.remote.QuakeMlParser;
import com.akash.quakewatch.util.TimeUtils;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * QuakeMlParserTest verifies parsing of USGS QuakeML feeds according to Section 17.
 * It validates extraction of event IDs, geographic coordinates, magnitudes,
 * depths, timestamps, and ensures events with missing magnitudes are omitted.
 */
public class QuakeMlParserTest {

    private static final double DELTA = 0.0001;

    @Test
    public void testParseSampleQuakeMl() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("sample_quakeml.xml");
        assertNotNull("sample_quakeml.xml resource must exist", in);

        QuakeMlParser parser = new QuakeMlParser();
        List<Quake> quakes = parser.parse(in);

        // 1. Assert exactly 3 events are parsed (the 4th event with no magnitude must be skipped)
        assertNotNull(quakes);
        assertEquals(3, quakes.size());

        // Event 1: Philippines
        Quake q1 = quakes.get(0);
        assertEquals("us7000ty0g", q1.getId());
        assertEquals("12 km ESE of Baculin, Philippines", q1.getPlace());
        assertEquals(4.4, q1.getMagnitude(), DELTA);
        assertEquals("mb", q1.getMagType());
        assertEquals(79.163, q1.getDepthKm(), DELTA);
        assertEquals(7.3953, q1.getLatitude(), DELTA);
        assertEquals(126.6864, q1.getLongitude(), DELTA);
        assertEquals(TimeUtils.parseIsoUtc("2026-11-13T12:12:57.241Z"), q1.getTimeMs());

        // Event 2: Tonga
        Quake q2 = quakes.get(1);
        assertEquals("us7000txzd", q2.getId());
        assertEquals("59 km WSW of Houma, Tonga", q2.getPlace());
        assertEquals(4.8, q2.getMagnitude(), DELTA);
        assertEquals("mb", q2.getMagType());
        assertEquals(162.78, q2.getDepthKm(), DELTA);
        assertEquals(-21.3036, q2.getLatitude(), DELTA);
        assertEquals(-175.8541, q2.getLongitude(), DELTA);
        assertEquals(TimeUtils.parseIsoUtc("2026-11-13T11:18:12.734Z"), q2.getTimeMs());

        // Event 3: Loyalty Islands
        Quake q3 = quakes.get(2);
        assertEquals("us7000txz3", q3.getId());
        assertEquals("southeast of the Loyalty Islands", q3.getPlace());
        assertEquals(4.9, q3.getMagnitude(), DELTA);
        assertEquals("mb", q3.getMagType());
        assertEquals(10.0, q3.getDepthKm(), DELTA);
        assertEquals(-22.3729, q3.getLatitude(), DELTA);
        assertEquals(173.4492, q3.getLongitude(), DELTA);
        assertEquals(TimeUtils.parseIsoUtc("2026-11-13T09:55:45.053Z"), q3.getTimeMs());
    }
}
