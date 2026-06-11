package com.eventore.connector.cloud;

import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudClientSupportTest {

    @Test
    void awsRegionUsesPropertyWhenPresent() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "https://kinesis.us-west-2.amazonaws.com",
                Map.of("region", "eu-west-1"),
                null);

        assertEquals(Region.EU_WEST_1, CloudClientSupport.awsRegion(profile));
    }

    @Test
    void awsRegionFallsBackToBrokerUrlWhenNotUrl() {
        var profile = StreamTestFixtures.profile(ProtocolType.KINESIS, "ap-south-1", null, null);

        assertEquals(Region.AP_SOUTH_1, CloudClientSupport.awsRegion(profile));
    }

    @Test
    void awsRegionDefaultsWhenMissing() {
        var profile = StreamTestFixtures.profile(ProtocolType.KINESIS, "  ", null, null);

        assertEquals(Region.US_EAST_1, CloudClientSupport.awsRegion(profile));
    }

    @Test
    void awsCredentialsUsesStaticCredentialsWhenProvided() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                null,
                Map.of("accessKeyId", "AKIA123", "secretAccessKey", "secret"));

        AwsCredentialsProvider provider = CloudClientSupport.awsCredentials(profile);
        assertTrue(provider instanceof StaticCredentialsProvider);
        AwsBasicCredentials creds = (AwsBasicCredentials) provider.resolveCredentials();
        assertEquals("AKIA123", creds.accessKeyId());
        assertEquals("secret", creds.secretAccessKey());
    }

    @Test
    void awsCredentialsFallsBackToDefaultChainWhenNoneProvided() {
        var profile = StreamTestFixtures.profile(ProtocolType.KINESIS, "us-east-1", null, null);

        AwsCredentialsProvider provider = CloudClientSupport.awsCredentials(profile);

        assertTrue(provider instanceof DefaultCredentialsProvider);
    }

    @Test
    void awsCredentialsRejectsPartialCredentialsWhenAccessKeyWithoutSecret() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                null,
                Map.of("accessKeyId", "AKIA123"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> CloudClientSupport.awsCredentials(profile));
        assertTrue(ex.getMessage().contains("secretAccessKey"));
        assertTrue(ex.getMessage().contains(profile.getId()));
    }

    @Test
    void awsCredentialsRejectsBlankSecretWhenAccessKeyProvided() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                null,
                Map.of("accessKeyId", "AKIA123", "secretAccessKey", "  "));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> CloudClientSupport.awsCredentials(profile));
        assertTrue(ex.getMessage().contains("secretAccessKey"));
        assertTrue(ex.getMessage().contains(profile.getId()));
    }

    @Test
    void awsCredentialsRejectsFallbackWhenDefaultCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS, "us-east-1", Map.of("allowDefaultCredentials", "false"), null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> CloudClientSupport.awsCredentials(profile));
        assertTrue(ex.getMessage().contains("allowDefaultCredentials=false"));
        assertTrue(ex.getMessage().contains(profile.getId()));
    }

    @Test
    void requireFallbackAllowedPassesWhenPropertyUnsetOrTrue() {
        var unset = StreamTestFixtures.profile(ProtocolType.KINESIS, "us-east-1", null, null);
        var explicitTrue = StreamTestFixtures.profile(
                ProtocolType.KINESIS, "us-east-1", Map.of("allowDefaultCredentials", "true"), null);

        CloudClientSupport.requireFallbackAllowed(unset, "test mechanism");
        CloudClientSupport.requireFallbackAllowed(explicitTrue, "test mechanism");
    }

    @Test
    void gcpProjectIdUsesPropertyOrBrokerUrl() {
        var withProperty = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB, "fallback-project", Map.of("projectId", "my-project"), null);
        var withBroker = StreamTestFixtures.profile(ProtocolType.GCP_PUBSUB, "broker-project", null, null);

        assertEquals("my-project", CloudClientSupport.gcpProjectId(withProperty));
        assertEquals("broker-project", CloudClientSupport.gcpProjectId(withBroker));
    }

    @Test
    void azureConnectionStringRequired() {
        var profile = StreamTestFixtures.profile(ProtocolType.AZURE_SERVICE_BUS, "unused", null, null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> CloudClientSupport.azureConnectionString(profile));
        assertEquals("Azure connectionString credential is required", ex.getMessage());
    }

    @Test
    void azureConnectionStringReturnsCredential() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.AZURE_SERVICE_BUS,
                "unused",
                null,
                Map.of("connectionString", "Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=x"));

        assertEquals(
                "Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=x",
                CloudClientSupport.azureConnectionString(profile));
    }
}
