package com.eventore.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the canonical OpenAPI 3 YAML contract from classpath. */
@RestController
public class OpenApiSpecController {

    @GetMapping(value = "/openapi/eventore-api.yaml", produces = "application/yaml")
    public Resource contractSpec() {
        return new ClassPathResource("openapi/eventore-api.yaml");
    }

}
