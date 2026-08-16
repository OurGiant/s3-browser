package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.S3Entry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectGridModelTest {

    @Test
    void mapsRootLevelFoldersAndObjects() {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .commonPrefixes(
                CommonPrefix.builder().prefix("photos/").build(),
                CommonPrefix.builder().prefix("videos/").build())
            .contents(
                S3Object.builder().key("readme.txt").size(100L).storageClass("STANDARD")
                    .lastModified(Instant.parse("2026-01-01T00:00:00Z")).build())
            .build();

        List<S3Entry> entries = ObjectGridModel.toEntries(response, "");

        assertEquals(3, entries.size());
        assertEquals(S3Entry.Type.FOLDER, entries.get(0).type);
        assertEquals("photos", entries.get(0).displayName);
        assertEquals("photos/", entries.get(0).key);
        assertEquals(S3Entry.Type.FOLDER, entries.get(1).type);
        assertEquals("videos", entries.get(1).displayName);
        assertEquals(S3Entry.Type.OBJECT, entries.get(2).type);
        assertEquals("readme.txt", entries.get(2).displayName);
        assertEquals(100L, entries.get(2).size);
    }

    @Test
    void stripsCurrentPrefixFromNestedFoldersAndObjects() {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .commonPrefixes(CommonPrefix.builder().prefix("photos/2026/").build())
            .contents(S3Object.builder().key("photos/cover.jpg").size(2048L).build())
            .build();

        List<S3Entry> entries = ObjectGridModel.toEntries(response, "photos/");

        assertEquals("2026", entries.get(0).displayName);
        assertEquals("cover.jpg", entries.get(1).displayName);
    }

    @Test
    void skipsZeroByteFolderMarkerObjectMatchingCurrentPrefix() {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .contents(
                S3Object.builder().key("photos/").size(0L).build(),
                S3Object.builder().key("photos/cover.jpg").size(2048L).build())
            .build();

        List<S3Entry> entries = ObjectGridModel.toEntries(response, "photos/");

        assertEquals(1, entries.size());
        assertEquals("cover.jpg", entries.get(0).displayName);
    }

    @Test
    void handlesEmptyResponse() {
        ListObjectsV2Response response = ListObjectsV2Response.builder().build();

        assertTrue(ObjectGridModel.toEntries(response, "").isEmpty());
    }

    @Test
    void formatRowShowsFolderTypeWithNoSize() {
        S3Entry folder = new S3Entry(S3Entry.Type.FOLDER, "photos/", "photos", null, null, null);

        List<Object> row = ObjectGridModel.formatRow(folder);

        assertEquals(List.of("photos", "Folder", "", "", ""), row);
    }

    @Test
    void formatRowShowsObjectSizeAndStorageClass() {
        S3Entry object = new S3Entry(S3Entry.Type.OBJECT, "readme.txt", "readme.txt", 2048L, "STANDARD", "2026-01-01T00:00:00Z");

        List<Object> row = ObjectGridModel.formatRow(object);

        assertEquals(List.of("readme.txt", "Object", "2.0 KB", "STANDARD", "2026-01-01T00:00:00Z"), row);
    }
}
