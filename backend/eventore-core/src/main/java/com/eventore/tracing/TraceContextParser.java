package com.eventore.tracing;

import com.eventore.domain.UnifiedMessage;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance parser, extractor, and generator for distributed tracing headers.
 * Supports W3C Trace Context (RFC 7230 / W3C Recommendation), B3, AWS X-Ray, Datadog, and Jaeger.
 */
public final class TraceContextParser {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern W3C_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})(?:-.*)?$");

    private static final String ALL_ZEROS_32 = "00000000000000000000000000000000";
    private static final String ALL_ZEROS_16 = "0000000000000000";

    private TraceContextParser() {
    }

    /**
     * Extracts a TraceContext from message headers using standard trace header conventions.
     */
    public static Optional<TraceContext> parse(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        // 1. W3C Trace Context: traceparent
        String traceparent = getHeaderIgnoreCase(headers, "traceparent");
        if (traceparent == null) {
            traceparent = getHeaderIgnoreCase(headers, "trace-parent");
        }
        if (traceparent != null) {
            TraceContext ctx = parseW3C(traceparent.trim());
            if (ctx != null) {
                String tracestate = getHeaderIgnoreCase(headers, "tracestate");
                if (tracestate != null) {
                    ctx.setTracestate(tracestate.trim());
                }
                return Optional.of(ctx);
            }
        }

        // 2. B3 Single Header: b3
        String b3 = getHeaderIgnoreCase(headers, "b3");
        if (b3 != null) {
            TraceContext ctx = parseB3Single(b3.trim());
            if (ctx != null) {
                return Optional.of(ctx);
            }
        }

        // 3. B3 Multi Headers: X-B3-TraceId, X-B3-SpanId, etc.
        String b3TraceId = getHeaderIgnoreCase(headers, "X-B3-TraceId");
        if (b3TraceId != null) {
            String b3SpanId = getHeaderIgnoreCase(headers, "X-B3-SpanId");
            String b3ParentSpanId = getHeaderIgnoreCase(headers, "X-B3-ParentSpanId");
            String b3Sampled = getHeaderIgnoreCase(headers, "X-B3-Sampled");
            boolean sampled = "1".equals(b3Sampled) || "true".equalsIgnoreCase(b3Sampled);
            TraceContext ctx = new TraceContext(
                    b3TraceId.trim(),
                    b3SpanId != null ? b3SpanId.trim() : null,
                    b3ParentSpanId != null ? b3ParentSpanId.trim() : null,
                    sampled,
                    TraceContext.TraceFormat.B3,
                    b3TraceId
            );
            return Optional.of(ctx);
        }

        // 4. AWS X-Ray: X-Amzn-Trace-Id
        String xray = getHeaderIgnoreCase(headers, "X-Amzn-Trace-Id");
        if (xray != null) {
            TraceContext ctx = parseXRay(xray.trim());
            if (ctx != null) {
                return Optional.of(ctx);
            }
        }

        // 5. Datadog: x-datadog-trace-id
        String ddTraceId = getHeaderIgnoreCase(headers, "x-datadog-trace-id");
        if (ddTraceId != null) {
            String ddSpanId = getHeaderIgnoreCase(headers, "x-datadog-parent-id");
            String ddPriority = getHeaderIgnoreCase(headers, "x-datadog-sampling-priority");
            boolean sampled = ddPriority != null && Integer.parseInt(ddPriority.trim()) > 0;
            TraceContext ctx = new TraceContext(
                    ddTraceId.trim(),
                    ddSpanId != null ? ddSpanId.trim() : null,
                    null,
                    sampled,
                    TraceContext.TraceFormat.DATADOG,
                    ddTraceId
            );
            return Optional.of(ctx);
        }

        // 6. Uber / Jaeger: uber-trace-id
        String uber = getHeaderIgnoreCase(headers, "uber-trace-id");
        if (uber != null) {
            TraceContext ctx = parseUber(uber.trim());
            if (ctx != null) {
                return Optional.of(ctx);
            }
        }

        return Optional.empty();
    }

    /**
     * Enriches a UnifiedMessage with normalized trace headers (x-eventore-trace-id, etc.) if trace context is present.
     */
    public static Optional<TraceContext> enrich(UnifiedMessage message) {
        if (message == null) {
            return Optional.empty();
        }
        Optional<TraceContext> opt = parse(message.getHeaders());
        if (opt.isPresent()) {
            TraceContext ctx = opt.get();
            if (ctx.getTraceId() != null) {
                message.putHeader("x-eventore-trace-id", ctx.getTraceId());
            }
            if (ctx.getSpanId() != null) {
                message.putHeader("x-eventore-span-id", ctx.getSpanId());
            }
            if (ctx.getFormat() != null) {
                message.putHeader("x-eventore-trace-format", ctx.getFormat().name());
            }
        }
        return opt;
    }

    /**
     * Generates a standard W3C traceparent header: 00-{32-hex-trace-id}-{16-hex-span-id}-01
     */
    public static String generateTraceparent() {
        return "00-" + randomHex(16) + "-" + randomHex(8) + "-01";
    }

    /**
     * Generates a child W3C traceparent header inheriting the parent traceId.
     */
    public static String generateChildTraceparent(TraceContext parent) {
        if (parent == null || parent.getTraceId() == null) {
            return generateTraceparent();
        }
        String traceId = parent.getTraceId().replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (traceId.length() < 32) {
            traceId = String.format("%32s", traceId).replace(' ', '0');
        } else if (traceId.length() > 32) {
            traceId = traceId.substring(0, 32);
        }
        String spanId = randomHex(8);
        String flags = parent.isSampled() ? "01" : "00";
        return "00-" + traceId + "-" + spanId + "-" + flags;
    }

    private static TraceContext parseW3C(String value) {
        Matcher m = W3C_PATTERN.matcher(value);
        if (!m.matches()) {
            return null;
        }
        String version = m.group(1).toLowerCase();
        if ("ff".equals(version)) {
            return null;
        }
        String traceId = m.group(2).toLowerCase();
        if (ALL_ZEROS_32.equals(traceId)) {
            return null;
        }
        String spanId = m.group(3).toLowerCase();
        if (ALL_ZEROS_16.equals(spanId)) {
            return null;
        }
        String flagsStr = m.group(4);
        int flags = Integer.parseInt(flagsStr, 16);
        boolean sampled = (flags & 1) != 0;

        return new TraceContext(traceId, spanId, null, sampled, TraceContext.TraceFormat.W3C, value);
    }

    private static TraceContext parseB3Single(String value) {
        // Format: {TraceId}-{SpanId}-{SamplingState}-{ParentSpanId}
        String[] parts = value.split("-");
        if (parts.length < 2) {
            return null;
        }
        String traceId = parts[0];
        String spanId = parts[1];
        boolean sampled = false;
        String parentSpanId = null;

        if (parts.length >= 3) {
            String s = parts[2];
            sampled = "1".equals(s) || "d".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
        }
        if (parts.length >= 4) {
            parentSpanId = parts[3];
        }

        return new TraceContext(traceId, spanId, parentSpanId, sampled, TraceContext.TraceFormat.B3, value);
    }

    private static TraceContext parseXRay(String value) {
        // Format: Root=1-5759e988-bd862e3fe1be46a994272767;Parent=53995cbe3140f4ec;Sampled=1
        String[] parts = value.split(";");
        String traceId = null;
        String spanId = null;
        boolean sampled = false;

        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String k = kv[0].trim();
                String v = kv[1].trim();
                if ("Root".equalsIgnoreCase(k)) {
                    traceId = v;
                } else if ("Parent".equalsIgnoreCase(k)) {
                    spanId = v;
                } else if ("Sampled".equalsIgnoreCase(k)) {
                    sampled = "1".equals(v);
                }
            }
        }

        if (traceId != null) {
            return new TraceContext(traceId, spanId, null, sampled, TraceContext.TraceFormat.AWS_XRAY, value);
        }
        return null;
    }

    private static TraceContext parseUber(String value) {
        // Format: {trace-id}:{span-id}:{parent-span-id}:{flags}
        String[] parts = value.split(":");
        if (parts.length < 2) {
            return null;
        }
        String traceId = parts[0].trim();
        String spanId = parts[1].trim();
        String parentSpanId = parts.length > 2 ? parts[2].trim() : null;
        boolean sampled = false;
        if (parts.length > 3) {
            try {
                int flags = Integer.parseInt(parts[3].trim(), 16);
                sampled = (flags & 1) != 0;
            } catch (NumberFormatException ignored) {
            }
        }
        return new TraceContext(traceId, spanId, parentSpanId, sampled, TraceContext.TraceFormat.JAEGER, value);
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String targetKey) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
