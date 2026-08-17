package com.ourgiant.s3.browser.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.s3.browser.util.HttpClientFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Builds a one-time AWS Management Console sign-in URL from temporary credentials via the AWS
 * federation endpoint (see
 * https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_enable-console-custom-url.html),
 * ported from aws-idp-saml-ui's core.AwsConsoleLauncher for the "Open in Console" action (see
 * gui.ObjectBrowserPanel). SessionDuration must NOT be passed to the federation endpoint - it's
 * rejected for AssumeRole/SSO-derived credentials, which is what a role or SSO profile resolved
 * via ProfileCredentialsProvider produces.
 */
public final class AwsConsoleLauncher {
    private static final String FEDERATION_ENDPOINT = "https://signin.aws.amazon.com/federation";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AwsConsoleLauncher() {
    }

    /**
     * Does a real network call - run this off the EDT (e.g. from a SwingWorker).
     *
     * @throws IllegalArgumentException if {@code credentials} aren't temporary (no session
     *     token) - the federation endpoint only accepts AssumeRole/SSO-derived credentials, not
     *     long-term IAM user access keys.
     */
    public static String buildLoginUrl(AwsCredentials credentials, String destinationUrl) throws Exception {
        if (!(credentials instanceof AwsSessionCredentials sessionCredentials)) {
            throw new IllegalArgumentException(
                "This profile's credentials aren't temporary (no session token) - only a "
                    + "role-assumption or SSO profile can open the AWS Console this way.");
        }

        String sessionJson = "{\"sessionId\":\"" + jsonEscape(sessionCredentials.accessKeyId())
            + "\",\"sessionKey\":\"" + jsonEscape(sessionCredentials.secretAccessKey())
            + "\",\"sessionToken\":\"" + jsonEscape(sessionCredentials.sessionToken()) + "\"}";

        String signinTokenUrl = FEDERATION_ENDPOINT + "?Action=getSigninToken&Session="
            + URLEncoder.encode(sessionJson, StandardCharsets.UTF_8);

        HttpClient client = HttpClientFactory.create(CONNECT_TIMEOUT);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(signinTokenUrl))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("AWS federation endpoint returned HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        String signinToken = root.path("SigninToken").asText(null);
        if (signinToken == null) {
            throw new IllegalStateException("AWS federation endpoint did not return a sign-in token");
        }

        return FEDERATION_ENDPOINT + "?Action=login"
            + "&Destination=" + URLEncoder.encode(destinationUrl, StandardCharsets.UTF_8)
            + "&SigninToken=" + URLEncoder.encode(signinToken, StandardCharsets.UTF_8);
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
