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

    public void connectionCreated(String connectionId, ProtocolType protocol, String name) {
        AUDIT.info("event=CONNECTION_CREATE connectionId={} protocol={} name={}", connectionId, protocol, name);
    }

    public void connectionUpdated(String connectionId, ProtocolType protocol, String name) {
        AUDIT.info("event=CONNECTION_UPDATE connectionId={} protocol={} name={}", connectionId, protocol, name);
    }

    public void connectionDeleted(String connectionId, ProtocolType protocol) {
        AUDIT.info("event=CONNECTION_DELETE connectionId={} protocol={}", connectionId, protocol);
    }

    public void providerRegistered(ProtocolType protocol) {
        AUDIT.info("event=PROVIDER_REGISTER protocol={}", protocol);
    }

    public void providerDeregistered(ProtocolType protocol) {
        AUDIT.info("event=PROVIDER_DEREGISTER protocol={}", protocol);
    }
}
