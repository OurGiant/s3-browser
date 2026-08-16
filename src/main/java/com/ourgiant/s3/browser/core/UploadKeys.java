package com.ourgiant.s3.browser.core;

// Pure logic for the upload dialog's default destination key: the currently browsed prefix plus
// the chosen local file's name - editable by the user afterward, this is just a sane starting
// point so uploading into the folder you're already looking at doesn't require retyping it.
public final class UploadKeys {

    private UploadKeys() {
    }

    public static String defaultKey(String currentPrefix, String fileName) {
        String prefix = currentPrefix != null ? currentPrefix : "";
        return prefix + fileName;
    }
}
