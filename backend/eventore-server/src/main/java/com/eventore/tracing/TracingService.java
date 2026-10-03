package com.eventore.tracing;

import com.eventore.config.EventoreProperties;
import com.eventore.domain.UnifiedMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service managing distributed tracing configuration, trace extraction, enrichment, and propagation.
 */
@Service
public class TracingService {

    private final EventoreProperties properties;

    @Autowired
    public TracingService(EventoreProperties properties) {
        this.properties = properties;
    }

    public TracingConfig getConfig() {
        if (properties == null || properties.getTracing() == null) {
            return new TracingConfig();
        }
        return properties.getTracing().toTracingConfig();
    }

    public Optional<TraceContext> parse(Map<String, String> headers) {
        return TraceContextParser.parse(headers);
    }

    public Optional<TraceContext> enrich(UnifiedMessage message) {
        return TraceContextParser.enrich(message);
    }

    public String generateTraceparent() {
        return TraceContextParser.generateTraceparent();
    }

    public String generateChildTraceparent(TraceContext parent) {
        return TraceContextParser.generateChildTraceparent(parent);
    }

    public String resolveViewerUrl(String traceId) {
        return getConfig().resolveViewerUrl(traceId);
    }

    /**
     * Injects a W3C traceparent header into the given headers map if tracing is enabled and no tracing header exists.
     */
    public boolean injectIfEnabled(Map<String, String> headers) {
        TracingConfig config = getConfig();
        if (!config.isEnabled() || !config.isInjectOnPublish() || headers == null) {
            return false;
        }
        if (TraceContextParser.parse(headers).isEmpty()) {
            headers.put("traceparent", generateTraceparent());
            return true;
        }
        return false;
    }
}
