package com.eventore.api;

import static org.junit.jupiter.api.Assertions.*;

import com.eventore.config.EventoreProperties;
import com.eventore.tracing.TraceContext;
import com.eventore.tracing.TracingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

class TracingControllerTest {

    private TracingController controller;

    @BeforeEach
    void setUp() {
        EventoreProperties properties = new EventoreProperties();
        TracingService tracingService = new TracingService(properties);
        controller = new TracingController(tracingService);
    }

    @Test
    @DisplayName("GET /api/v1/tracing/config returns active tracing settings and default URL template")
    void returnsActiveTracingConfig() {
        ResponseEntity<TracingController.TracingConfigResponse> res = controller.getConfig();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        assertTrue(res.getBody().enabled());
        assertEquals("JAEGER", res.getBody().viewerType());
        assertTrue(res.getBody().urlTemplate().contains("{traceId}"));
        assertTrue(res.getBody().injectOnPublish());
    }

    @Test
    @DisplayName("POST /api/v1/tracing/extract parses valid traceparent")
    void extractsTraceparentHeader() {
        Map<String, String> headers = Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "tracestate", "rojo=123"
        );

        ResponseEntity<TraceContext> res = controller.extract(headers);
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", res.getBody().getTraceId());
        assertEquals("00f067aa0ba902b7", res.getBody().getSpanId());
        assertTrue(res.getBody().isSampled());
        assertEquals(TraceContext.TraceFormat.W3C, res.getBody().getFormat());
    }

    @Test
    @DisplayName("POST /api/v1/tracing/extract returns 204 when no trace header present")
    void returnsNoContentForUntracedHeaders() {
        Map<String, String> headers = Map.of("content-type", "application/json", "host", "localhost");
        ResponseEntity<TraceContext> res = controller.extract(headers);
        assertEquals(204, res.getStatusCode().value());
        assertNull(res.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/tracing/url resolves deep link URL for traceId")
    void resolvesViewerUrl() {
        ResponseEntity<Map<String, String>> res = controller.resolveUrl("abc12345");
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals("abc12345", res.getBody().get("traceId"));
        assertEquals("http://localhost:16686/trace/abc12345", res.getBody().get("url"));
    }
}
