package com.eventore.diagnostics;

import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.ValidationHistoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticsController {

    private final SubscriptionManager subscriptionManager;
    private final ValidationHistoryService validationHistoryService;
    private final ConnectionRegistry connectionRegistry;
    private final DeploymentModePolicy policy;

    public DiagnosticsController(
            SubscriptionManager subscriptionManager,
            ValidationHistoryService validationHistoryService,
            ConnectionRegistry connectionRegistry,
            DeploymentModePolicy policy) {
        this.subscriptionManager = subscriptionManager;
        this.validationHistoryService = validationHistoryService;
        this.connectionRegistry = connectionRegistry;
        this.policy = policy;
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionDiagnosticDto> subscriptions() {
        policy.require(Action.SUBSCRIBE);
        return subscriptionManager.diagnosticsSnapshot();
    }

    @GetMapping("/connections/{connectionId}/validations")
    public List<ValidationRecordDto> validationHistory(@PathVariable String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        connectionRegistry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
        return validationHistoryService.history(connectionId).stream()
                .map(r -> new ValidationRecordDto(
                        r.timestamp().toString(), r.status(), r.message()))
                .toList();
    }
}
