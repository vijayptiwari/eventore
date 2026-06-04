package com.eventore.service;

import com.eventore.domain.ProtocolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    public void publish(
            String connectionId,
            ProtocolType protocol,
            String destination,
            int payloadBytes,
            String userAgent) {
        AUDIT.info(
                "event=PUBLISH connectionId={} protocol={} destination={} bytes={} userAgent={}",
                connectionId,
                protocol,
                destination,
                payloadBytes,
                userAgent != null ? userAgent : "unknown");
    }
}
