package com.eventore.connector.azure;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AzureServiceBusMessagingConnector implements MessagingConnector {

    private final Map<String, ServiceBusProcessorClient> processors = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.AZURE_SERVICE_BUS;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        ServiceBusAdministrationClient admin = adminClient(profile);
        try {
            admin.listQueues().stream().findFirst();
        } catch (Exception e) {
            throw new IllegalStateException("Azure Service Bus connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        ServiceBusAdministrationClient admin = adminClient(profile);
        try {
            admin.listQueues().forEach(q -> refs.add(new TopicRef(q.getName(), "queue", ProtocolType.AZURE_SERVICE_BUS)));
            admin.listTopics().forEach(t -> refs.add(new TopicRef(t.getName(), "topic", ProtocolType.AZURE_SERVICE_BUS)));
        } catch (Exception e) {
            String fallback = profile.propertyOrDefault("entityPath", "eventore");
            refs.add(new TopicRef(fallback, profile.propertyOrDefault("entityType", "queue"), ProtocolType.AZURE_SERVICE_BUS));
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String entity = SubscribeDestinations.resolve(request).get(0);
        String entityType = request.getOptions().getOrDefault("entityType", profile.propertyOrDefault("entityType", "queue"));
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
                        UnifiedMessage msg = new UnifiedMessage();
                        msg.setConnectionId(profile.getId());
                        msg.setProtocol(ProtocolType.AZURE_SERVICE_BUS);
                        msg.setDestination(entity);
                        msg.setDirection(MessageDirection.INBOUND);
                        msg.setPayload(new String(ctx.getMessage().getBody().toBytes(), StandardCharsets.UTF_8));
                        msg.getHeaders().put("messageId", ctx.getMessage().getMessageId());
                        msg.getHeaders().put("subscription", sub);
                        handler.onMessage(msg);
                    })
                    .processError(ctx -> handler.onError(ctx.getException().getMessage()))
                    .buildProcessorClient();
        } else {
            processor = builder.processor()
                    .queueName(entity)
                    .processMessage(ctx -> {
                        UnifiedMessage msg = new UnifiedMessage();
                        msg.setConnectionId(profile.getId());
                        msg.setProtocol(ProtocolType.AZURE_SERVICE_BUS);
                        msg.setDestination(entity);
                        msg.setDirection(MessageDirection.INBOUND);
                        msg.setPayload(new String(ctx.getMessage().getBody().toBytes(), StandardCharsets.UTF_8));
                        msg.getHeaders().put("messageId", ctx.getMessage().getMessageId());
                        handler.onMessage(msg);
                    })
                    .processError(ctx -> handler.onError(ctx.getException().getMessage()))
                    .buildProcessorClient();
        }
        processor.start();
        processors.put(key, processor);
        return () -> {
            processor.close();
            processors.remove(key);
        };
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        String connectionString = CloudClientSupport.azureConnectionString(profile);
        String entityType = profile.propertyOrDefault("entityType", "queue");
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder().connectionString(connectionString);
        if ("topic".equalsIgnoreCase(entityType)) {
            try (ServiceBusSenderClient sender = builder.sender().topicName(request.getDestination()).buildClient()) {
                sender.sendMessage(new com.azure.messaging.servicebus.ServiceBusMessage(
                        request.getPayload() != null ? request.getPayload() : ""));
            }
        } else {
            try (ServiceBusSenderClient sender = builder.sender().queueName(request.getDestination()).buildClient()) {
                sender.sendMessage(new com.azure.messaging.servicebus.ServiceBusMessage(
                        request.getPayload() != null ? request.getPayload() : ""));
            }
        }
    }

    @Override
    public void close(String connectionId) {
        processors.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(connectionId)) {
                e.getValue().close();
                return true;
            }
            return false;
        });
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
}
