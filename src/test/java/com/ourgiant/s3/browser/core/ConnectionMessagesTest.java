package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionMessagesTest {

    @Test
    void plainTitleWhenNoProfileConnected() {
        assertEquals("S3 Browser", ConnectionMessages.windowTitle(null, null, null));
    }

    @Test
    void includesProfileAccountAndRegionWhenKnown() {
        assertEquals("S3 Browser — dev (123456789012, us-east-1)",
            ConnectionMessages.windowTitle("dev", "123456789012", "us-east-1"));
    }

    @Test
    void degradesGracefullyWithoutAccountId() {
        assertEquals("S3 Browser — dev (us-east-1)",
            ConnectionMessages.windowTitle("dev", null, "us-east-1"));
    }
}
