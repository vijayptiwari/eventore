package com.eventore.api;

import com.eventore.schema.SchemaMetadata;
import com.eventore.schema.SchemaRegistryClient;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import java.util.Collection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schemas")
public class SchemaRegistryController {

    private final SchemaRegistryClient schemaRegistryClient;
    private final DeploymentModePolicy policy;

    public SchemaRegistryController(
            SchemaRegistryClient schemaRegistryClient,
            DeploymentModePolicy policy) {
        this.schemaRegistryClient = schemaRegistryClient;
        this.policy = policy;
    }

    @GetMapping
    public Collection<SchemaMetadata> listSchemas() {
        return schemaRegistryClient.getCachedSchemas().values();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchemaMetadata> getSchema(
            @PathVariable("id") int id,
            @RequestParam(value = "registryUrl", required = false) String registryUrl) {
        return schemaRegistryClient.getSchemaById(registryUrl, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record RegisterSchemaPayload(
            Integer schemaId,
            String subject,
            String schemaType,
            String schemaContent,
            String registryUrl) {}

    @PostMapping
    public ResponseEntity<SchemaMetadata> registerSchema(@RequestBody RegisterSchemaPayload payload) {
        policy.require(Action.MANAGE_CONNECTIONS);
        if (payload == null || payload.schemaContent() == null || payload.schemaContent().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String type = (payload.schemaType() != null && !payload.schemaType().isBlank())
                ? payload.schemaType() : "AVRO";
        String subject = (payload.subject() != null && !payload.subject().isBlank())
                ? payload.subject() : "default-subject";

        if (payload.schemaId() != null) {
            schemaRegistryClient.registerLocalSchema(payload.schemaId(), payload.schemaContent(), type);
            return ResponseEntity.ok(new SchemaMetadata(payload.schemaId(), subject, 1, type, payload.schemaContent()));
        } else {
            SchemaMetadata meta = schemaRegistryClient.registerSchema(
                    payload.registryUrl(), subject, payload.schemaContent(), type);
            return ResponseEntity.ok(meta);
        }
    }
}
