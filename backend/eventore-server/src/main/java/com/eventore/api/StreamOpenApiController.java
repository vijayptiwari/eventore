package com.eventore.api;

import com.eventore.openapi.StreamOpenApiCatalog;
import com.eventore.openapi.StreamOpenApiCatalog.StreamSpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StreamOpenApiController {

    private final StreamOpenApiCatalog catalog;

    public StreamOpenApiController(StreamOpenApiCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping(value = "/openapi/streams/{streamId}-api.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> streamSpec(@PathVariable String streamId) {
        StreamSpec spec = catalog.get(streamId);
        if (spec == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream OpenAPI not available: " + streamId);
        }
        Resource resource = new ClassPathResource("openapi/streams/" + streamId + "-api.yaml");
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec file missing for " + streamId);
        }
        return ResponseEntity.ok(resource);
    }
}
