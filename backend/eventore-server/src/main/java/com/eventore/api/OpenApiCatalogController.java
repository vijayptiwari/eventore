package com.eventore.api;

import com.eventore.openapi.StreamOpenApiCatalog;
import com.eventore.openapi.StreamOpenApiCatalog.StreamSpec;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi")
public class OpenApiCatalogController {

    private final StreamOpenApiCatalog catalog;

    public OpenApiCatalogController(StreamOpenApiCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    public List<StreamSpec> catalog() {
        return catalog.listForDeployment();
    }
}
