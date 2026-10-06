package com.eventore.api;

import com.eventore.domain.ConnectionStoreType;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.store.ConnectionStoreInfo;
import com.eventore.service.store.DelegatingConnectionProfileStore;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inspecting connection storage status and executing migrations (REQ-101).
 */
@RestController
@RequestMapping("/api/v1/connections/store")
public class ConnectionStoreController {

    private final ConnectionRegistry connectionRegistry;
    private final com.eventore.security.DeploymentModePolicy policy;

    @Autowired
    public ConnectionStoreController(ConnectionRegistry connectionRegistry, com.eventore.security.DeploymentModePolicy policy) {
        this.connectionRegistry = connectionRegistry;
        this.policy = policy;
    }

    @GetMapping
    public ResponseEntity<ConnectionStoreInfo> getStoreInfo() {
        return ResponseEntity.ok(connectionRegistry.getStoreInfo());
    }

    @PostMapping("/migrate")
    public ResponseEntity<MigrateStoreResponse> migrate(
            @org.springframework.web.bind.annotation.RequestParam(name = "targetType", required = false) ConnectionStoreType targetTypeParam,
            @RequestBody(required = false) MigrateStoreRequest request) {
        policy.require(com.eventore.security.Action.MANAGE_CONNECTIONS);
        ConnectionStoreType targetType = targetTypeParam != null ? targetTypeParam : (request != null ? request.targetType() : null);
        if (targetType == null) {
            return ResponseEntity.badRequest().body(new MigrateStoreResponse(
                    "ERROR",
                    0,
                    null,
                    "targetType is required (FILE, JDBC, K8S_CRD, IN_MEMORY)"));
        }

        if (!(connectionRegistry.getStore() instanceof DelegatingConnectionProfileStore delegator)) {
            return ResponseEntity.badRequest().body(new MigrateStoreResponse(
                    "ERROR",
                    0,
                    connectionRegistry.getStore().getType(),
                    "Active connection store does not support runtime migration"));
        }

        try {
            int count = delegator.migrateTo(targetType);
            return ResponseEntity.ok(new MigrateStoreResponse(
                    "SUCCESS",
                    count,
                    delegator.getType(),
                    "Successfully migrated " + count + " connection profile(s) to " + targetType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MigrateStoreResponse(
                    "ERROR",
                    0,
                    delegator.getType(),
                    e.getMessage()));
        }
    }

    public ResponseEntity<MigrateStoreResponse> migrate(MigrateStoreRequest request) {
        return migrate(null, request);
    }

    public record MigrateStoreRequest(ConnectionStoreType targetType) {}

    public record MigrateStoreResponse(
            String status,
            int migratedProfiles,
            ConnectionStoreType activeStore,
            String message) {}
}
