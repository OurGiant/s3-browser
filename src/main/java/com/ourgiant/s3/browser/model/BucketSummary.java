package com.ourgiant.s3.browser.model;

// One row's worth of bucket detail shown in the bucket list (see core.BucketGridModel).
public class BucketSummary {
    public final String name;
    public final String creationDate;

    public BucketSummary(String name, String creationDate) {
        this.name = name;
        this.creationDate = creationDate;
    }
}
