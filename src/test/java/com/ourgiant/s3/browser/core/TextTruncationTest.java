package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTruncationTest {

    @Test
    void nullBecomesPlaceholder() {
        assertEquals("—", TextTruncation.truncate(null, 10));
    }

    @Test
    void valueAtExactlyMaxLengthIsUnchanged() {
        String value = "1234567890";
        assertEquals(value, TextTruncation.truncate(value, 10));
    }

    @Test
    void valueUnderMaxLengthIsUnchanged() {
        assertEquals("short", TextTruncation.truncate("short", 10));
    }

    @Test
    void valueOverMaxLengthIsTruncatedWithEllipsis() {
        String value = "arn:aws:s3:::my-bucket/a/very/long/nested/key/path/that/goes/on/and/on.txt";
        String result = TextTruncation.truncate(value, 20);

        assertEquals(20, result.length());
        assertTrue(result.endsWith("…"));
        assertTrue(value.startsWith(result.substring(0, 19)));
    }

    @Test
    void wasTruncatedReflectsWhetherTruncationHappened() {
        assertFalse(TextTruncation.wasTruncated("short", 10));
        assertFalse(TextTruncation.wasTruncated(null, 10));
        assertTrue(TextTruncation.wasTruncated("this is definitely longer than ten chars", 10));
    }
}
