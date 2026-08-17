package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3ConsoleUrlsTest {

    @Test
    void buildsBucketRootUrlWithoutPrefixParam() {
        String url = S3ConsoleUrls.bucketPrefixUrl("my-bucket", "", "us-east-1");

        assertEquals("https://us-east-1.console.aws.amazon.com/s3/buckets/my-bucket"
            + "?region=us-east-1&bucketType=general", url);
    }

    @Test
    void treatsNullPrefixLikeEmpty() {
        String url = S3ConsoleUrls.bucketPrefixUrl("my-bucket", null, "us-east-1");

        assertEquals("https://us-east-1.console.aws.amazon.com/s3/buckets/my-bucket"
            + "?region=us-east-1&bucketType=general", url);
    }

    @Test
    void appendsPrefixParamPreservingSlashes() {
        String url = S3ConsoleUrls.bucketPrefixUrl("my-bucket", "photos/2024/", "us-east-1");

        assertEquals("https://us-east-1.console.aws.amazon.com/s3/buckets/my-bucket"
            + "?region=us-east-1&bucketType=general&prefix=photos/2024/", url);
    }

    @Test
    void percentEncodesSpecialCharactersWithinASegment() {
        String url = S3ConsoleUrls.bucketPrefixUrl("my-bucket", "a b&c/", "us-east-1");

        assertEquals("https://us-east-1.console.aws.amazon.com/s3/buckets/my-bucket"
            + "?region=us-east-1&bucketType=general&prefix=a%20b%26c/", url);
    }
}
