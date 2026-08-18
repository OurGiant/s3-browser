package com.ourgiant.s3.browser.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Pure planning logic behind the "Download Selected" batch flow (see gui.ObjectBrowserPanel /
// gui.BatchDownloadDialog): turns a list of S3 (key, size) pairs into local download
// destinations, relative to a given base prefix - one function covers both the flat
// multi-select case (basePrefix = the current browsing prefix, so each relative path collapses
// to just the object's basename) and the recursive-folder case (basePrefix = the selected
// folder's own key, so nested keys keep their structure under the destination directory) purely
// by what basePrefix/destinationDir the caller passes in. Mirrors BatchUploadPlanner in reverse.
public final class BatchDownloadPlanner {

    private BatchDownloadPlanner() {
    }

    public static List<RemoteDownloadItem> plan(List<KeyAndSize> objects, String basePrefix, Path destinationDir) {
        String prefix = basePrefix != null ? basePrefix : "";
        List<RemoteDownloadItem> items = new ArrayList<>();
        for (KeyAndSize object : objects) {
            String relative = object.key().startsWith(prefix) ? object.key().substring(prefix.length()) : object.key();
            Path localPath = destinationDir;
            for (String segment : relative.split("/")) {
                if (!segment.isEmpty()) {
                    localPath = localPath.resolve(segment);
                }
            }
            items.add(new RemoteDownloadItem(object.key(), object.size(), localPath));
        }
        return items;
    }
}
