package com.eventore.connector.cloud;

import com.eventore.domain.ConnectionProfile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public final class CloudClientSupport {

    private CloudClientSupport() {}

    public static Region awsRegion(ConnectionProfile profile) {
        String region = profile.propertyOrDefault("region", profile.getBrokerUrl());
        if (region == null || region.isBlank()) {
            return Region.US_EAST_1;
        }
        String r = region.trim();
        if (r.contains("://")) {
            r = "us-east-1";
        }
        return Region.of(r);
    }

    public static AwsCredentialsProvider awsCredentials(ConnectionProfile profile) {
        String key = profile.credential("accessKeyId");
        String secret = profile.credential("secretAccessKey");
        if (key != null && !key.isBlank() && secret != null) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret));
        }
        return DefaultCredentialsProvider.create();
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
