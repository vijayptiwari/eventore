package com.eventore.connector.cloud;

import com.eventore.domain.ConnectionProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public final class CloudClientSupport {

    private static final Logger log = LoggerFactory.getLogger(CloudClientSupport.class);

    private CloudClientSupport() {}

    public static Region awsRegion(ConnectionProfile profile) {
        String region = profile.propertyOrDefault("region", profile.getBrokerUrl());
        if (region == null || region.isBlank()) {
            return Region.US_EAST_1;
        }
        String r = region.trim();
        if (r.contains("://")) {
            log.warn(
                    "Connection '{}' has a URL-like region value '{}'; falling back to us-east-1. "
                            + "Set the 'region' property to a valid AWS region",
                    profile.getId(),
                    r);
            r = "us-east-1";
        }
        return Region.of(r);
    }

    public static AwsCredentialsProvider awsCredentials(ConnectionProfile profile) {
        String key = profile.credential("accessKeyId");
        String secret = profile.credential("secretAccessKey");
        if (key != null && !key.isBlank()) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException(
                        "secretAccessKey credential is required when accessKeyId is set for connection '"
                                + profile.getId() + "'");
            }
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret));
        }
        requireFallbackAllowed(profile, "AWS default credentials chain");
        log.warn(
                "Connection '{}' has no explicit AWS credentials; falling back to the default "
                        + "credentials chain (env vars, instance profile, etc.)",
                profile.getId());
        return DefaultCredentialsProvider.create();
    }

    /**
     * Profiles can opt out of ambient/default cloud credentials by setting the
     * property "allowDefaultCredentials" to "false". This prevents silent use of
     * instance roles or Application Default Credentials in shared deployments.
     */
    public static void requireFallbackAllowed(ConnectionProfile profile, String mechanism) {
        if ("false".equalsIgnoreCase(profile.propertyOrDefault("allowDefaultCredentials", "true"))) {
            throw new IllegalArgumentException(
                    "Explicit credentials are required for connection '" + profile.getId()
                            + "' (allowDefaultCredentials=false); refusing to use " + mechanism);
        }
    }

    public static String gcpProjectId(ConnectionProfile profile) {
        return profile.propertyOrDefault("projectId", profile.getBrokerUrl());
    }

    public static String azureConnectionString(ConnectionProfile profile) {
        String cs = profile.credential("connectionString");
        if (cs == null || cs.isBlank()) {
            throw new IllegalArgumentException("Azure connectionString credential is required");
        }
        return cs;
    }
}
