package com.eventore.inspect.gcp;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.domain.ConnectionProfile;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GCP Pub/Sub Admin API helpers for inspect operations. */
final class GcpPubSubInspectSupport {

    private static final Logger log = LoggerFactory.getLogger(GcpPubSubInspectSupport.class);

    private GcpPubSubInspectSupport() {}

    static List<ConsumerGroupSummary> listSubscriptions(ConnectionProfile profile) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        List<ConsumerGroupSummary> summaries = new ArrayList<>();
        try (SubscriptionAdminClient admin = subscriptionAdmin(profile)) {
            for (Subscription sub : admin.listSubscriptions(ProjectName.of(projectId)).iterateAll()) {
                ConsumerGroupSummary summary = new ConsumerGroupSummary();
                ProjectSubscriptionName name = ProjectSubscriptionName.parse(sub.getName());
                summary.setGroupId(name.getSubscription());
                summary.setState("ACTIVE");
                summary.putAttribute("topic", shortTopicName(sub.getTopic()));
                if (sub.hasPushConfig() && !sub.getPushConfig().getPushEndpoint().isBlank()) {
                    summary.putAttribute("pushEndpoint", sub.getPushConfig().getPushEndpoint());
                }
                summaries.add(summary);
            }
        } catch (Exception e) {
            throw new IllegalStateException("List Pub/Sub subscriptions failed: " + e.getMessage(), e);
        }
        return summaries;
    }

    static ConsumerGroupDetail describeSubscription(ConnectionProfile profile, String subscriptionId) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        ConsumerGroupDetail detail = new ConsumerGroupDetail();
        detail.setGroupId(subscriptionId);
        try (SubscriptionAdminClient admin = subscriptionAdmin(profile)) {
            Subscription sub = admin.getSubscription(ProjectSubscriptionName.of(projectId, subscriptionId));
            detail.setState("ACTIVE");
            detail.setPartitionAssignor(shortTopicName(sub.getTopic()));
            GroupOffset offset = new GroupOffset();
            offset.setTopic(shortTopicName(sub.getTopic()));
            offset.setLag(readBacklogCount(sub));
            offset.setOldestUnackedMessageAge(readOldestUnackedAge(sub));
            List<GroupOffset> offsets = new ArrayList<>();
            offsets.add(offset);
            detail.setOffsets(offsets);
        } catch (Exception e) {
            throw new IllegalStateException("Describe Pub/Sub subscription failed: " + e.getMessage(), e);
        }
        return detail;
    }

    static List<GroupOffset> subscriptionBacklog(
            ConnectionProfile profile, String subscriptionId, String topicFilter) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        List<GroupOffset> rows = new ArrayList<>();
        try (SubscriptionAdminClient admin = subscriptionAdmin(profile)) {
            Subscription sub = admin.getSubscription(ProjectSubscriptionName.of(projectId, subscriptionId));
            String topic = shortTopicName(sub.getTopic());
            if (topicFilter != null
                    && !topicFilter.isBlank()
                    && !topic.toLowerCase().contains(topicFilter.toLowerCase())) {
                return rows;
            }
            GroupOffset offset = new GroupOffset();
            offset.setTopic(topic);
            offset.setLag(readBacklogCount(sub));
            offset.setOldestUnackedMessageAge(readOldestUnackedAge(sub));
            rows.add(offset);
        } catch (Exception e) {
            throw new IllegalStateException("Pub/Sub subscription backlog failed: " + e.getMessage(), e);
        }
        return rows;
    }

    private static long readBacklogCount(Subscription sub) {
        if (sub.getUnknownFields().hasField(7)) {
            try {
                return sub.getUnknownFields().getField(7).getVarintList().getFirst();
            } catch (Exception e) {
                log.trace("Could not parse numUndeliveredMessages from subscription {}", sub.getName(), e);
            }
        }
        return 0;
    }

    private static Long readOldestUnackedAge(Subscription sub) {
        if (sub.getUnknownFields().hasField(8)) {
            try {
                return sub.getUnknownFields().getField(8).getVarintList().getFirst();
            } catch (Exception e) {
                log.trace("Could not parse oldestUnackedMessageAge from subscription {}", sub.getName(), e);
            }
        }
        return null;
    }

    private static String shortTopicName(String topicResource) {
        return TopicName.parse(topicResource).getTopic();
    }

    private static SubscriptionAdminClient subscriptionAdmin(ConnectionProfile profile) throws Exception {
        return SubscriptionAdminClient.create(
                SubscriptionAdminSettings.newBuilder().setCredentialsProvider(credentials(profile)).build());
    }

    private static CredentialsProvider credentials(ConnectionProfile profile) throws Exception {
        String json = profile.credential("serviceAccountJson");
        if (json != null && !json.isBlank()) {
            GoogleCredentials creds =
                    GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            return FixedCredentialsProvider.create(creds);
        }
        CloudClientSupport.requireFallbackAllowed(profile, "GCP Application Default Credentials");
        log.warn(
                "Connection '{}' has no serviceAccountJson credential; falling back to "
                        + "Application Default Credentials",
                profile.getId());
        return FixedCredentialsProvider.create(GoogleCredentials.getApplicationDefault());
    }
}
