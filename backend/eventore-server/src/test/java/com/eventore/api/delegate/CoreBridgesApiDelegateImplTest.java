package com.eventore.api.delegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eventore.domain.bridge.ReplicationBridge;
import com.eventore.domain.bridge.ReplicationBridgeRequest;
import com.eventore.domain.bridge.ReplicationBridgeState;
import com.eventore.domain.bridge.ReplicationBridgeStats;
import com.eventore.domain.bridge.ReplicationTestRequest;
import com.eventore.domain.bridge.ReplicationTestResult;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ReplicationBridgeService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class CoreBridgesApiDelegateImplTest {

    private ReplicationBridgeService service;
    private DeploymentModePolicy policy;
    private CoreBridgesApiDelegateImpl delegate;

    private ReplicationBridge sampleBridge;

    @BeforeEach
    void setUp() {
        service = mock(ReplicationBridgeService.class);
        policy = mock(DeploymentModePolicy.class);
        delegate = new CoreBridgesApiDelegateImpl(service, policy);

        sampleBridge = new ReplicationBridge(
                "bridge-1",
                "Kafka to RabbitMQ",
                "conn-kafka",
                "orders",
                "conn-rabbit",
                "orders-queue",
                Map.of("Env", "Prod"),
                null,
                true,
                true,
                "2026-10-04T00:00:00Z",
                new ReplicationBridgeStats(100L, 5000L, 0L, "2026-10-04T01:00:00Z", null, ReplicationBridgeState.RUNNING));
    }

    @Test
    @DisplayName("GET /api/v1/bridges returns 200 with list of bridges")
    void listBridgesReturnsList() {
        when(service.listBridges()).thenReturn(List.of(sampleBridge));

        ResponseEntity<List<ReplicationBridge>> response = delegate.listBridges();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("Kafka to RabbitMQ");
    }

    @Test
    @DisplayName("POST /api/v1/bridges returns 201 with created bridge")
    void createBridgeReturnsCreated() {
        ReplicationBridgeRequest request = new ReplicationBridgeRequest(
                "Kafka to RabbitMQ", "conn-kafka", "orders", "conn-rabbit", "orders-queue", null, null, true, false);

        when(service.createBridge(any())).thenReturn(sampleBridge);

        ResponseEntity<ReplicationBridge> response = delegate.createBridge(request);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("bridge-1");
    }

    @Test
    @DisplayName("GET /api/v1/bridges/{id} returns 200 or 404")
    void getBridgeDetail() {
        when(service.getBridge("bridge-1")).thenReturn(Optional.of(sampleBridge));
        when(service.getBridge("unknown")).thenReturn(Optional.empty());

        ResponseEntity<ReplicationBridge> ok = delegate.getBridge("bridge-1");
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody().id()).isEqualTo("bridge-1");

        ResponseEntity<ReplicationBridge> notFound = delegate.getBridge("unknown");
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /api/v1/bridges/{id}/start and /stop control bridge lifecycle")
    void startAndStopLifecycle() {
        when(service.startBridge("bridge-1")).thenReturn(sampleBridge);
        when(service.stopBridge("bridge-1")).thenReturn(sampleBridge);

        ResponseEntity<ReplicationBridge> started = delegate.startBridge("bridge-1");
        assertThat(started.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<ReplicationBridge> stopped = delegate.stopBridge("bridge-1");
        assertThat(stopped.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("DELETE /api/v1/bridges/{id} returns 204 or 404")
    void deleteBridge() {
        when(service.deleteBridge("bridge-1")).thenReturn(true);
        when(service.deleteBridge("unknown")).thenReturn(false);

        ResponseEntity<Void> deleted = delegate.deleteBridge("bridge-1");
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<Void> notFound = delegate.deleteBridge("unknown");
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /api/v1/bridges/test dry-runs transformation rules")
    void testBridgeDryRun() {
        ReplicationTestRequest req = new ReplicationTestRequest(
                Map.of("key", "val"), "payload", null, null, true, "bridge-1");
        ReplicationTestResult res = new ReplicationTestResult(
                true, false, Map.of("key", "val", "x-eventore-bridge-id", "bridge-1"), "payload", "OK");

        when(service.testBridge(eq(req))).thenReturn(res);

        ResponseEntity<ReplicationTestResult> response = delegate.testBridge(req);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().passedFilter()).isTrue();
    }
}
