package com.eventore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.domain.bridge.ReplicationBridge;
import com.eventore.domain.bridge.ReplicationBridgeRequest;
import com.eventore.domain.bridge.ReplicationBridgeState;
import com.eventore.domain.bridge.ReplicationTestRequest;
import com.eventore.domain.bridge.ReplicationTestResult;
import com.eventore.service.SubscriptionManager.StreamEvent;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReplicationBridgeServiceTest {

    private ConnectionRegistry connectionRegistry;
    private ConnectorRegistry connectorRegistry;
    private SubscriptionManager subscriptionManager;
    private AuditService auditService;
    private ReplicationBridgeService service;

    private ConnectionProfile kafkaProfile;
    private ConnectionProfile rabbitProfile;
    private MessagingConnector kafkaConnector;
    private MessagingConnector rabbitConnector;

    @BeforeEach
    void setUp() {
        connectionRegistry = mock(ConnectionRegistry.class);
        connectorRegistry = mock(ConnectorRegistry.class);
        subscriptionManager = mock(SubscriptionManager.class);
        auditService = mock(AuditService.class);

        kafkaProfile = new ConnectionProfile();
        kafkaProfile.setId("conn-kafka");
        kafkaProfile.setName("Kafka Cluster");
        kafkaProfile.setProtocol(ProtocolType.KAFKA);
        kafkaProfile.setBrokerUrl("localhost:9092");

        rabbitProfile = new ConnectionProfile();
        rabbitProfile.setId("conn-rabbit");
        rabbitProfile.setName("RabbitMQ Cluster");
        rabbitProfile.setProtocol(ProtocolType.RABBITMQ);
        rabbitProfile.setBrokerUrl("localhost:5672");

        kafkaConnector = mock(MessagingConnector.class);
        rabbitConnector = mock(MessagingConnector.class);

        when(connectionRegistry.find("conn-kafka")).thenReturn(Optional.of(kafkaProfile));
        when(connectionRegistry.find("conn-rabbit")).thenReturn(Optional.of(rabbitProfile));
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(kafkaConnector);
        when(connectorRegistry.get(ProtocolType.RABBITMQ)).thenReturn(rabbitConnector);

        service = new ReplicationBridgeService(connectionRegistry, connectorRegistry, subscriptionManager, auditService, mock(com.eventore.security.DeploymentModePolicy.class));
    }

    @Test
    void dryRunPreservesLoopMarkersDespiteTransforms() {
        var result = service.testBridge(new ReplicationTestRequest(
                Map.of(), "hello",
                Map.of("remove:x-eventore-bridge-id", "", "x-eventore-replicated", "false"), null, true, "test-bridge"));
        assertThat(result.transformedHeaders()).containsEntry("x-eventore-bridge-id", "test-bridge");
        assertThat(result.transformedHeaders()).containsEntry("x-eventore-replicated", "true");
    }

    @Test
    void forwardsMessagesDeliveredSynchronouslyDuringSubscribe() {
        ReplicationBridge bridge = service.createBridge(new ReplicationBridgeRequest(
                "Immediate", "conn-kafka", "orders", "conn-rabbit", "orders-queue", null, null, true, false));
        when(subscriptionManager.subscribe(eq(kafkaProfile), any(), any(), eq(false))).thenAnswer(inv -> {
            Consumer<StreamEvent> callback = inv.getArgument(2);
            UnifiedMessage message = new UnifiedMessage();
            message.setPayload("first message");
            callback.accept(StreamEvent.message("sub", message));
            return "sub";
        });
        service.startBridge(bridge.id());
        verify(rabbitConnector).publish(eq(rabbitProfile), any());
        assertThat(service.getBridge(bridge.id()).orElseThrow().stats().totalReplicated()).isEqualTo(1);
    }

    @Test
    @DisplayName("createBridge validates required fields")
    void createBridgeValidation() {
        assertThatThrownBy(() -> service.createBridge(new ReplicationBridgeRequest(
                "", "conn-kafka", "source-topic", "conn-rabbit", "target-queue", null, null, true, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");

        assertThatThrownBy(() -> service.createBridge(new ReplicationBridgeRequest(
                "Bridge", "conn-unknown", "source-topic", "conn-rabbit", "target-queue", null, null, true, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source connection not found");
    }

    @Test
    @DisplayName("startBridge subscribes to source and replicates messages to target")
    @SuppressWarnings("unchecked")
    void startBridgeAndReplicate() {
        ReplicationBridgeRequest req = new ReplicationBridgeRequest(
                "Kafka-to-Rabbit",
                "conn-kafka",
                "orders",
                "conn-rabbit",
                "orders-queue",
                Map.of("rename:orderId", "x-order-id", "Environment", "Production"),
                "order-.*",
                true,
                false);

        ReplicationBridge bridge = service.createBridge(req);
        assertThat(bridge.id()).startsWith("bridge-");

        // Start bridge
        ArgumentCaptor<Consumer<StreamEvent>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(subscriptionManager.subscribe(eq(kafkaProfile), any(SubscribeRequest.class), consumerCaptor.capture(), eq(false)))
                .thenReturn("sub-123");

        ReplicationBridge started = service.startBridge(bridge.id());
        assertThat(started.stats().state()).isEqualTo(ReplicationBridgeState.RUNNING);

        // Simulate incoming message from Kafka
        UnifiedMessage msg = new UnifiedMessage();
        msg.setId("msg-1");
        msg.setDestination("orders");
        msg.setPayload("order-12345 created");
        msg.setHeaders(Map.of("orderId", "12345", "traceId", "tr-1"));
        msg.setContentType("text/plain");

        consumerCaptor.getValue().accept(StreamEvent.message("sub-123", msg));

        // Verify published to RabbitMQ
        ArgumentCaptor<PublishRequest> pubCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(rabbitConnector).publish(eq(rabbitProfile), pubCaptor.capture());

        PublishRequest published = pubCaptor.getValue();
        assertThat(published.getDestination()).isEqualTo("orders-queue");
        assertThat(published.getPayload()).isEqualTo("order-12345 created");
        assertThat(published.getHeaders()).containsEntry("x-order-id", "12345");
        assertThat(published.getHeaders()).containsEntry("Environment", "Production");
        assertThat(published.getHeaders()).containsEntry("x-eventore-bridge-id", bridge.id());
        assertThat(published.getHeaders()).containsEntry("x-eventore-replicated", "true");

        // Verify stats updated
        ReplicationBridge updated = service.getBridge(bridge.id()).orElseThrow();
        assertThat(updated.stats().totalReplicated()).isEqualTo(1L);
        assertThat(updated.stats().bytesReplicated()).isGreaterThan(0L);
        assertThat(updated.stats().lastReplicatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Replication skips messages matching loop prevention signature")
    @SuppressWarnings("unchecked")
    void loopPreventionSkipsMessage() {
        ReplicationBridgeRequest req = new ReplicationBridgeRequest(
                "Loop-Test",
                "conn-kafka",
                "orders",
                "conn-rabbit",
                "orders-queue",
                null,
                null,
                true,
                false);

        ReplicationBridge bridge = service.createBridge(req);

        ArgumentCaptor<Consumer<StreamEvent>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(subscriptionManager.subscribe(eq(kafkaProfile), any(SubscribeRequest.class), consumerCaptor.capture(), eq(false)))
                .thenReturn("sub-loop");

        service.startBridge(bridge.id());

        // Message that originated from this same bridge
        UnifiedMessage loopedMsg = new UnifiedMessage();
        loopedMsg.setId("msg-loop");
        loopedMsg.setDestination("orders");
        loopedMsg.setPayload("already replicated");
        loopedMsg.setHeaders(Map.of("x-eventore-bridge-id", bridge.id()));

        consumerCaptor.getValue().accept(StreamEvent.message("sub-loop", loopedMsg));

        verify(rabbitConnector, never()).publish(any(), any());
    }

    @Test
    @DisplayName("Replication skips messages that do not match payload filter regex")
    @SuppressWarnings("unchecked")
    void payloadFilterSkipsNonMatching() {
        ReplicationBridgeRequest req = new ReplicationBridgeRequest(
                "Filter-Test",
                "conn-kafka",
                "orders",
                "conn-rabbit",
                "orders-queue",
                null,
                "^CRITICAL:.*",
                true,
                false);

        ReplicationBridge bridge = service.createBridge(req);

        ArgumentCaptor<Consumer<StreamEvent>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(subscriptionManager.subscribe(eq(kafkaProfile), any(SubscribeRequest.class), consumerCaptor.capture(), eq(false)))
                .thenReturn("sub-filter");

        service.startBridge(bridge.id());

        UnifiedMessage nonMatching = new UnifiedMessage();
        nonMatching.setId("msg-non-match");
        nonMatching.setPayload("INFO: normal event");

        consumerCaptor.getValue().accept(StreamEvent.message("sub-filter", nonMatching));
        verify(rabbitConnector, never()).publish(any(), any());

        UnifiedMessage matching = new UnifiedMessage();
        matching.setId("msg-match");
        matching.setPayload("CRITICAL: payment gateway down");

        consumerCaptor.getValue().accept(StreamEvent.message("sub-filter", matching));
        verify(rabbitConnector).publish(eq(rabbitProfile), any(PublishRequest.class));
    }

    @Test
    @DisplayName("testBridge performs dry-run transform and filter verification")
    void testBridgeDryRun() {
        ReplicationTestRequest testReq = new ReplicationTestRequest(
                Map.of("authKey", "secret-token", "x-eventore-bridge-id", "bridge-foo"),
                "Hello Eventore Bridge",
                Map.of("rename:authKey", "x-auth-token", "app", "demo"),
                ".*Bridge.*",
                true,
                "bridge-foo");

        ReplicationTestResult result = service.testBridge(testReq);
        assertThat(result.passedFilter()).isFalse(); // False because loop was detected
        assertThat(result.loopDetected()).isTrue();
        assertThat(result.filterReason()).contains("Loop detected");

        // Non-looping test
        ReplicationTestRequest validTest = new ReplicationTestRequest(
                Map.of("traceId", "tr-123"),
                "Sensor temperature: 42C",
                Map.of("rename:traceId", "x-trace-id", "origin", "edge-sensor"),
                ".*temperature.*",
                true,
                "bridge-different");

        ReplicationTestResult validResult = service.testBridge(validTest);
        assertThat(validResult.passedFilter()).isTrue();
        assertThat(validResult.loopDetected()).isFalse();
        assertThat(validResult.transformedHeaders()).containsEntry("x-trace-id", "tr-123");
        assertThat(validResult.transformedHeaders()).containsEntry("origin", "edge-sensor");
        assertThat(validResult.transformedHeaders()).containsEntry("x-eventore-replicated", "true");
    }

    @Test
    @DisplayName("stopBridge and deleteBridge clean up active subscriptions")
    void stopAndDeleteBridge() {
        ReplicationBridgeRequest req = new ReplicationBridgeRequest(
                "Delete-Test",
                "conn-kafka",
                "events",
                "conn-rabbit",
                "events",
                null,
                null,
                false,
                false);

        ReplicationBridge bridge = service.createBridge(req);
        when(subscriptionManager.subscribe(eq(kafkaProfile), any(), any(), eq(false))).thenReturn("sub-del");

        service.startBridge(bridge.id());
        assertThat(service.getBridge(bridge.id()).orElseThrow().stats().state()).isEqualTo(ReplicationBridgeState.RUNNING);

        service.stopBridge(bridge.id());
        verify(subscriptionManager).unsubscribe("sub-del");
        assertThat(service.getBridge(bridge.id()).orElseThrow().stats().state()).isEqualTo(ReplicationBridgeState.STOPPED);

        boolean deleted = service.deleteBridge(bridge.id());
        assertThat(deleted).isTrue();
        assertThat(service.getBridge(bridge.id())).isEmpty();
    }
}
