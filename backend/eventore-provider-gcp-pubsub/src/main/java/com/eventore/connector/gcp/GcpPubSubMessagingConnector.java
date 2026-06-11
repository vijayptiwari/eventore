package com.eventore.connector.gcp;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.connector.spi.SubscriptionKeys;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.google.api.core.ApiService;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GcpPubSubMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(GcpPubSubMessagingConnector.class);

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.GCP_PUBSUB;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        try (TopicAdminClient admin = topicAdmin(profile)) {
            admin.listTopics(ProjectName.of(projectId)).iterateAll();
        } catch (Exception e) {
            throw new IllegalStateException("GCP Pub/Sub connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        List<TopicRef> refs = new ArrayList<>();
        try (TopicAdminClient admin = topicAdmin(profile)) {
            for (Topic topic : admin.listTopics(ProjectName.of(projectId)).iterateAll()) {
                String shortName = TopicName.parse(topic.getName()).getTopic();
                refs.add(new TopicRef(shortName, "topic", ProtocolType.GCP_PUBSUB));
            }
        } catch (Exception e) {
            throw new IllegalStateException("List Pub/Sub topics failed: " + e.getMessage(), e);
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String topic = SubscribeDestinations.resolve(request).get(0);
        String projectId = CloudClientSupport.gcpProjectId(profile);
        String subName = profile.propertyOrDefault("subscription", "eventore-" + topic.replace("/", "-"));
        String key = request.getSubscriptionKey() != null
                ? request.getSubscriptionKey()
                : profile.getId() + ":" + topic;
        closeExisting(key);
        TopicName topicName = TopicName.of(projectId, topic);
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subName);
        try {
            ensureSubscription(profile, topicName, subscriptionName);
        } catch (Exception e) {
            throw new IllegalStateException("Pub/Sub subscription setup failed: " + e.getMessage(), e);
        }
        try {
            MessageReceiver receiver = (msg, consumer) -> {
                UnifiedMessage unified = new UnifiedMessage();
                unified.setConnectionId(profile.getId());
                unified.setProtocol(ProtocolType.GCP_PUBSUB);
                unified.setDestination(topic);
                unified.setDirection(MessageDirection.INBOUND);
                PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(msg.getData().toByteArray());
                unified.setPayload(decoded.text());
                unified.setContentType(decoded.contentType());
                unified.putHeader("messageId", msg.getMessageId());
                unified.putHeader("subscription", subName);
                handler.onMessage(unified);
                consumer.ack();
            };
            Subscriber subscriber = Subscriber.newBuilder(subscriptionName, receiver)
                    .setCredentialsProvider(credentials(profile))
                    .build();
            // startAsync() failures (auth, missing subscription, stream errors) surface
            // through the service listener, not as exceptions from this method.
            subscriber.addListener(new Subscriber.Listener() {
                @Override
                public void failed(ApiService.State from, Throwable failure) {
                    log.warn("Pub/Sub subscriber failed for subscription {}", subName, failure);
                    handler.onError(failure != null ? failure.getMessage() : "Pub/Sub subscriber failed");
                }
            }, MoreExecutors.directExecutor());
            subscriber.startAsync();
            subscribers.put(key, subscriber);
            return () -> {
                subscriber.stopAsync();
                subscribers.remove(key);
            };
        } catch (Exception e) {
            throw new IllegalStateException("Pub/Sub subscribe failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        String projectId = CloudClientSupport.gcpProjectId(profile);
        TopicName topicName = TopicName.of(projectId, request.getDestination());
        Publisher publisher;
        try {
            publisher = Publisher.newBuilder(topicName)
                    .setCredentialsProvider(credentials(profile))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Pub/Sub publisher init failed: " + e.getMessage(), e);
        }
        try {
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(
                            PayloadCodec.toBytes(request.getPayload(), request.getContentType())))
                    .build();
            publisher.publish(message).get();
        } catch (Exception e) {
            throw new IllegalStateException("Pub/Sub publish failed: " + e.getMessage(), e);
        } finally {
            publisher.shutdown();
        }
    }

    @Override
    public void close(String connectionId) {
        subscribers.entrySet().removeIf(e -> {
            if (SubscriptionKeys.belongsToConnection(e.getKey(), connectionId)) {
                e.getValue().stopAsync();
                return true;
            }
            return false;
        });
    }

    private void ensureSubscription(
            ConnectionProfile profile, TopicName topicName, ProjectSubscriptionName subscriptionName)
            throws Exception {
        try (SubscriptionAdminClient admin = SubscriptionAdminClient.create(
                SubscriptionAdminSettings.newBuilder()
                        .setCredentialsProvider(credentials(profile))
                        .build())) {
            try {
                admin.getSubscription(subscriptionName);
            } catch (NotFoundException notFound) {
                admin.createSubscription(
                        Subscription.newBuilder()
                                .setName(subscriptionName.toString())
                                .setTopic(topicName.toString())
                                .build());
            }
        }
    }

    private void closeExisting(String key) {
        Subscriber existing = subscribers.remove(key);
        if (existing != null) {
            existing.stopAsync();
        }
    }

    private TopicAdminClient topicAdmin(ConnectionProfile profile) throws Exception {
        return TopicAdminClient.create(
                TopicAdminSettings.newBuilder().setCredentialsProvider(credentials(profile)).build());
    }

    private CredentialsProvider credentials(ConnectionProfile profile) throws Exception {
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
