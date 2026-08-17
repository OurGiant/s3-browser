package com.ourgiant.s3.browser.core;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// Pure planning logic behind the "Upload Multiple..." batch flow (see gui.BatchUploadDialog):
// turns a raw JFileChooser selection (a mix of individual files and/or folders, since the
// picker uses FILES_AND_DIRECTORIES + multi-select) into a flat list of local-file-to-S3-key
// pairs, before any AWS call or confirmation dialog happens. A selected folder is walked
// recursively, preserving its own name as a subprefix so two different local folders whose
// contents happen to share relative paths can't collide into the same destination keys.
public final class BatchUploadPlanner {

    private BatchUploadPlanner() {
    }

    public static List<LocalUploadItem> plan(List<File> selection, String destinationPrefix) {
        String prefix = destinationPrefix != null ? destinationPrefix : "";
        List<LocalUploadItem> items = new ArrayList<>();
        for (File file : selection) {
            if (file.isDirectory()) {
                items.addAll(planFolder(file, prefix));
            } else if (file.isFile()) {
                items.add(new LocalUploadItem(file.toPath(), prefix + file.getName()));
            }
        }
        return items;
    }

    private static List<LocalUploadItem> planFolder(File folder, String prefix) {
        Path folderPath = folder.toPath();
        String folderKeyPrefix = prefix + folder.getName() + "/";
        List<LocalUploadItem> items = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(folderPath)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String relative = folderPath.relativize(path).toString().replace(File.separatorChar, '/');
                items.add(new LocalUploadItem(path, folderKeyPrefix + relative));
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk folder " + folder, e);
        }
        return items;
    }
}
