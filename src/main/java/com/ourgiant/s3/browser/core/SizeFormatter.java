package com.ourgiant.s3.browser.core;

// Pure byte-count formatting for the object browser grid and detail view - binary (1024-based)
// units, matching what the AWS console itself shows for object sizes.
public final class SizeFormatter {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private SizeFormatter() {
    }

    public static String humanReadable(Long bytes) {
        if (bytes == null) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < UNITS.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", value, UNITS[unitIndex]);
    }
}
