package com.eventore.testsupport;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import java.util.HashMap;
import java.util.Map;

public final class StreamTestFixtures {

    private StreamTestFixtures() {}

    public static ConnectionProfile profile(ProtocolType protocol, String brokerUrl) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("test-" + protocol.name().toLowerCase());
        profile.setName("Test " + protocol.name());
        profile.setProtocol(protocol);
        profile.setBrokerUrl(brokerUrl);
        return profile;
    }

    public static ConnectionProfile profile(
            ProtocolType protocol, String brokerUrl, Map<String, String> properties, Map<String, String> credentials) {
        ConnectionProfile profile = profile(protocol, brokerUrl);
        if (properties != null) {
            profile.setProperties(new HashMap<>(properties));
        }
        if (credentials != null) {
            profile.setCredentials(new HashMap<>(credentials));
        }
        return profile;
    }
}
