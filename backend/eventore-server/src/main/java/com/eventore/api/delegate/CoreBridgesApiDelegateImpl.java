package com.eventore.api.delegate;

import com.eventore.api.generated.core.BridgesApiDelegate;
import com.eventore.domain.bridge.ReplicationBridge;
import com.eventore.domain.bridge.ReplicationBridgeRequest;
import com.eventore.domain.bridge.ReplicationTestRequest;
import com.eventore.domain.bridge.ReplicationTestResult;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ReplicationBridgeService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoreBridgesApiDelegateImpl implements BridgesApiDelegate {

    private final ReplicationBridgeService bridgeService;
    private final DeploymentModePolicy policy;

    public CoreBridgesApiDelegateImpl(
            ReplicationBridgeService bridgeService,
            DeploymentModePolicy policy) {
        this.bridgeService = bridgeService;
        this.policy = policy;
    }

    @Override
    public ResponseEntity<List<ReplicationBridge>> listBridges() {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(bridgeService.listBridges());
    }

    @Override
    public ResponseEntity<ReplicationBridge> getBridge(String bridgeId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return bridgeService.getBridge(bridgeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ReplicationBridge> createBridge(ReplicationBridgeRequest request) {
        policy.require(Action.MANAGE_CONNECTIONS);
        return ResponseEntity.status(HttpStatus.CREATED).body(bridgeService.createBridge(request));
    }

    @Override
    public ResponseEntity<ReplicationBridge> updateBridge(String bridgeId, ReplicationBridgeRequest request) {
        policy.require(Action.MANAGE_CONNECTIONS);
        try {
            return ResponseEntity.ok(bridgeService.updateBridge(bridgeId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> deleteBridge(String bridgeId) {
        policy.require(Action.MANAGE_CONNECTIONS);
        boolean deleted = bridgeService.deleteBridge(bridgeId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Override
    public ResponseEntity<ReplicationBridge> startBridge(String bridgeId) {
        policy.require(Action.MANAGE_CONNECTIONS);
        try {
            return ResponseEntity.ok(bridgeService.startBridge(bridgeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<ReplicationBridge> stopBridge(String bridgeId) {
        policy.require(Action.MANAGE_CONNECTIONS);
        try {
            return ResponseEntity.ok(bridgeService.stopBridge(bridgeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<ReplicationTestResult> testBridge(ReplicationTestRequest request) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(bridgeService.testBridge(request));
    }
}
