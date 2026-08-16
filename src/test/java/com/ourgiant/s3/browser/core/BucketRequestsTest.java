package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BucketRequestsTest {

    @Test
    void buildsRequestWithMaxBucketsAndNoContinuationToken() {
        ListBucketsRequest request = BucketRequests.build(50, null);

        assertEquals(50, request.maxBuckets());
        assertNull(request.continuationToken());
    }

    @Test
    void buildsRequestWithContinuationTokenWhenProvided() {
        ListBucketsRequest request = BucketRequests.build(50, "token-123");

        assertEquals("token-123", request.continuationToken());
    }

    @Test
    void blankContinuationTokenIsTreatedAsAbsent() {
        ListBucketsRequest request = BucketRequests.build(50, "   ");

        assertNull(request.continuationToken());
    }
}
