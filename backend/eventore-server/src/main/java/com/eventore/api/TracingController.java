package com.eventore.api;

import com.eventore.tracing.TraceContext;
import com.eventore.tracing.TracingConfig;
import com.eventore.tracing.TracingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for distributed tracing configuration and trace header diagnostics.
 */
@RestController
@RequestMapping("/api/v1/tracing")
public class TracingController {

    private final TracingService tracingService;

    @Autowired
    public TracingController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    @GetMapping("/config")
    public ResponseEntity<TracingConfigResponse> getConfig() {
        TracingConfig cfg = tracingService.getConfig();
        return ResponseEntity.ok(new TracingConfigResponse(
                cfg.isEnabled(),
                cfg.getViewerType(),
                cfg.getUrlTemplate(),
                cfg.isInjectOnPublish()
        ));
    }

    @PostMapping("/extract")
    public ResponseEntity<TraceContext> extract(@RequestBody(required = false) Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        Optional<TraceContext> opt = tracingService.parse(headers);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> resolveUrl(@RequestParam("traceId") String traceId) {
        String url = tracingService.resolveViewerUrl(traceId);
        if (url == null) {
            return ResponseEntity.ok(Map.of("traceId", traceId));
        }
        return ResponseEntity.ok(Map.of("traceId", traceId, "url", url));
    }

    public record TracingConfigResponse(
            boolean enabled,
            String viewerType,
            String urlTemplate,
            boolean injectOnPublish
    ) {}
}
