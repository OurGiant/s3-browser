package com.ourgiant.s3.browser.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppVersionTest {

    @Test
    void resolveNeverReturnsNull() {
        // Running from the test JVM (not the packaged jar), so this falls back to
        // version.properties or "dev" - either way, never null.
        assertNotNull(AppVersion.resolve());
    }
}
