package com.ourgiant.s3.browser.core;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AwsConsoleLauncherTest {

    @Test
    void rejectsCredentialsWithoutASessionToken() {
        AwsBasicCredentials longTermCredentials = AwsBasicCredentials.create("AKIAEXAMPLE", "secret");

        assertThrows(IllegalArgumentException.class,
            () -> AwsConsoleLauncher.buildLoginUrl(longTermCredentials, "https://console.aws.amazon.com/"));
    }
}
