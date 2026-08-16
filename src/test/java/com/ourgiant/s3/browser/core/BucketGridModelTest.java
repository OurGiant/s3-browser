package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.BucketSummary;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.Bucket;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BucketGridModelTest {

    @Test
    void toSummaryMapsNameAndCreationDate() {
        Instant created = Instant.parse("2026-01-15T10:00:00Z");
        Bucket bucket = Bucket.builder().name("my-bucket").creationDate(created).build();

        BucketSummary summary = BucketGridModel.toSummary(bucket);

        assertEquals("my-bucket", summary.name);
        assertEquals(created.toString(), summary.creationDate);
    }

    @Test
    void toSummaryHandlesMissingCreationDate() {
        Bucket bucket = Bucket.builder().name("my-bucket").build();

        BucketSummary summary = BucketGridModel.toSummary(bucket);

        assertNull(summary.creationDate);
    }

    @Test
    void toSummariesMapsEachBucketInOrder() {
        List<Bucket> buckets = List.of(
            Bucket.builder().name("bucket-a").build(),
            Bucket.builder().name("bucket-b").build());

        List<BucketSummary> summaries = BucketGridModel.toSummaries(buckets);

        assertEquals(2, summaries.size());
        assertEquals("bucket-a", summaries.get(0).name);
        assertEquals("bucket-b", summaries.get(1).name);
    }

    @Test
    void formatRowSubstitutesPlaceholderForMissingValues() {
        BucketSummary summary = new BucketSummary(null, null);

        List<Object> row = BucketGridModel.formatRow(summary);

        assertEquals(List.of("—", "—"), row);
    }
}
