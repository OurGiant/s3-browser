package com.ourgiant.s3.browser.core;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// Pure request-building logic behind the upload dialog (see gui.UploadDialog). The actual
// upload call is S3Client.putObject(PutObjectRequest, Path) - streaming straight from the local
// file, so this class only builds the request half, not the body.
public final class PutObjectRequests {

    private PutObjectRequests() {
    }

    public static PutObjectRequest build(String bucket, String key, String contentType) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key);
        if (contentType != null && !contentType.isBlank()) {
            builder.contentType(contentType);
        }
        return builder.build();
    }
}
