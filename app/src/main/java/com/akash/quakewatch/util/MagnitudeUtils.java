package com.akash.quakewatch.util;

import com.akash.quakewatch.R;

import java.util.Locale;

/**
 * MagnitudeUtils classifies earthquake magnitudes into standardized severity bands.
 * It provides the corresponding color resource, textual description, and formatted string.
 */
public final class MagnitudeUtils {

    public enum Severity {
        LOW("Low", R.color.qw_sev_low),
        MODERATE("Moderate", R.color.qw_sev_moderate),
        STRONG("Strong", R.color.qw_sev_strong),
        MAJOR("Major", R.color.qw_sev_major);

        private final String label;
        private final int colorRes;

        Severity(String label, int colorRes) {
            this.label = label;
            this.colorRes = colorRes;
        }

        public String getLabel() {
            return label;
        }

        public int getColorRes() {
            return colorRes;
        }
    }

    private MagnitudeUtils() {}

    /**
     * Maps a numerical magnitude to its categorical severity band.
     */
    public static Severity severity(double magnitude) {
        if (magnitude < 4.0) {
            return Severity.LOW;
        } else if (magnitude < 5.0) {
            return Severity.MODERATE;
        } else if (magnitude < 6.0) {
            return Severity.STRONG;
        } else {
            return Severity.MAJOR;
        }
    }

    /**
     * Formats magnitude with 1 decimal place (e.g. 5.4).
     */
    public static String formatMagnitude(double magnitude) {
        return String.format(Locale.US, "%.1f", magnitude);
    }
}
