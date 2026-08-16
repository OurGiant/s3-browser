package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

// Pure request-building logic behind the object detail dialog's metadata fetch (see
// gui.ObjectDetailDialog) - a separate call from listing, since ListObjectsV2 doesn't carry
// content-type, encryption, version ID, or user metadata.
public final class HeadObjectRequests {

    private HeadObjectRequests() {
    }

    public static HeadObjectRequest build(String bucket, String key) {
        return HeadObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
    }
}
