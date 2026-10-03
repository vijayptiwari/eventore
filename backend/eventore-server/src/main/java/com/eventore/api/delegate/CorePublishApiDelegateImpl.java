package com.eventore.api.delegate;

import com.eventore.api.generated.core.PublishApiDelegate;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.schema.DefaultSchemaRegistryClient;
import com.eventore.schema.SchemaPayloadValidator;
import com.eventore.schema.SchemaRegistryClient;
import com.eventore.schema.SchemaValidationResult;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CorePublishApiDelegateImpl implements PublishApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final ConnectorRegistry connectorRegistry;
    private final DeploymentModePolicy policy;
    private final AuditService auditService;
    private final SchemaRegistryClient schemaRegistryClient;

    public CorePublishApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService) {
        this(connectionRegistry, connectorRegistry, policy, auditService, new DefaultSchemaRegistryClient());
    }

    @Autowired
    public CorePublishApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            ConnectorRegistry connectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService,
            SchemaRegistryClient schemaRegistryClient) {
        this.connectionRegistry = connectionRegistry;
        this.connectorRegistry = connectorRegistry;
        this.policy = policy;
        this.auditService = auditService;
        this.schemaRegistryClient = schemaRegistryClient != null ? schemaRegistryClient : new DefaultSchemaRegistryClient();
    }

    @Override
    public ResponseEntity<Map<String, String>> publishMessage(String connectionId, PublishRequest publishRequest) {
        policy.require(Action.PUBLISH);
        var profile = CoreDelegateSupport.profile(connectionRegistry, connectionId);
        policy.requireProtocol(profile.getProtocol());

        String registryUrl = profile != null ? profile.property("schemaRegistryUrl") : null;
        if (registryUrl == null && profile != null) {
            registryUrl = profile.property("schema.registry.url");
        }
        SchemaValidationResult validation = SchemaPayloadValidator.validate(
                schemaRegistryClient, registryUrl, publishRequest.getPayload(), publishRequest.getHeaders());
        if (!validation.valid()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payload failed schema validation: " + String.join(", ", validation.errors()));
        }

        int bytes = PayloadCodec.toBytes(publishRequest.getPayload(), publishRequest.getContentType()).length;
        policy.validatePublishSize(bytes);
        connectorRegistry.get(profile.getProtocol()).publish(profile, publishRequest);
        var request = CoreDelegateSupport.currentRequest();
        auditService.publish(
                connectionId,
                profile.getProtocol(),
                publishRequest.getDestination(),
                bytes,
                request != null ? request.getHeader("User-Agent") : null);
        return ResponseEntity.ok(Map.of("status", "published"));
    }
}

