package com.eventore.connector.spi;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import java.util.List;

public interface MessagingConnector {

    ProtocolType protocol();

    void validate(ConnectionProfile profile);

    List<TopicRef> listDestinations(ConnectionProfile profile);

    AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler);

    void publish(ConnectionProfile profile, PublishRequest request);

    /**
     * Releases connector resources for the given connection id. Implementations should
     * stop active subscriptions, close clients, and tolerate repeated calls for the
     * same id without throwing.
     */
    void close(String connectionId);
}
