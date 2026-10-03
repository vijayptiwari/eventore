package com.eventore.tracing;

import com.eventore.domain.UnifiedMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextParserTest {

    @Test
    void parsesValidW3CTraceparent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        headers.put("tracestate", "congo=t61rcWkgMzE,rojo=00f067a");

        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.getTraceId());
        assertEquals("00f067aa0ba902b7", ctx.getSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.W3C, ctx.getFormat());
        assertEquals("congo=t61rcWkgMzE,rojo=00f067a", ctx.getTracestate());
        assertEquals("4bf92f35", ctx.getShortTraceId());
    }

    @Test
    void rejectsInvalidW3CTraceparent() {
        // Version ff
        Map<String, String> headers1 = Map.of("traceparent", "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertFalse(TraceContextParser.parse(headers1).isPresent());

        // All zeros trace id
        Map<String, String> headers2 = Map.of("traceparent", "00-00000000000000000000000000000000-00f067aa0ba902b7-01");
        assertFalse(TraceContextParser.parse(headers2).isPresent());

        // All zeros span id
        Map<String, String> headers3 = Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01");
        assertFalse(TraceContextParser.parse(headers3).isPresent());
    }

    @Test
    void parsesB3SingleHeader() {
        Map<String, String> headers = Map.of("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1-05f067aa0ba902b7");
        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.getTraceId());
        assertEquals("00f067aa0ba902b7", ctx.getSpanId());
        assertEquals("05f067aa0ba902b7", ctx.getParentSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.B3, ctx.getFormat());
    }

    @Test
    void parsesB3MultiHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-B3-TraceId", "80f198ee56343ba864fe8b2a57d3eff7");
        headers.put("X-B3-SpanId", "e457b5a2e4d86bd1");
        headers.put("X-B3-Sampled", "1");

        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("80f198ee56343ba864fe8b2a57d3eff7", ctx.getTraceId());
        assertEquals("e457b5a2e4d86bd1", ctx.getSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.B3, ctx.getFormat());
    }

    @Test
    void parsesAwsXRayHeader() {
        Map<String, String> headers = Map.of("X-Amzn-Trace-Id", "Root=1-5759e988-bd862e3fe1be46a994272767;Parent=53995cbe3140f4ec;Sampled=1");
        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("1-5759e988-bd862e3fe1be46a994272767", ctx.getTraceId());
        assertEquals("53995cbe3140f4ec", ctx.getSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.AWS_XRAY, ctx.getFormat());
    }

    @Test
    void parsesDatadogHeaders() {
        Map<String, String> headers = Map.of(
                "x-datadog-trace-id", "1234567890",
                "x-datadog-parent-id", "9876543210",
                "x-datadog-sampling-priority", "1"
        );
        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("1234567890", ctx.getTraceId());
        assertEquals("9876543210", ctx.getSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.DATADOG, ctx.getFormat());
    }

    @Test
    void parsesJaegerHeader() {
        Map<String, String> headers = Map.of("uber-trace-id", "1000000000000001:2000000000000002:3000000000000003:1");
        Optional<TraceContext> opt = TraceContextParser.parse(headers);
        assertTrue(opt.isPresent());
        TraceContext ctx = opt.get();
        assertEquals("1000000000000001", ctx.getTraceId());
        assertEquals("2000000000000002", ctx.getSpanId());
        assertEquals("3000000000000003", ctx.getParentSpanId());
        assertTrue(ctx.isSampled());
        assertEquals(TraceContext.TraceFormat.JAEGER, ctx.getFormat());
    }

    @Test
    void generateTraceparentCreatesValidW3CHeader() {
        String tp = TraceContextParser.generateTraceparent();
        assertNotNull(tp);
        assertTrue(tp.startsWith("00-"));
        assertEquals(55, tp.length()); // 2 + 1 + 32 + 1 + 16 + 1 + 2 = 55 chars

        Optional<TraceContext> parsed = TraceContextParser.parse(Map.of("traceparent", tp));
        assertTrue(parsed.isPresent());
        assertTrue(parsed.get().isSampled());
        assertEquals(32, parsed.get().getTraceId().length());
        assertEquals(16, parsed.get().getSpanId().length());
    }

    @Test
    void generateChildTraceparentInheritsTraceId() {
        TraceContext parent = new TraceContext("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null, true, TraceContext.TraceFormat.W3C, "raw");
        String childTp = TraceContextParser.generateChildTraceparent(parent);

        Optional<TraceContext> parsed = TraceContextParser.parse(Map.of("traceparent", childTp));
        assertTrue(parsed.isPresent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", parsed.get().getTraceId());
        assertNotEquals("00f067aa0ba902b7", parsed.get().getSpanId());
        assertTrue(parsed.get().isSampled());
    }

    @Test
    void enrichInjectsMetadataHeaders() {
        UnifiedMessage msg = new UnifiedMessage();
        msg.putHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        Optional<TraceContext> opt = TraceContextParser.enrich(msg);
        assertTrue(opt.isPresent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", msg.getHeaders().get("x-eventore-trace-id"));
        assertEquals("00f067aa0ba902b7", msg.getHeaders().get("x-eventore-span-id"));
        assertEquals("W3C", msg.getHeaders().get("x-eventore-trace-format"));
    }

    @Test
    void tracingConfigResolvesViewerUrls() {
        TracingConfig config = new TracingConfig();
        config.setViewerType("JAEGER");
        assertEquals("http://localhost:16686/trace/abc12345", config.resolveViewerUrl("abc12345"));

        config.setViewerType("ZIPKIN");
        assertEquals("http://localhost:9411/zipkin/traces/abc12345", config.resolveViewerUrl("abc12345"));

        config.setViewerType("DATADOG");
        assertEquals("https://app.datadoghq.com/apm/trace/abc12345", config.resolveViewerUrl("abc12345"));

        config.setViewerType("CUSTOM");
        config.setUrlTemplate("https://internal.observability.corp/traces/{traceId}?env=prod");
        assertEquals("https://internal.observability.corp/traces/abc12345?env=prod", config.resolveViewerUrl("abc12345"));

        config.setViewerType("NONE");
        assertNull(config.resolveViewerUrl("abc12345"));
    }
}
