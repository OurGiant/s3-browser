package com.ourgiant.s3.browser.model;

// One row in the object browser at a given prefix (see core.ObjectGridModel) - either a
// "folder" (an S3 common prefix, not a real object) or a real object at this level.
public class S3Entry {
    public enum Type { FOLDER, OBJECT }

    public final Type type;
    public final String key;          // full key (objects) or full prefix (folders)
    public final String displayName;  // key/prefix with the current prefix and trailing "/" stripped
    public final Long size;           // null for folders
    public final String storageClass; // null for folders
    public final String lastModified; // null for folders

    public S3Entry(Type type, String key, String displayName, Long size, String storageClass, String lastModified) {
        this.type = type;
        this.key = key;
        this.displayName = displayName;
        this.size = size;
        this.storageClass = storageClass;
        this.lastModified = lastModified;
    }
}
