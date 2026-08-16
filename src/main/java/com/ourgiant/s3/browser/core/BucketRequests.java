package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

// Pure request-building logic behind the bucket list (see gui.BucketListPanel), mirroring
// lambda-inspector's core.LambdaListRequests split between request-building and the
// JTable/DefaultTableModel that displays the result.
public final class BucketRequests {

    private BucketRequests() {
    }

    public static ListBucketsRequest build(int maxBuckets, String continuationToken) {
        ListBucketsRequest.Builder builder = ListBucketsRequest.builder().maxBuckets(maxBuckets);
        if (continuationToken != null && !continuationToken.isBlank()) {
            builder.continuationToken(continuationToken);
        }
        return builder.build();
    }
}
