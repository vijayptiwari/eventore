package com.eventore.service.store;

import com.eventore.domain.ConnectionStoreType;
import java.util.Map;

/**
 * Diagnostic and runtime status metadata for the active connection store (REQ-101).
 */
public record ConnectionStoreInfo(
        ConnectionStoreType type,
        boolean enabled,
        int activeProfileCount,
        boolean supportsOptimisticLocking,
        String location,
        Map<String, String> details) {}
