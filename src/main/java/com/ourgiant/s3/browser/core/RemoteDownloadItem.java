package com.ourgiant.s3.browser.core;

import java.nio.file.Path;

// One planned download in a batch (see core.BatchDownloadPlanner / gui.BatchDownloadDialog): an
// S3 key/size paired with the local path it's destined for.
public record RemoteDownloadItem(String key, long size, Path localPath) {
}
