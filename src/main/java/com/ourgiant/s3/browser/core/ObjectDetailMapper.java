package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.ObjectDetail;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.Collections;
import java.util.Map;

// Pure mapping from a HeadObject response to the shared ObjectDetail model.
public final class ObjectDetailMapper {

    private ObjectDetailMapper() {
    }

    public static ObjectDetail toDetail(String key, HeadObjectResponse response) {
        String lastModified = response.lastModified() != null ? response.lastModified().toString() : null;
        Map<String, String> metadata = response.hasMetadata() ? response.metadata() : Collections.emptyMap();
        return new ObjectDetail(
            key,
            response.contentLength(),
            response.contentType(),
            response.storageClassAsString(),
            lastModified,
            response.eTag(),
            response.versionId(),
            response.serverSideEncryptionAsString(),
            response.ssekmsKeyId(),
            metadata);
    }
}
