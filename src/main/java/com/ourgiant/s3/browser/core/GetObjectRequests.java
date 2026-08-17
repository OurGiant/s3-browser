package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;

// Pure request-building logic behind the object browser's Download action (see
// gui.ObjectBrowserPanel). The actual download call is S3Client.getObject(GetObjectRequest,
// Path) - streaming straight to the local file, so this class only builds the request half,
// mirroring PutObjectRequests for upload.
public final class GetObjectRequests {

    private GetObjectRequests() {
    }

    public static GetObjectRequest build(String bucket, String key) {
        return GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
    }
}
