package com.ourgiant.s3.browser.model;

import java.util.Map;

// Full metadata for a single object, fetched via HeadObject (see core.ObjectDetailMapper) -
// separate from S3Entry because this needs its own AWS call (HeadObject), unlike
// lambda-inspector's function config detail, which was already fully present in the listing
// response. Encryption/content-type/version aren't part of ListObjectsV2's response shape.
public class ObjectDetail {
    public final String key;
    public final Long size;
    public final String contentType;
    public final String storageClass;
    public final String lastModified;
    public final String eTag;
    public final String versionId;
    public final String serverSideEncryption; // null if the object isn't encrypted at rest
    public final String sseKmsKeyId;          // null unless serverSideEncryption is KMS-based
    public final Map<String, String> userMetadata;

    public ObjectDetail(String key, Long size, String contentType, String storageClass, String lastModified,
            String eTag, String versionId, String serverSideEncryption, String sseKmsKeyId,
            Map<String, String> userMetadata) {
        this.key = key;
        this.size = size;
        this.contentType = contentType;
        this.storageClass = storageClass;
        this.lastModified = lastModified;
        this.eTag = eTag;
        this.versionId = versionId;
        this.serverSideEncryption = serverSideEncryption;
        this.sseKmsKeyId = sseKmsKeyId;
        this.userMetadata = userMetadata;
    }
}
