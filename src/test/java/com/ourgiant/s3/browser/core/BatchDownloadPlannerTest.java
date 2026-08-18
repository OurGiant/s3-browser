package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchDownloadPlannerTest {

    @Test
    void flatObjectsCollapseToJustTheirBasenameUnderTheCurrentPrefix(@TempDir Path destinationDir) {
        List<KeyAndSize> objects = List.of(
            new KeyAndSize("photos/cover.jpg", 100),
            new KeyAndSize("photos/notes.txt", 20));

        List<RemoteDownloadItem> items = BatchDownloadPlanner.plan(objects, "photos/", destinationDir);

        assertEquals(destinationDir.resolve("cover.jpg"), items.get(0).localPath());
        assertEquals(destinationDir.resolve("notes.txt"), items.get(1).localPath());
        assertEquals(100, items.get(0).size());
    }

    @Test
    void nestedKeysPreserveTheirStructureRelativeToTheFolderPrefix(@TempDir Path destinationDir) {
        List<KeyAndSize> objects = List.of(
            new KeyAndSize("photos/2024/beach.jpg", 100),
            new KeyAndSize("photos/2024/sub/deep.jpg", 200));

        List<RemoteDownloadItem> items = BatchDownloadPlanner.plan(objects, "photos/", destinationDir);

        assertEquals(destinationDir.resolve("2024").resolve("beach.jpg"), items.get(0).localPath());
        assertEquals(destinationDir.resolve("2024").resolve("sub").resolve("deep.jpg"), items.get(1).localPath());
    }

    @Test
    void treatsNullBasePrefixAsEmpty(@TempDir Path destinationDir) {
        List<KeyAndSize> objects = List.of(new KeyAndSize("report.pdf", 5));

        List<RemoteDownloadItem> items = BatchDownloadPlanner.plan(objects, null, destinationDir);

        assertEquals(destinationDir.resolve("report.pdf"), items.get(0).localPath());
    }

    @Test
    void keyOutsideTheBasePrefixIsNotStripped(@TempDir Path destinationDir) {
        List<KeyAndSize> objects = List.of(new KeyAndSize("other/report.pdf", 5));

        List<RemoteDownloadItem> items = BatchDownloadPlanner.plan(objects, "photos/", destinationDir);

        assertEquals(destinationDir.resolve("other").resolve("report.pdf"), items.get(0).localPath());
    }
}
