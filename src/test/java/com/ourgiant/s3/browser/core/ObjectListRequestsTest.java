package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObjectListRequestsTest {

    @Test
    void buildsRequestWithBucketAndPrefix() {
        ListObjectsV2Request request = ObjectListRequests.build("my-bucket", "photos/", 50, null);

        assertEquals("my-bucket", request.bucket());
        assertEquals("photos/", request.prefix());
        assertEquals("/", request.delimiter());
        assertEquals(50, request.maxKeys());
        assertNull(request.continuationToken());
    }

    @Test
    void emptyPrefixIsOmittedNotSentAsEmptyString() {
        ListObjectsV2Request request = ObjectListRequests.build("my-bucket", "", 50, null);

        assertNull(request.prefix());
    }

    @Test
    void includesContinuationTokenWhenProvided() {
        ListObjectsV2Request request = ObjectListRequests.build("my-bucket", "", 50, "token-abc");

        assertEquals("token-abc", request.continuationToken());
    }
}
