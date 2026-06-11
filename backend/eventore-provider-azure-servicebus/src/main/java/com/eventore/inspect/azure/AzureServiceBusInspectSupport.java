package com.eventore.inspect.azure;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import com.azure.messaging.servicebus.administration.models.SubscriptionProperties;
import com.azure.messaging.servicebus.administration.models.SubscriptionRuntimeProperties;
import java.util.ArrayList;
import java.util.List;

/** Azure Service Bus Admin API helpers for inspect operations. */
final class AzureServiceBusInspectSupport {

    private AzureServiceBusInspectSupport() {}

    static List<ConsumerGroupSummary> listSubscriptions(
            ConnectionProfile profile, List<TopicRef> destinations) {
        List<ConsumerGroupSummary> summaries = new ArrayList<>();
        ServiceBusAdministrationClient admin = adminClient(profile);
        for (TopicRef ref : destinations) {
            if (!"topic".equalsIgnoreCase(ref.getType())) {
                continue;
            }
            for (SubscriptionProperties sub : admin.listSubscriptions(ref.getName())) {
                ConsumerGroupSummary summary = new ConsumerGroupSummary();
                summary.setGroupId(sub.getSubscriptionName());
                summary.setState("ACTIVE");
                summary.putAttribute("topic", ref.getName());
                SubscriptionRuntimeProperties runtime =
                        admin.getSubscriptionRuntimeProperties(ref.getName(), sub.getSubscriptionName());
                summary.putAttribute("activeMessageCount", String.valueOf(runtime.getActiveMessageCount()));
                summary.putAttribute(
                        "deadLetterMessageCount", String.valueOf(runtime.getDeadLetterMessageCount()));
                summaries.add(summary);
            }
        }
        return summaries;
    }

    static ConsumerGroupDetail describeSubscription(
            ConnectionProfile profile, String groupId, List<TopicRef> destinations) {
        ServiceBusAdministrationClient admin = adminClient(profile);
        for (TopicRef ref : destinations) {
            if (!"topic".equalsIgnoreCase(ref.getType())) {
                continue;
            }
            try {
                admin.getSubscription(ref.getName(), groupId);
                SubscriptionRuntimeProperties runtime =
                        admin.getSubscriptionRuntimeProperties(ref.getName(), groupId);
                ConsumerGroupDetail detail = new ConsumerGroupDetail();
                detail.setGroupId(groupId);
                detail.setState("ACTIVE");
                detail.setPartitionAssignor(ref.getName());
                GroupOffset offset = new GroupOffset();
                offset.setTopic(ref.getName());
                offset.setLag(runtime.getActiveMessageCount());
                List<GroupOffset> offsets = new ArrayList<>();
                offsets.add(offset);
                detail.setOffsets(offsets);
                return detail;
            } catch (Exception ignored) {
                // try next topic
            }
        }
        throw new IllegalStateException("Subscription not found: " + groupId);
    }

    static List<GroupOffset> entityBacklog(
            ConnectionProfile profile, String entityId, String topicFilter, List<TopicRef> destinations) {
        ServiceBusAdministrationClient admin = adminClient(profile);
        List<GroupOffset> rows = new ArrayList<>();
        for (TopicRef ref : destinations) {
            if (topicFilter != null
                    && !topicFilter.isBlank()
                    && !ref.getName().toLowerCase().contains(topicFilter.toLowerCase())) {
                continue;
            }
            if ("queue".equalsIgnoreCase(ref.getType()) && ref.getName().equals(entityId)) {
                QueueRuntimeProperties runtime = admin.getQueueRuntimeProperties(ref.getName());
                GroupOffset offset = new GroupOffset();
                offset.setTopic(ref.getName());
                offset.setLag(runtime.getActiveMessageCount());
                rows.add(offset);
                return rows;
            }
            if ("topic".equalsIgnoreCase(ref.getType())) {
                try {
                    SubscriptionRuntimeProperties runtime =
                            admin.getSubscriptionRuntimeProperties(ref.getName(), entityId);
                    GroupOffset offset = new GroupOffset();
                    offset.setTopic(ref.getName());
                    offset.setLag(runtime.getActiveMessageCount());
                    rows.add(offset);
                    return rows;
                } catch (Exception ignored) {
                    // try next topic
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("Entity not found for backlog: " + entityId);
        }
        return rows;
    }

    static List<UnifiedMessage> peekMessages(
            ConnectionProfile profile, MessageSearchRequest request, List<TopicRef> destinations) {
        String entity = request.getTopic();
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("topic (entity path) is required for peek");
        }
        int max = request.getMaxMessages() != null ? Math.min(request.getMaxMessages(), 100) : 10;
        String subscription = request.getPartition();
        String entityType = resolveEntityType(entity, subscription, destinations);
        String connectionString = CloudClientSupport.azureConnectionString(profile);
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder().connectionString(connectionString);
        List<UnifiedMessage> messages = new ArrayList<>();
        try (ServiceBusReceiverClient receiver = buildReceiver(builder, entity, entityType, subscription)) {
            receiver.peekMessages(max).forEach(msg -> {
                UnifiedMessage unified = new UnifiedMessage();
                unified.setConnectionId(profile.getId());
                unified.setProtocol(ProtocolType.AZURE_SERVICE_BUS);
                unified.setDestination(entity);
                unified.setDirection(MessageDirection.INBOUND);
                unified.setPayload(msg.getBody() != null ? msg.getBody().toString() : "");
                unified.putHeader("messageId", msg.getMessageId());
                unified.putHeader("peek", "true");
                if (subscription != null && !subscription.isBlank()) {
                    unified.putHeader("subscription", subscription);
                }
                if (request.getPayloadContains() != null
                        && !request.getPayloadContains().isBlank()
                        && !unified.getPayload().contains(request.getPayloadContains())) {
                    return;
                }
                messages.add(unified);
            });
        }
        return messages;
    }

    static void enrichQueueDetail(ConnectionProfile profile, TopicDetailHolder holder) {
        if (!"queue".equalsIgnoreCase(holder.type())) {
            return;
        }
        QueueRuntimeProperties runtime = adminClient(profile).getQueueRuntimeProperties(holder.name());
        holder.detail().putConfig("activeMessageCount", String.valueOf(runtime.getActiveMessageCount()));
        holder.detail().putConfig("deadLetterMessageCount", String.valueOf(runtime.getDeadLetterMessageCount()));
    }

    private static ServiceBusReceiverClient buildReceiver(
            ServiceBusClientBuilder builder, String entity, String entityType, String subscription) {
        if ("topic".equalsIgnoreCase(entityType)) {
            if (subscription == null || subscription.isBlank()) {
                throw new IllegalArgumentException("partition (subscription name) is required for topic peek");
            }
            return builder.receiver().topicName(entity).subscriptionName(subscription).buildClient();
        }
        return builder.receiver().queueName(entity).buildClient();
    }

    private static String resolveEntityType(String entity, String subscription, List<TopicRef> destinations) {
        for (TopicRef ref : destinations) {
            if (ref.getName().equals(entity)) {
                return ref.getType();
            }
        }
        return subscription != null && !subscription.isBlank() ? "topic" : "queue";
    }

    private static ServiceBusAdministrationClient adminClient(ConnectionProfile profile) {
        return new ServiceBusAdministrationClientBuilder()
                .connectionString(CloudClientSupport.azureConnectionString(profile))
                .buildClient();
    }

    record TopicDetailHolder(String name, String type, com.eventore.inspect.domain.InspectModels.TopicDetail detail) {}
}
