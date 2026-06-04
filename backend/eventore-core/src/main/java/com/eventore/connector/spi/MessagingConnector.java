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

    void close(String connectionId);
}
