package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketNameFilterTest {

    @Test
    void blankOrNullQueryMatchesEverything() {
        assertTrue(BucketNameFilter.matches("my-bucket", null));
        assertTrue(BucketNameFilter.matches("my-bucket", ""));
        assertTrue(BucketNameFilter.matches("my-bucket", "   "));
    }

    @Test
    void matchesSubstringNotJustPrefix() {
        assertTrue(BucketNameFilter.matches("aaacomdev-vpcflowlogs", "flow"));
        assertTrue(BucketNameFilter.matches("aaacomdev-vpcflowlogs", "aaacomdev"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(BucketNameFilter.matches("aaacomdev-vpcflowlogs", "FLOW"));
        assertTrue(BucketNameFilter.matches("AAACOMDEV-VPCFLOWLOGS", "flow"));
    }

    @Test
    void trimsQueryWhitespace() {
        assertTrue(BucketNameFilter.matches("my-bucket", "  bucket  "));
    }

    @Test
    void nonMatchingQueryReturnsFalse() {
        assertFalse(BucketNameFilter.matches("my-bucket", "nonexistent"));
    }

    @Test
    void nullBucketNameWithNonBlankQueryDoesNotMatch() {
        assertFalse(BucketNameFilter.matches(null, "bucket"));
    }
}
