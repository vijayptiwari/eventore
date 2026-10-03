package com.eventore.dlq;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.InspectorRegistry;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.masking.MaskingService;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import com.eventore.tracing.TraceContextParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final InspectorRegistry inspectorRegistry;
    private final DeploymentModePolicy policy;
    private final AuditService auditService;
    private final MaskingService maskingService;

    @Autowired
    public DlqService(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            InspectorRegistry inspectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService,
            MaskingService maskingService) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.inspectorRegistry = inspectorRegistry;
        this.policy = policy;
        this.auditService = auditService;
        this.maskingService = maskingService != null ? maskingService : new MaskingService(null);
    }

    public DlqService(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            InspectorRegistry inspectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService) {
        this(connectionRegistry, connectorRegistry, inspectorRegistry, policy, auditService, null);
    }

    private ConnectionProfile requireProfile(String connectionId) {
        return connectionRegistry.find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found: " + connectionId));
    }

    /**
     * Lists detected Dead Letter Queue topics for a connection.
     */
    public List<DlqTopicSummary> listDlqTopics(String connectionId) {
        ConnectionProfile profile = requireProfile(connectionId);
        String customPattern = profile.property("dlqPattern");

        MessagingInspector inspector = inspectorRegistry.get(profile.getProtocol());
        if (inspector == null) {
            return List.of();
        }

        List<TopicDetail> topics;
        try {
            topics = inspector.listTopics(profile, null);
        } catch (Exception e) {
            log.warn("Failed to list topics for connection {}: {}", connectionId, e.getMessage());
            return List.of();
        }

        List<DlqTopicSummary> dlqSummaries = new ArrayList<>();
        for (TopicDetail t : topics) {
            if (t != null && DlqDetector.isDlqTopic(t.getName(), customPattern)) {
                String target = DlqDetector.inferTargetTopic(t.getName(), customPattern);
                long partitionCount = t.getPartitions() != null ? t.getPartitions().size() : 1L;
                dlqSummaries.add(new DlqTopicSummary(t.getName(), target, partitionCount, "Auto-detected DLQ pattern"));
            }
        }
        return dlqSummaries;
    }

    /**
     * Inspects messages within a DLQ topic and annotates error diagnostics.
     */
    public List<DlqMessageInfo> inspectDlqMessages(String connectionId, String dlqTopic, Integer maxMessages) {
        ConnectionProfile profile = requireProfile(connectionId);
        MessagingInspector inspector = inspectorRegistry.get(profile.getProtocol());
        if (inspector == null) {
            return List.of();
        }

        MessageSearchRequest req = new MessageSearchRequest();
        req.setTopic(dlqTopic);
        req.setMaxMessages(maxMessages != null && maxMessages > 0 ? maxMessages : 50);
        req.setStartAt("earliest");

        List<UnifiedMessage> messages;
        try {
            messages = inspector.searchMessages(profile, req);
        } catch (Exception e) {
            log.warn("Failed to search messages in DLQ topic {} for connection {}: {}", dlqTopic, connectionId, e.getMessage());
            return List.of();
        }

        List<DlqMessageInfo> infos = new ArrayList<>();
        for (UnifiedMessage m : messages) {
            if (m != null) {
                UnifiedMessage masked = maskingService.mask(m);
                TraceContextParser.enrich(masked);
                infos.add(DlqMessageInfo.from(masked));
            }
        }
        return infos;
    }

    /**
     * Redrives messages from a DLQ topic back to a destination.
     */
    public DlqRedriveResult redrive(String connectionId, DlqRedriveRequest request) {
        policy.require(Action.PUBLISH);
        if (request == null || request.sourceTopic() == null || request.sourceTopic().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceTopic is required for DLQ redrive");
        }

        ConnectionProfile profile = requireProfile(connectionId);
        policy.requireProtocol(profile.getProtocol());

        String targetTopic = request.targetTopic();
        if (targetTopic == null || targetTopic.isBlank()) {
            String customPattern = profile.property("dlqPattern");
            targetTopic = DlqDetector.inferTargetTopic(request.sourceTopic(), customPattern);
        }
        if (targetTopic.isBlank() || targetTopic.equalsIgnoreCase(request.sourceTopic())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetTopic could not be inferred from " + request.sourceTopic() + "; please specify targetTopic explicitly");
        }

        MessagingConnector connector = connectorRegistry.get(profile.getProtocol());
        if (connector == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Publish not supported for protocol " + profile.getProtocol());
        }

        // If a single message is explicitly edited
        if (request.editedPayload() != null) {
            try {
                PublishRequest pubReq = new PublishRequest();
                pubReq.setDestination(targetTopic);
                pubReq.setPayload(request.editedPayload());
                pubReq.setContentType("application/json");

                Map<String, String> headers = request.editedHeaders() != null
                        ? new HashMap<>(request.editedHeaders()) : new HashMap<>();
                headers.put("x-eventore-redriven-from", request.sourceTopic());
                headers.put("x-eventore-redrive-timestamp", Instant.now().toString());
                TraceContextParser.parse(headers).ifPresent(parent -> {
                    headers.put("traceparent", TraceContextParser.generateChildTraceparent(parent));
                });
                pubReq.setHeaders(headers);

                int bytes = PayloadCodec.toBytes(pubReq.getPayload(), pubReq.getContentType()).length;
                policy.validatePublishSize(bytes);

                connector.publish(profile, pubReq);
                auditService.publish(connectionId, profile.getProtocol(), targetTopic, bytes, "eventore-dlq-redrive");
                return DlqRedriveResult.success(request.sourceTopic(), targetTopic, 1);
            } catch (Exception e) {
                log.error("Failed to redrive single message from {} to {}: {}", request.sourceTopic(), targetTopic, e.getMessage(), e);
                return DlqRedriveResult.failure(request.sourceTopic(), targetTopic, e.getMessage());
            }
        }

        // Batch or list redrive from DLQ topic
        MessagingInspector inspector = inspectorRegistry.get(profile.getProtocol());
        if (inspector == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Inspect search not supported for protocol " + profile.getProtocol());
        }

        MessageSearchRequest searchReq = new MessageSearchRequest();
        searchReq.setTopic(request.sourceTopic());
        searchReq.setMaxMessages(request.maxMessages() != null && request.maxMessages() > 0 ? request.maxMessages() : 100);
        searchReq.setStartAt("earliest");

        List<UnifiedMessage> dlqMessages;
        try {
            dlqMessages = inspector.searchMessages(profile, searchReq);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read messages from DLQ: " + e.getMessage(), e);
        }

        int redriven = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UnifiedMessage msg : dlqMessages) {
            if (request.messageIds() != null && !request.messageIds().isEmpty() && !request.messageIds().contains(msg.getId())) {
                continue;
            }
            try {
                PublishRequest pubReq = new PublishRequest();
                pubReq.setDestination(targetTopic);
                pubReq.setPayload(msg.getPayload());
                pubReq.setContentType(msg.getContentType());

                Map<String, String> headers = new HashMap<>(msg.getHeaders());
                headers.put("x-eventore-redriven-from", request.sourceTopic());
                headers.put("x-eventore-redrive-timestamp", Instant.now().toString());
                TraceContextParser.parse(headers).ifPresent(parent -> {
                    headers.put("traceparent", TraceContextParser.generateChildTraceparent(parent));
                });
                pubReq.setHeaders(headers);

                int bytes = PayloadCodec.toBytes(pubReq.getPayload(), pubReq.getContentType()).length;
                policy.validatePublishSize(bytes);

                connector.publish(profile, pubReq);
                auditService.publish(connectionId, profile.getProtocol(), targetTopic, bytes, "eventore-dlq-redrive");
                redriven++;
            } catch (Exception ex) {
                failed++;
                errors.add("Message " + msg.getId() + " failed: " + ex.getMessage());
                log.warn("Failed to redrive message {} to {}: {}", msg.getId(), targetTopic, ex.getMessage());
            }
        }

        return DlqRedriveResult.partial(request.sourceTopic(), targetTopic, redriven, failed, errors);
    }
}
