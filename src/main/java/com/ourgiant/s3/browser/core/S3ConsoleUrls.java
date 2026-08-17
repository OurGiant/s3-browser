package com.ourgiant.s3.browser.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Pure S3 console URL construction for the "Open in Console" action (see
// gui.ObjectBrowserPanel and core.AwsConsoleLauncher, whose Destination parameter this becomes).
// Targets the region-specific console subdomain to avoid an extra global-endpoint redirect hop.
public final class S3ConsoleUrls {

    private S3ConsoleUrls() {
    }

    public static String bucketPrefixUrl(String bucket, String prefix, String region) {
        StringBuilder url = new StringBuilder("https://")
            .append(region)
            .append(".console.aws.amazon.com/s3/buckets/")
            .append(bucket)
            .append("?region=").append(region)
            .append("&bucketType=general");
        if (prefix != null && !prefix.isEmpty()) {
            url.append("&prefix=").append(encodePrefix(prefix));
        }
        return url.toString();
    }

    // Percent-encodes each path segment for safe use as a URI query value, but leaves the "/"
    // separators between segments unencoded - matching how the console's own folder-navigation
    // links represent a prefix, rather than collapsing it to an opaque %2F-encoded blob.
    private static String encodePrefix(String prefix) {
        String[] segments = prefix.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
