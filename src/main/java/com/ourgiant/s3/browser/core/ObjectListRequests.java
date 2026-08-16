package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

// Pure request-building logic behind the object browser (see gui.ObjectBrowserPanel). Always
// uses delimiter "/" so the response comes back pre-split into "folders" (commonPrefixes) and
// real objects at the current level (contents) - that's what makes prefix/folder-style
// navigation possible without listing (and filtering client-side) an entire bucket at once.
public final class ObjectListRequests {

    private ObjectListRequests() {
    }

    public static ListObjectsV2Request build(String bucket, String prefix, int maxKeys, String continuationToken) {
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
            .bucket(bucket)
            .delimiter("/")
            .maxKeys(maxKeys);
        if (prefix != null && !prefix.isEmpty()) {
            builder.prefix(prefix);
        }
        if (continuationToken != null && !continuationToken.isBlank()) {
            builder.continuationToken(continuationToken);
        }
        return builder.build();
    }
}
