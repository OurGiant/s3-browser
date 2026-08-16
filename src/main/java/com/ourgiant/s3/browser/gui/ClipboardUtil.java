package com.ourgiant.s3.browser.gui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

// Shared by ObjectDetailDialog's and ObjectBrowserPanel's Copy ARN/Copy S3 URL buttons.
final class ClipboardUtil {

    private ClipboardUtil() {
    }

    static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
