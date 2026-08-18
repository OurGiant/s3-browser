package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

// Lists every object under a prefix, recursively - separate from ObjectListRequests, which
// always sets delimiter "/" for the one-level folder-browsing UI (see gui.ObjectBrowserPanel).
// A folder download (see gui.ObjectBrowserPanel's "Download Selected") needs the *full*
// recursive contents, not just the next level, so this deliberately omits the delimiter and
// pages through every result. Does real AWS calls - run off the EDT.
public final class RecursiveObjectListing {

    private RecursiveObjectListing() {
    }

    public static List<S3Object> listAll(S3Client s3, String bucket, String prefix) {
        List<S3Object> all = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix);
            if (continuationToken != null) {
                builder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = s3.listObjectsV2(builder.build());
            all.addAll(response.contents());
            continuationToken = Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
        } while (continuationToken != null);
        return all;
    }
}
