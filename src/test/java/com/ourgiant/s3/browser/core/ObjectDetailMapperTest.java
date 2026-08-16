package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.ObjectDetail;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDetailMapperTest {

    @Test
    void mapsAllFieldsIncludingKmsEncryption() {
        Instant lastModified = Instant.parse("2026-02-01T00:00:00Z");
        HeadObjectResponse response = HeadObjectResponse.builder()
            .contentLength(2048L)
            .contentType("application/json")
            .storageClass("STANDARD")
            .lastModified(lastModified)
            .eTag("\"abc123\"")
            .versionId("v1")
            .serverSideEncryption(ServerSideEncryption.AWS_KMS)
            .ssekmsKeyId("arn:aws:kms:us-east-1:123456789012:key/abc")
            .metadata(Map.of("uploaded-by", "ci"))
            .build();

        ObjectDetail detail = ObjectDetailMapper.toDetail("path/to/file.json", response);

        assertEquals("path/to/file.json", detail.key);
        assertEquals(2048L, detail.size);
        assertEquals("application/json", detail.contentType);
        assertEquals("STANDARD", detail.storageClass);
        assertEquals(lastModified.toString(), detail.lastModified);
        assertEquals("\"abc123\"", detail.eTag);
        assertEquals("v1", detail.versionId);
        assertEquals("aws:kms", detail.serverSideEncryption);
        assertEquals("arn:aws:kms:us-east-1:123456789012:key/abc", detail.sseKmsKeyId);
        assertEquals(Map.of("uploaded-by", "ci"), detail.userMetadata);
    }

    @Test
    void handlesUnencryptedObjectWithNoMetadata() {
        HeadObjectResponse response = HeadObjectResponse.builder()
            .contentLength(100L)
            .build();

        ObjectDetail detail = ObjectDetailMapper.toDetail("plain.txt", response);

        assertEquals(null, detail.serverSideEncryption);
        assertEquals(null, detail.sseKmsKeyId);
        assertTrue(detail.userMetadata.isEmpty());
    }
}
