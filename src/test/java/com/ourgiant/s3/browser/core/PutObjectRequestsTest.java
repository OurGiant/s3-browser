package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PutObjectRequestsTest {

    @Test
    void buildsRequestWithBucketKeyAndContentType() {
        PutObjectRequest request = PutObjectRequests.build("my-bucket", "photos/cover.jpg", "image/jpeg");

        assertEquals("my-bucket", request.bucket());
        assertEquals("photos/cover.jpg", request.key());
        assertEquals("image/jpeg", request.contentType());
    }

    @Test
    void omitsContentTypeWhenNull() {
        PutObjectRequest request = PutObjectRequests.build("my-bucket", "file.bin", null);

        assertNull(request.contentType());
    }

    @Test
    void omitsContentTypeWhenBlank() {
        PutObjectRequest request = PutObjectRequests.build("my-bucket", "file.bin", "   ");

        assertNull(request.contentType());
    }
}
