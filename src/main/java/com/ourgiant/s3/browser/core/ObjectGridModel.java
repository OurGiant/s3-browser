package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.S3Entry;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

// Pure mapping/formatting logic behind the object browser grid: turns a ListObjectsV2 response
// (commonPrefixes = "folders" one level below the current prefix, contents = real objects at
// this level - see core.ObjectListRequests for why delimiter "/" makes this split happen
// server-side) into display rows, stripping the current prefix off each key/prefix so the grid
// shows just the folder/file name, not the full path every time.
public final class ObjectGridModel {

    public static final List<String> COLUMN_NAMES = List.of("Name", "Type", "Size", "Storage Class", "Last Modified");

    private static final String UNKNOWN = "—";

    private ObjectGridModel() {
    }

    public static List<S3Entry> toEntries(ListObjectsV2Response response, String currentPrefix) {
        String prefix = currentPrefix != null ? currentPrefix : "";
        List<S3Entry> entries = new ArrayList<>();

        if (response.hasCommonPrefixes()) {
            for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                String fullPrefix = commonPrefix.prefix();
                String displayName = stripPrefix(fullPrefix, prefix);
                if (displayName.endsWith("/")) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                if (displayName.isEmpty()) {
                    continue;
                }
                entries.add(new S3Entry(S3Entry.Type.FOLDER, fullPrefix, displayName, null, null, null));
            }
        }

        if (response.hasContents()) {
            for (S3Object object : response.contents()) {
                String key = object.key();
                // Skip the zero-byte "folder marker" object some tools (including the AWS
                // console) create at the prefix itself when you create an empty folder - it
                // isn't a real file and would otherwise show up as a nameless row.
                if (key.equals(prefix)) {
                    continue;
                }
                String displayName = stripPrefix(key, prefix);
                if (displayName.isEmpty()) {
                    continue;
                }
                String lastModified = object.lastModified() != null ? object.lastModified().toString() : null;
                entries.add(new S3Entry(S3Entry.Type.OBJECT, key, displayName, object.size(),
                    object.storageClassAsString(), lastModified));
            }
        }

        return entries;
    }

    private static String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    public static List<Object> formatRow(S3Entry entry) {
        List<Object> row = new ArrayList<>();
        row.add(entry.displayName != null && !entry.displayName.isEmpty() ? entry.displayName : UNKNOWN);
        row.add(entry.type == S3Entry.Type.FOLDER ? "Folder" : "Object");
        row.add(entry.type == S3Entry.Type.FOLDER ? "" : SizeFormatter.humanReadable(entry.size));
        row.add(entry.storageClass != null ? entry.storageClass : "");
        row.add(entry.lastModified != null ? entry.lastModified : "");
        return row;
    }
}
