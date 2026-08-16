package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SizeFormatterTest {

    @Test
    void formatsBytesBelow1024AsPlainBytes() {
        assertEquals("512 B", SizeFormatter.humanReadable(512L));
        assertEquals("0 B", SizeFormatter.humanReadable(0L));
    }

    @Test
    void formatsKilobytes() {
        assertEquals("1.0 KB", SizeFormatter.humanReadable(1024L));
        assertEquals("1.5 KB", SizeFormatter.humanReadable(1536L));
    }

    @Test
    void formatsMegabytesGigabytesAndTerabytes() {
        assertEquals("1.0 MB", SizeFormatter.humanReadable(1024L * 1024));
        assertEquals("1.0 GB", SizeFormatter.humanReadable(1024L * 1024 * 1024));
        assertEquals("1.0 TB", SizeFormatter.humanReadable(1024L * 1024 * 1024 * 1024));
    }

    @Test
    void returnsPlaceholderForNull() {
        assertEquals("—", SizeFormatter.humanReadable(null));
    }
}
