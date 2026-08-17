package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GetObjectRequestsTest {

    @Test
    void buildsRequestWithBucketAndKey() {
        GetObjectRequest request = GetObjectRequests.build("my-bucket", "photos/cover.jpg");

        assertEquals("my-bucket", request.bucket());
        assertEquals("photos/cover.jpg", request.key());
    }
}
