package com.eventore.tracing;

import java.io.Serializable;
import java.util.Objects;

/**
 * Normalized representation of a distributed trace context extracted from message transport headers.
 * Supports W3C Trace Context (RFC 7230 / W3C Recommendation), B3 (Zipkin), AWS X-Ray, Datadog, and Jaeger.
 */
public class TraceContext implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum TraceFormat {
        W3C,
        B3,
        AWS_XRAY,
        DATADOG,
        JAEGER,
        CUSTOM
    }

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private boolean sampled;
    private TraceFormat format = TraceFormat.W3C;
    private String rawHeader;
    private String tracestate;

    public TraceContext() {
    }

    public TraceContext(String traceId, String spanId, String parentSpanId, boolean sampled, TraceFormat format, String rawHeader) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
        this.format = format;
        this.rawHeader = rawHeader;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public boolean isSampled() {
        return sampled;
    }

    public void setSampled(boolean sampled) {
        this.sampled = sampled;
    }

    public TraceFormat getFormat() {
        return format;
    }

    public void setFormat(TraceFormat format) {
        this.format = format;
    }

    public String getRawHeader() {
        return rawHeader;
    }

    public void setRawHeader(String rawHeader) {
        this.rawHeader = rawHeader;
    }

    public String getTracestate() {
        return tracestate;
    }

    public void setTracestate(String tracestate) {
        this.tracestate = tracestate;
    }

    /**
     * Returns a short display representation of the trace ID (first 8 characters or whole if short).
     */
    public String getShortTraceId() {
        if (traceId == null) {
            return "";
        }
        return traceId.length() > 8 ? traceId.substring(0, 8) : traceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceContext that = (TraceContext) o;
        return sampled == that.sampled &&
                Objects.equals(traceId, that.traceId) &&
                Objects.equals(spanId, that.spanId) &&
                Objects.equals(parentSpanId, that.parentSpanId) &&
                format == that.format;
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, parentSpanId, sampled, format);
    }

    @Override
    public String toString() {
        return "TraceContext{" +
                "traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                ", format=" + format +
                ", sampled=" + sampled +
                '}';
    }
}
