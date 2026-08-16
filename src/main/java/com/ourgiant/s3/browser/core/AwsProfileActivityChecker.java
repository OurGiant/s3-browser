package com.ourgiant.s3.browser.core;

import com.ourgiant.s3.browser.model.ProfileActivity;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

// Verifies a profile's credentials via STS. Unlike dynamodb-client's equivalent, this doesn't
// also list any resources yet - there's no bucket-listing feature to populate a dropdown for
// until the first real feature (bucket/object browsing) lands.
// Makes a network call, so callers must invoke this off the EDT (e.g. from a SwingWorker).
public final class AwsProfileActivityChecker {

    private AwsProfileActivityChecker() {
    }

    public static ProfileActivity check(String profile) {
        String region = AwsProfiles.resolveRegionForProfile(profile);
        try {
            ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profile);

            String accountId;
            try (StsClient stsClient = StsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .build()) {
                GetCallerIdentityResponse identity = stsClient.getCallerIdentity(
                    GetCallerIdentityRequest.builder().build());
                accountId = identity.account();
            }

            return new ProfileActivity(true, accountId, null, region);
        } catch (Exception e) {
            return new ProfileActivity(false, null, e.getMessage(), region);
        }
    }
}
