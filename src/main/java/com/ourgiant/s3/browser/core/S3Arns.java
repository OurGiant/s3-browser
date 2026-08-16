package com.ourgiant.s3.browser.core;

// Pure ARN/URI-string construction for the "copy ARN" / "copy S3 URL" actions (see
// gui.ObjectDetailDialog and gui.ObjectBrowserPanel). S3 bucket ARNs have no region/account
// segment (bucket names are globally unique), unlike most other AWS resource ARNs - a bare
// "arn:aws:s3:::bucket-name" is the whole thing.
public final class S3Arns {

    private S3Arns() {
    }

    public static String bucketArn(String bucketName) {
        return "arn:aws:s3:::" + bucketName;
    }

    public static String objectArn(String bucketName, String key) {
        return "arn:aws:s3:::" + bucketName + "/" + key;
    }

    public static String bucketUrl(String bucketName) {
        return "s3://" + bucketName + "/";
    }

    public static String objectUrl(String bucketName, String key) {
        return "s3://" + bucketName + "/" + key;
    }
}
