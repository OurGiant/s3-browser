package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.BucketSummary;
import software.amazon.awssdk.services.s3.model.Bucket;

import java.util.ArrayList;
import java.util.List;

// Pure mapping/formatting logic behind the bucket list grid, kept separate from the
// JTable/DefaultTableModel that actually displays it - same split lambda-inspector's
// FunctionGridModel makes for its functions grid.
public final class BucketGridModel {

    public static final List<String> COLUMN_NAMES = List.of("Bucket Name", "Created");

    private static final String UNKNOWN = "—";

    private BucketGridModel() {
    }

    public static BucketSummary toSummary(Bucket bucket) {
        String created = bucket.creationDate() != null ? bucket.creationDate().toString() : null;
        return new BucketSummary(bucket.name(), created);
    }

    public static List<BucketSummary> toSummaries(List<Bucket> buckets) {
        List<BucketSummary> summaries = new ArrayList<>();
        for (Bucket bucket : buckets) {
            summaries.add(toSummary(bucket));
        }
        return summaries;
    }

    public static List<Object> formatRow(BucketSummary summary) {
        List<Object> row = new ArrayList<>();
        row.add(summary.name != null ? summary.name : UNKNOWN);
        row.add(summary.creationDate != null ? summary.creationDate : UNKNOWN);
        return row;
    }
}
