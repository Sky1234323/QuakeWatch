package com.akash.quakewatch.data.remote;

import android.util.Xml;

import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.util.TimeUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * QuakeMlParser parses earthquake event data from USGS QuakeML (XML) streams.
 * It uses an XmlPullParser with an element path stack to precisely map nested elements
 * to the Quake data model, ensuring only valid earthquake events are retained.
 */
public class QuakeMlParser {

    /**
     * Parses the given QuakeML input stream and returns a list of Quake objects.
     * Skips invalid, non-earthquake, or incomplete events without throwing.
     */
    public List<Quake> parse(InputStream in) throws XmlPullParserException, IOException {
        List<Quake> quakes = new ArrayList<>();
        if (in == null) {
            return quakes;
        }

        XmlPullParser parser;
        try {
            org.xmlpull.v1.XmlPullParserFactory factory = org.xmlpull.v1.XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            parser = factory.newPullParser();
        } catch (Exception e) {
            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        }
        parser.setInput(in, null);

        int eventType = parser.getEventType();
        Deque<String> tagStack = new ArrayDeque<>();

        boolean insideEvent = false;
        String eventId = "";
        String place = "";
        Double magnitude = null;
        String magType = "";
        Double depthKm = null;
        Double latitude = null;
        Double longitude = null;
        Long timeMs = null;
        String eventCategory = "";

        boolean firstOriginProcessed = false;
        boolean firstMagnitudeProcessed = false;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if ("event".equalsIgnoreCase(tagName)) {
                        insideEvent = true;
                        tagStack.clear();
                        firstOriginProcessed = false;
                        firstMagnitudeProcessed = false;

                        // Extract ID from catalog:datasource and catalog:eventid attributes
                        String datasource = "";
                        String catalogEventId = "";
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            String attrName = parser.getAttributeName(i);
                            if (attrName != null) {
                                if (attrName.endsWith(":datasource") || attrName.equalsIgnoreCase("datasource")) {
                                    datasource = parser.getAttributeValue(i);
                                } else if (attrName.endsWith(":eventid") || attrName.equalsIgnoreCase("eventid")) {
                                    catalogEventId = parser.getAttributeValue(i);
                                } else if (attrName.equalsIgnoreCase("publicID") && eventId.isEmpty()) {
                                    eventId = parser.getAttributeValue(i);
                                }
                            }
                        }

                        if (!datasource.isEmpty() || !catalogEventId.isEmpty()) {
                            eventId = datasource + catalogEventId;
                        }

                        place = "";
                        magnitude = null;
                        magType = "";
                        depthKm = 0.0;
                        latitude = null;
                        longitude = null;
                        timeMs = null;
                        eventCategory = "earthquake";
                    } else if (insideEvent) {
                        tagStack.push(tagName);
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (insideEvent && !tagStack.isEmpty()) {
                        String text = parser.getText();
                        if (text != null) {
                            text = text.trim();
                            if (!text.isEmpty()) {
                                String path = buildPath(tagStack);
                                try {
                                    if ("description/text".equalsIgnoreCase(path)) {
                                        place = text;
                                    } else if ("type".equalsIgnoreCase(path)) {
                                        eventCategory = text;
                                    } else if (!firstOriginProcessed) {
                                        if ("origin/time/value".equalsIgnoreCase(path)) {
                                            timeMs = TimeUtils.parseIsoUtc(text);
                                        } else if ("origin/latitude/value".equalsIgnoreCase(path)) {
                                            latitude = Double.parseDouble(text);
                                        } else if ("origin/longitude/value".equalsIgnoreCase(path)) {
                                            longitude = Double.parseDouble(text);
                                        } else if ("origin/depth/value".equalsIgnoreCase(path)) {
                                            // Depth is in meters, convert to km per spec
                                            depthKm = Double.parseDouble(text) / 1000.0;
                                        }
                                    } else if (!firstMagnitudeProcessed) {
                                        if ("magnitude/mag/value".equalsIgnoreCase(path)) {
                                            magnitude = Double.parseDouble(text);
                                        } else if ("magnitude/type".equalsIgnoreCase(path)) {
                                            magType = text;
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // Tolerant parsing for malformed values
                                }
                            }
                        }
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if ("event".equalsIgnoreCase(tagName)) {
                        insideEvent = false;

                        // Retain only valid earthquakes with all required fields
                        boolean isEarthquake = eventCategory.isEmpty() || "earthquake".equalsIgnoreCase(eventCategory);
                        if (isEarthquake && magnitude != null && latitude != null && longitude != null && timeMs != null) {
                            if (eventId.isEmpty()) {
                                eventId = "us_" + timeMs;
                            }
                            Quake quake = new Quake(eventId, place, magnitude, magType,
                                    depthKm != null ? depthKm : 0.0, latitude, longitude, timeMs);
                            quakes.add(quake);
                        }
                    } else if (insideEvent) {
                        if ("origin".equalsIgnoreCase(tagName)) {
                            firstOriginProcessed = true;
                        } else if ("magnitude".equalsIgnoreCase(tagName)) {
                            firstMagnitudeProcessed = true;
                        }
                        if (!tagStack.isEmpty()) {
                            tagStack.pop();
                        }
                    }
                    break;
            }

            eventType = parser.next();
        }

        return quakes;
    }

    private String buildPath(Deque<String> stack) {
        StringBuilder sb = new StringBuilder();
        // ArrayDeque iterates from top (newest) to bottom (oldest), so we reverse for parent/child order
        List<String> list = new ArrayList<>(stack);
        for (int i = list.size() - 1; i >= 0; i--) {
            sb.append(list.get(i));
            if (i > 0) {
                sb.append("/");
            }
        }
        return sb.toString();
    }
}
