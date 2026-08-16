package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadKeysTest {

    @Test
    void combinesPrefixAndFileName() {
        assertEquals("photos/cover.jpg", UploadKeys.defaultKey("photos/", "cover.jpg"));
    }

    @Test
    void handlesEmptyPrefixAtBucketRoot() {
        assertEquals("cover.jpg", UploadKeys.defaultKey("", "cover.jpg"));
    }

    @Test
    void handlesNullPrefixAsBucketRoot() {
        assertEquals("cover.jpg", UploadKeys.defaultKey(null, "cover.jpg"));
    }

    @Test
    void handlesNestedPrefix() {
        assertEquals("photos/2026/cover.jpg", UploadKeys.defaultKey("photos/2026/", "cover.jpg"));
    }
}
