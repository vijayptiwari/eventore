package com.eventore.connector.azure;

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
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AzureServiceBusMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(AzureServiceBusMessagingConnector.class);

    private final Map<String, ServiceBusProcessorClient> processors = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.AZURE_SERVICE_BUS;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (AdminClientHandle admin = openAdminClient(profile)) {
            admin.client().listQueues().stream().findFirst();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Azure Service Bus connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        // HTTP-based admin client; no persistent connection to close after listing.
        ServiceBusAdministrationClient admin = adminClient(profile);
        try {
            admin.listQueues().forEach(q -> refs.add(new TopicRef(q.getName(), "queue", ProtocolType.AZURE_SERVICE_BUS)));
            admin.listTopics().forEach(t -> refs.add(new TopicRef(t.getName(), "topic", ProtocolType.AZURE_SERVICE_BUS)));
        } catch (Exception e) {
            log.debug("Azure Service Bus entity listing failed for connection {}; "
                    + "returning configured default entity", profile.getId(), e);
            String fallback = profile.propertyOrDefault("entityPath", "eventore");
            refs.add(new TopicRef(fallback, profile.propertyOrDefault("entityType", "queue"), ProtocolType.AZURE_SERVICE_BUS));
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String entity = SubscribeDestinations.resolve(request).get(0);
        String entityType = Optional.ofNullable(request.getOptions()).orElseGet(Map::of)
                .getOrDefault("entityType", profile.propertyOrDefault("entityType", "queue"));
        String key = request.getSubscriptionKey() != null
                ? request.getSubscriptionKey()
                : profile.getId() + ":" + entity;
        closeExisting(key);
        String connectionString = CloudClientSupport.azureConnectionString(profile);
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder().connectionString(connectionString);
        ServiceBusProcessorClient processor;
        if ("topic".equalsIgnoreCase(entityType)) {
            String sub = profile.propertyOrDefault("subscription", "eventore-sub");
            processor = builder.processor()
                    .topicName(entity)
                    .subscriptionName(sub)
                    .processMessage(ctx -> {
                        handler.onMessage(toUnifiedMessage(profile, entity, ctx, sub));
                        ctx.complete();
                    })
                    .processError(ctx -> handler.onError(ctx.getException().getMessage()))
                    .buildProcessorClient();
        } else {
            processor = builder.processor()
                    .queueName(entity)
                    .processMessage(ctx -> {
                        handler.onMessage(toUnifiedMessage(profile, entity, ctx, null));
                        ctx.complete();
                    })
                    .processError(ctx -> handler.onError(ctx.getException().getMessage()))
                    .buildProcessorClient();
        }
        try {
            processor.start();
        } catch (Exception e) {
            try {
                processor.close();
            } catch (Exception closeError) {
                log.debug("Error closing Azure Service Bus processor after failed start", closeError);
            }
            throw new IllegalStateException("Azure Service Bus subscribe failed: " + e.getMessage(), e);
        }
        processors.put(key, processor);
        return () -> {
            processor.close();
            processors.remove(key);
        };
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        String connectionString = CloudClientSupport.azureConnectionString(profile);
        String entityType = request.getHeaders() != null
                ? request.getHeaders().getOrDefault("entityType", profile.propertyOrDefault("entityType", "queue"))
                : profile.propertyOrDefault("entityType", "queue");
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder().connectionString(connectionString);
        byte[] body = PayloadCodec.toBytes(request.getPayload(), request.getContentType());
        if ("topic".equalsIgnoreCase(entityType)) {
            try (ServiceBusSenderClient sender = builder.sender().topicName(request.getDestination()).buildClient()) {
                sender.sendMessage(new com.azure.messaging.servicebus.ServiceBusMessage(body));
            }
        } else {
            try (ServiceBusSenderClient sender = builder.sender().queueName(request.getDestination()).buildClient()) {
                sender.sendMessage(new com.azure.messaging.servicebus.ServiceBusMessage(body));
            }
        }
    }

    @Override
    public void close(String connectionId) {
        processors.entrySet().removeIf(e -> {
            if (SubscriptionKeys.belongsToConnection(e.getKey(), connectionId)) {
                e.getValue().close();
                return true;
            }
            return false;
        });
    }

    /**
     * Maps a received Service Bus message to the unified model, running the body
     * through {@link PayloadCodec} so binary payloads survive the string transport.
     * The subscription header is only present for topic subscriptions.
     */
    private static UnifiedMessage toUnifiedMessage(
            ConnectionProfile profile,
            String entity,
            ServiceBusReceivedMessageContext ctx,
            String subscription) {
        UnifiedMessage msg = new UnifiedMessage();
        msg.setConnectionId(profile.getId());
        msg.setProtocol(ProtocolType.AZURE_SERVICE_BUS);
        msg.setDestination(entity);
        msg.setDirection(MessageDirection.INBOUND);
        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(ctx.getMessage().getBody().toBytes());
        msg.setPayload(decoded.text());
        msg.setContentType(decoded.contentType());
        msg.putHeader("messageId", ctx.getMessage().getMessageId());
        if (subscription != null) {
            msg.putHeader("subscription", subscription);
        }
        return msg;
    }

    private void closeExisting(String key) {
        ServiceBusProcessorClient existing = processors.remove(key);
        if (existing != null) {
            existing.close();
        }
    }

    private ServiceBusAdministrationClient adminClient(ConnectionProfile profile) {
        return new ServiceBusAdministrationClientBuilder()
                .connectionString(CloudClientSupport.azureConnectionString(profile))
                .buildClient();
    }

    /** Scoped administration client for short-lived validate probes (SDK 7.17 has no close()). */
    private AdminClientHandle openAdminClient(ConnectionProfile profile) {
        return new AdminClientHandle(adminClient(profile));
    }

    private static final class AdminClientHandle implements AutoCloseable {
        private final ServiceBusAdministrationClient client;

        private AdminClientHandle(ServiceBusAdministrationClient client) {
            this.client = client;
        }

        private ServiceBusAdministrationClient client() {
            return client;
        }

        @Override
        public void close() {
            // HTTP administration client; SDK 7.17 has no close hook after probe completes.
        }
    }
}
