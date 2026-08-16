package com.ourgiant.s3.browser.core;

import java.util.Locale;

// Pure matching logic behind the bucket list's search field (see gui.BucketListPanel) - a plain
// case-insensitive substring match, not just a prefix match, since bucket names in a real
// account rarely share a common prefix a user would think to type.
public final class BucketNameFilter {

    private BucketNameFilter() {
    }

    public static boolean matches(String bucketName, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (bucketName == null) {
            return false;
        }
        return bucketName.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
}
