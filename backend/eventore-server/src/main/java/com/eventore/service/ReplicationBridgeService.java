package com.eventore.service;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import com.eventore.domain.bridge.ReplicationBridge;
import com.eventore.domain.bridge.ReplicationBridgeRequest;
import com.eventore.domain.bridge.ReplicationBridgeState;
import com.eventore.domain.bridge.ReplicationBridgeStats;
import com.eventore.domain.bridge.ReplicationTestRequest;
import com.eventore.domain.bridge.ReplicationTestResult;
import com.eventore.service.SubscriptionManager.StreamEvent;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core engine for managing and executing cross-broker data replication bridges (REQ-110).
 */
@Service
public class ReplicationBridgeService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationBridgeService.class);

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final SubscriptionManager subscriptionManager;
    private final AuditService auditService;

    private final Map<String, BridgeRuntime> bridges = new ConcurrentHashMap<>();

    public ReplicationBridgeService(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            SubscriptionManager subscriptionManager,
            AuditService auditService) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.subscriptionManager = subscriptionManager;
        this.auditService = auditService;
    }

    public List<ReplicationBridge> listBridges() {
        List<ReplicationBridge> result = new ArrayList<>();
        for (BridgeRuntime runtime : bridges.values()) {
            result.add(runtime.toBridge());
        }
        return result;
    }

    public Optional<ReplicationBridge> getBridge(String bridgeId) {
        BridgeRuntime runtime = bridges.get(bridgeId);
        return runtime != null ? Optional.of(runtime.toBridge()) : Optional.empty();
    }

    public ReplicationBridge createBridge(ReplicationBridgeRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Bridge name is required");
        }
        if (request.sourceConnectionId() == null || request.sourceConnectionId().isBlank()) {
            throw new IllegalArgumentException("Source connection ID is required");
        }
        if (request.sourceDestination() == null || request.sourceDestination().isBlank()) {
            throw new IllegalArgumentException("Source destination is required");
        }
        if (request.targetConnectionId() == null || request.targetConnectionId().isBlank()) {
            throw new IllegalArgumentException("Target connection ID is required");
        }
        if (request.targetDestination() == null || request.targetDestination().isBlank()) {
            throw new IllegalArgumentException("Target destination is required");
        }

        // Validate connections exist
        connectionRegistry.find(request.sourceConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Source connection not found: " + request.sourceConnectionId()));
        connectionRegistry.find(request.targetConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Target connection not found: " + request.targetConnectionId()));

        String id = "bridge-" + UUID.randomUUID().toString().substring(0, 8);
        boolean loopPrevention = request.loopPrevention() == null || request.loopPrevention();

        ReplicationBridge bridge = new ReplicationBridge(
                id,
                request.name(),
                request.sourceConnectionId(),
                request.sourceDestination(),
                request.targetConnectionId(),
                request.targetDestination(),
                request.headerTransform() != null ? new HashMap<>(request.headerTransform()) : Map.of(),
                request.payloadFilter(),
                loopPrevention,
                true,
                Instant.now().toString(),
                ReplicationBridgeStats.initial());

        BridgeRuntime runtime = new BridgeRuntime(bridge);
        bridges.put(id, runtime);

        auditService.bridgeCreated(id, request.name(), request.sourceConnectionId(), request.targetConnectionId());

        if (Boolean.TRUE.equals(request.autoStart())) {
            startBridge(id);
        }

        return runtime.toBridge();
    }

    public ReplicationBridge updateBridge(String bridgeId, ReplicationBridgeRequest request) {
        BridgeRuntime runtime = bridges.get(bridgeId);
        if (runtime == null) {
            throw new IllegalArgumentException("Bridge not found: " + bridgeId);
        }

        boolean wasRunning = runtime.state.get() == ReplicationBridgeState.RUNNING;
        if (wasRunning) {
            stopBridge(bridgeId);
        }

        boolean loopPrevention = request.loopPrevention() == null ? runtime.bridge.loopPrevention() : request.loopPrevention();

        ReplicationBridge updated = new ReplicationBridge(
                bridgeId,
                request.name() != null ? request.name() : runtime.bridge.name(),
                request.sourceConnectionId() != null ? request.sourceConnectionId() : runtime.bridge.sourceConnectionId(),
                request.sourceDestination() != null ? request.sourceDestination() : runtime.bridge.sourceDestination(),
                request.targetConnectionId() != null ? request.targetConnectionId() : runtime.bridge.targetConnectionId(),
                request.targetDestination() != null ? request.targetDestination() : runtime.bridge.targetDestination(),
                request.headerTransform() != null ? new HashMap<>(request.headerTransform()) : runtime.bridge.headerTransform(),
                request.payloadFilter() != null ? request.payloadFilter() : runtime.bridge.payloadFilter(),
                loopPrevention,
                runtime.bridge.enabled(),
                runtime.bridge.createdAt(),
                runtime.buildStats());

        runtime.bridge = updated;

        if (wasRunning || Boolean.TRUE.equals(request.autoStart())) {
            startBridge(bridgeId);
        }

        return runtime.toBridge();
    }

    public boolean deleteBridge(String bridgeId) {
        BridgeRuntime runtime = bridges.remove(bridgeId);
        if (runtime != null) {
            if (runtime.activeSubscriptionId != null) {
                try {
                    subscriptionManager.unsubscribe(runtime.activeSubscriptionId);
                } catch (Exception e) {
                    log.warn("Error unsubscribing bridge {} during delete: {}", bridgeId, e.getMessage());
                }
            }
            auditService.bridgeDeleted(bridgeId);
            return true;
        }
        return false;
    }

    public synchronized ReplicationBridge startBridge(String bridgeId) {
        BridgeRuntime runtime = bridges.get(bridgeId);
        if (runtime == null) {
            throw new IllegalArgumentException("Bridge not found: " + bridgeId);
        }

        if (runtime.state.get() == ReplicationBridgeState.RUNNING && runtime.activeSubscriptionId != null) {
            return runtime.toBridge();
        }

        ConnectionProfile sourceProfile = connectionRegistry.find(runtime.bridge.sourceConnectionId())
                .orElseThrow(() -> new IllegalStateException("Source connection missing: " + runtime.bridge.sourceConnectionId()));

        connectionRegistry.find(runtime.bridge.targetConnectionId())
                .orElseThrow(() -> new IllegalStateException("Target connection missing: " + runtime.bridge.targetConnectionId()));

        SubscribeRequest subReq = new SubscribeRequest();
        subReq.setDestination(runtime.bridge.sourceDestination());
        try {
            String subscriptionId = subscriptionManager.subscribe(
                    sourceProfile,
                    subReq,
                    event -> onStreamEvent(bridgeId, event),
                    false);

            runtime.activeSubscriptionId = subscriptionId;
            runtime.state.set(ReplicationBridgeState.RUNNING);
            runtime.lastError.set(null);
            auditService.bridgeStarted(bridgeId);
            log.info("Started replication bridge {} ({} -> {}) with subscription {}",
                    bridgeId, runtime.bridge.sourceDestination(), runtime.bridge.targetDestination(), subscriptionId);
        } catch (Exception e) {
            runtime.state.set(ReplicationBridgeState.ERROR);
            runtime.lastError.set(e.getMessage());
            runtime.errorsCount.incrementAndGet();
            log.error("Failed to start replication bridge {}: {}", bridgeId, e.getMessage(), e);
            throw new IllegalStateException("Failed to start replication bridge: " + e.getMessage(), e);
        }

        return runtime.toBridge();
    }

    public synchronized ReplicationBridge stopBridge(String bridgeId) {
        BridgeRuntime runtime = bridges.get(bridgeId);
        if (runtime == null) {
            throw new IllegalArgumentException("Bridge not found: " + bridgeId);
        }

        if (runtime.activeSubscriptionId != null) {
            try {
                subscriptionManager.unsubscribe(runtime.activeSubscriptionId);
            } catch (Exception e) {
                log.warn("Error stopping subscription for bridge {}: {}", bridgeId, e.getMessage());
            }
            runtime.activeSubscriptionId = null;
        }

        runtime.state.set(ReplicationBridgeState.STOPPED);
        auditService.bridgeStopped(bridgeId);
        log.info("Stopped replication bridge {}", bridgeId);
        return runtime.toBridge();
    }

    public ReplicationTestResult testBridge(ReplicationTestRequest request) {
        Map<String, String> headers = request.headers() != null ? new HashMap<>(request.headers()) : new HashMap<>();
        String payload = request.payload() != null ? request.payload() : "";
        boolean loopPrevention = request.loopPrevention() == null || request.loopPrevention();
        String bridgeId = request.bridgeId() != null ? request.bridgeId() : "test-bridge";

        // Check loop prevention
        boolean loopDetected = false;
        if (loopPrevention) {
            String existingBridge = headers.get("x-eventore-bridge-id");
            if (existingBridge != null && (existingBridge.equals(bridgeId) || "true".equalsIgnoreCase(headers.get("x-eventore-replicated")))) {
                loopDetected = true;
            }
        }

        // Check payload filter
        boolean passedFilter = true;
        String filterReason = "Payload passed all filter checks";
        if (request.payloadFilter() != null && !request.payloadFilter().isBlank()) {
            try {
                Pattern pattern = Pattern.compile(request.payloadFilter());
                if (!pattern.matcher(payload).find()) {
                    passedFilter = false;
                    filterReason = "Payload does not match regex filter: " + request.payloadFilter();
                }
            } catch (Exception e) {
                passedFilter = false;
                filterReason = "Invalid regex filter: " + e.getMessage();
            }
        }

        if (loopDetected) {
            filterReason = "Loop detected: message contains replication loop metadata (x-eventore-bridge-id / x-eventore-replicated)";
        }

        // Apply header transform
        Map<String, String> transformedHeaders = applyHeaderTransform(headers, request.headerTransform(), bridgeId, "test-source", "test-dest");

        return new ReplicationTestResult(
                passedFilter && !loopDetected,
                loopDetected,
                transformedHeaders,
                payload,
                filterReason);
    }

    private void onStreamEvent(String bridgeId, StreamEvent event) {
        BridgeRuntime runtime = bridges.get(bridgeId);
        if (runtime == null || runtime.state.get() != ReplicationBridgeState.RUNNING) {
            return;
        }

        UnifiedMessage msg = event.message();
        if (msg == null) {
            return;
        }

        try {
            // Loop prevention check
            Map<String, String> headers = msg.getHeaders() != null ? new HashMap<>(msg.getHeaders()) : new HashMap<>();
            if (runtime.bridge.loopPrevention()) {
                String existingBridge = headers.get("x-eventore-bridge-id");
                if (bridgeId.equals(existingBridge) || "true".equalsIgnoreCase(headers.get("x-eventore-replicated"))) {
                    log.debug("Dropping replicated message on bridge {} to prevent loop", bridgeId);
                    return;
                }
            }

            // Payload filter check
            String payload = msg.getPayload() != null ? msg.getPayload() : "";
            if (runtime.bridge.payloadFilter() != null && !runtime.bridge.payloadFilter().isBlank()) {
                Pattern pattern = Pattern.compile(runtime.bridge.payloadFilter());
                if (!pattern.matcher(payload).find()) {
                    log.debug("Message skipped by filter on bridge {}: {}", bridgeId, runtime.bridge.payloadFilter());
                    return;
                }
            }

            // Target connection & connector resolution
            ConnectionProfile targetProfile = connectionRegistry.find(runtime.bridge.targetConnectionId())
                    .orElseThrow(() -> new IllegalStateException("Target connection missing: " + runtime.bridge.targetConnectionId()));

            MessagingConnector targetConnector = connectorRegistry.get(targetProfile.getProtocol());
            if (targetConnector == null) {
                throw new IllegalStateException("No connector available for protocol: " + targetProfile.getProtocol());
            }

            // Transform headers
            Map<String, String> transformedHeaders = applyHeaderTransform(
                    headers,
                    runtime.bridge.headerTransform(),
                    bridgeId,
                    runtime.bridge.sourceConnectionId(),
                    runtime.bridge.sourceDestination());

            // Build publish request
            PublishRequest pubReq = new PublishRequest();
            pubReq.setDestination(runtime.bridge.targetDestination());
            pubReq.setPayload(payload);
            pubReq.setHeaders(transformedHeaders);
            pubReq.setContentType(msg.getContentType() != null ? msg.getContentType() : "text/plain");

            // Execute publish
            targetConnector.publish(targetProfile, pubReq);

            // Record stats
            runtime.totalReplicated.incrementAndGet();
            runtime.bytesReplicated.addAndGet(payload.getBytes(StandardCharsets.UTF_8).length);
            runtime.lastReplicatedAt.set(Instant.now().toString());
        } catch (Exception e) {
            runtime.errorsCount.incrementAndGet();
            runtime.lastError.set(e.getMessage());
            log.warn("Error replicating message on bridge {}: {}", bridgeId, e.getMessage());
        }
    }

    private Map<String, String> applyHeaderTransform(
            Map<String, String> original,
            Map<String, String> transformRules,
            String bridgeId,
            String sourceConn,
            String sourceDest) {
        Map<String, String> headers = new HashMap<>(original);

        // Core replication provenance headers
        headers.put("x-eventore-bridge-id", bridgeId);
        headers.put("x-eventore-replicated", "true");
        headers.put("x-eventore-source-connection", sourceConn);
        headers.put("x-eventore-source-destination", sourceDest);
        headers.put("x-eventore-replicated-at", Instant.now().toString());

        // Custom transformation rules (e.g. rename:old->new or key->value)
        if (transformRules != null) {
            for (Map.Entry<String, String> entry : transformRules.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.startsWith("rename:")) {
                    String oldKey = key.substring("rename:".length());
                    if (headers.containsKey(oldKey)) {
                        String oldVal = headers.remove(oldKey);
                        headers.put(value, oldVal);
                    }
                } else if (key.startsWith("remove:")) {
                    String removeKey = key.substring("remove:".length());
                    headers.remove(removeKey);
                } else {
                    headers.put(key, value);
                }
            }
        }

        return headers;
    }

    @PreDestroy
    public void cleanup() {
        for (BridgeRuntime runtime : bridges.values()) {
            if (runtime.activeSubscriptionId != null) {
                try {
                    subscriptionManager.unsubscribe(runtime.activeSubscriptionId);
                } catch (Exception e) {
                    // ignore on shutdown
                }
            }
        }
        bridges.clear();
    }

    private static class BridgeRuntime {
        volatile ReplicationBridge bridge;
        volatile String activeSubscriptionId;
        final AtomicLong totalReplicated = new AtomicLong();
        final AtomicLong bytesReplicated = new AtomicLong();
        final AtomicLong errorsCount = new AtomicLong();
        final AtomicReference<String> lastReplicatedAt = new AtomicReference<>();
        final AtomicReference<String> lastError = new AtomicReference<>();
        final AtomicReference<ReplicationBridgeState> state = new AtomicReference<>(ReplicationBridgeState.STOPPED);

        BridgeRuntime(ReplicationBridge bridge) {
            this.bridge = bridge;
        }

        ReplicationBridgeStats buildStats() {
            return new ReplicationBridgeStats(
                    totalReplicated.get(),
                    bytesReplicated.get(),
                    errorsCount.get(),
                    lastReplicatedAt.get(),
                    lastError.get(),
                    state.get());
        }

        ReplicationBridge toBridge() {
            return new ReplicationBridge(
                    bridge.id(),
                    bridge.name(),
                    bridge.sourceConnectionId(),
                    bridge.sourceDestination(),
                    bridge.targetConnectionId(),
                    bridge.targetDestination(),
                    bridge.headerTransform(),
                    bridge.payloadFilter(),
                    bridge.loopPrevention(),
                    bridge.enabled(),
                    bridge.createdAt(),
                    buildStats());
        }
    }
}
