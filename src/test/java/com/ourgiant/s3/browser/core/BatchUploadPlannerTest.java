package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchUploadPlannerTest {

    @Test
    void plansIndividualFilesUsingPrefixPlusName(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("report.pdf");
        Files.writeString(file, "content");

        List<LocalUploadItem> items = BatchUploadPlanner.plan(List.of(file.toFile()), "uploads/");

        assertEquals(1, items.size());
        assertEquals("uploads/report.pdf", items.get(0).key());
        assertEquals(file, items.get(0).localPath());
    }

    @Test
    void treatsNullPrefixAsEmpty(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("report.pdf");
        Files.writeString(file, "content");

        List<LocalUploadItem> items = BatchUploadPlanner.plan(List.of(file.toFile()), null);

        assertEquals("report.pdf", items.get(0).key());
    }

    @Test
    void walksAFolderRecursivelyPreservingItsNameAsASubprefix(@TempDir Path tempDir) throws IOException {
        Path folder = tempDir.resolve("photos");
        Files.createDirectory(folder);
        Files.writeString(folder.resolve("cover.jpg"), "a");
        Path sub = folder.resolve("2024");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve("beach.jpg"), "b");

        List<LocalUploadItem> items = BatchUploadPlanner.plan(List.of(folder.toFile()), "uploads/");

        Set<String> keys = items.stream().map(LocalUploadItem::key).collect(Collectors.toSet());
        assertEquals(Set.of("uploads/photos/cover.jpg", "uploads/photos/2024/beach.jpg"), keys);
    }

    @Test
    void combinesIndividualFilesAndFoldersInOneSelection(@TempDir Path tempDir) throws IOException {
        Path standaloneFile = tempDir.resolve("notes.txt");
        Files.writeString(standaloneFile, "x");
        Path folder = tempDir.resolve("docs");
        Files.createDirectory(folder);
        Files.writeString(folder.resolve("a.txt"), "a");

        List<LocalUploadItem> items = BatchUploadPlanner.plan(
            List.of(standaloneFile.toFile(), folder.toFile()), "");

        Set<String> keys = items.stream().map(LocalUploadItem::key).collect(Collectors.toSet());
        assertEquals(Set.of("notes.txt", "docs/a.txt"), keys);
    }

    @Test
    void skipsAnEntryThatIsNeitherAFileNorADirectory() {
        File doesNotExist = new File("/no/such/path/ever");

        List<LocalUploadItem> items = BatchUploadPlanner.plan(List.of(doesNotExist), "uploads/");

        assertTrue(items.isEmpty());
    }
}
