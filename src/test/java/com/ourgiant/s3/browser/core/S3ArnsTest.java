package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3ArnsTest {

    @Test
    void buildsBucketArnWithNoRegionOrAccountSegment() {
        assertEquals("arn:aws:s3:::my-bucket", S3Arns.bucketArn("my-bucket"));
    }

    @Test
    void buildsObjectArn() {
        assertEquals("arn:aws:s3:::my-bucket/photos/cover.jpg",
            S3Arns.objectArn("my-bucket", "photos/cover.jpg"));
    }

    @Test
    void buildsBucketUrlWithTrailingSlash() {
        assertEquals("s3://my-bucket/", S3Arns.bucketUrl("my-bucket"));
    }

    @Test
    void buildsObjectUrl() {
        assertEquals("s3://my-bucket/photos/cover.jpg", S3Arns.objectUrl("my-bucket", "photos/cover.jpg"));
    }
}
