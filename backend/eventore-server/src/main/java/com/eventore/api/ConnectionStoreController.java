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

    @Autowired
    public ConnectionStoreController(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @GetMapping
    public ResponseEntity<ConnectionStoreInfo> getStoreInfo() {
        return ResponseEntity.ok(connectionRegistry.getStoreInfo());
    }

    @PostMapping("/migrate")
    public ResponseEntity<MigrateStoreResponse> migrate(@RequestBody MigrateStoreRequest request) {
        if (request == null || request.targetType() == null) {
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
            int count = delegator.migrateTo(request.targetType());
            return ResponseEntity.ok(new MigrateStoreResponse(
                    "SUCCESS",
                    count,
                    delegator.getType(),
                    "Successfully migrated " + count + " connection profile(s) to " + request.targetType()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MigrateStoreResponse(
                    "ERROR",
                    0,
                    delegator.getType(),
                    e.getMessage()));
        }
    }

    public record MigrateStoreRequest(ConnectionStoreType targetType) {}

    public record MigrateStoreResponse(
            String status,
            int migratedProfiles,
            ConnectionStoreType activeStore,
            String message) {}
}
