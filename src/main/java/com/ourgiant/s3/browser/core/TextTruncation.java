package com.ourgiant.s3.browser.core;

// Pure display-text truncation, used wherever a detail dialog shows a value that could be much
// longer than reasonable to lay out inline (an ARN, an S3 URL, an ETag, a deeply nested key) -
// see gui.ObjectDetailDialog. Without capping display length, a layout manager sizing a column
// to its widest unwrapped string can blow the whole dialog's preferred width out past what's
// visible, pushing later content (e.g. a Copy button on the same row) out of view.
public final class TextTruncation {

    private TextTruncation() {
    }

    /** Null becomes "—". A value at or under maxLength is returned unchanged. */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "—";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 1) + "…" : value;
    }

    public static boolean wasTruncated(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }
}
