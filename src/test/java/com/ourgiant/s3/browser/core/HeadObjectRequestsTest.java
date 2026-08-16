package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadObjectRequestsTest {

    @Test
    void buildsRequestWithBucketAndKey() {
        HeadObjectRequest request = HeadObjectRequests.build("my-bucket", "photos/cover.jpg");

        assertEquals("my-bucket", request.bucket());
        assertEquals("photos/cover.jpg", request.key());
    }
}
