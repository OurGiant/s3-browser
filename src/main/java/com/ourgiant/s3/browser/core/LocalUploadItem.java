package com.ourgiant.s3.browser.core;

import java.nio.file.Path;

// One planned upload in a batch (see core.BatchUploadPlanner / gui.BatchUploadDialog): a local
// file paired with the S3 key it's destined for.
public record LocalUploadItem(Path localPath, String key) {
}
