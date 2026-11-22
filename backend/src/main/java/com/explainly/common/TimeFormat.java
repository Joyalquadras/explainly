package com.explainly.common;

public final class TimeFormat {
    private TimeFormat() {}

    public static String clock(Integer seconds) {
        if (seconds == null || seconds < 0) return "";
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** Parses "mm:ss" or "h:mm:ss" into seconds; returns null when not parseable. */
    public static Integer parseClock(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split(":");
        if (parts.length < 2 || parts.length > 3) return null;
        try {
            int total = 0;
            for (String p : parts) total = total * 60 + Integer.parseInt(p.trim());
            return total;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
