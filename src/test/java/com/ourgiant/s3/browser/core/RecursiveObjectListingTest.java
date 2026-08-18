package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecursiveObjectListingTest {

    @Mock
    private S3Client s3;

    @Test
    void returnsEveryObjectFromASinglePageResponse() {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
            .contents(
                S3Object.builder().key("photos/a.jpg").size(1L).build(),
                S3Object.builder().key("photos/sub/b.jpg").size(2L).build())
            .isTruncated(false)
            .build();
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        List<S3Object> result = RecursiveObjectListing.listAll(s3, "my-bucket", "photos/");

        assertEquals(List.of("photos/a.jpg", "photos/sub/b.jpg"),
            result.stream().map(S3Object::key).collect(Collectors.toList()));
    }

    @Test
    void followsContinuationTokensAcrossMultiplePages() {
        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
            .contents(S3Object.builder().key("photos/a.jpg").size(1L).build())
            .isTruncated(true)
            .nextContinuationToken("token-1")
            .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
            .contents(S3Object.builder().key("photos/b.jpg").size(2L).build())
            .isTruncated(false)
            .build();
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page1, page2);

        List<S3Object> result = RecursiveObjectListing.listAll(s3, "my-bucket", "photos/");

        assertEquals(List.of("photos/a.jpg", "photos/b.jpg"),
            result.stream().map(S3Object::key).collect(Collectors.toList()));

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3, times(2)).listObjectsV2(captor.capture());
        assertNull(captor.getAllValues().get(0).continuationToken());
        assertEquals("token-1", captor.getAllValues().get(1).continuationToken());
    }
}
