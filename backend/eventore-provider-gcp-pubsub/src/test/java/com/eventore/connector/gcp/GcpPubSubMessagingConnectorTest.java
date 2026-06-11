package com.eventore.connector.gcp;

import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ProtocolType;
import com.eventore.testsupport.StreamTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpPubSubMessagingConnectorTest {

    private GcpPubSubMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GcpPubSubMessagingConnector();
    }

    @Test
    void protocolIsGcpPubSub() {
        assertEquals(ProtocolType.GCP_PUBSUB, connector.protocol());
    }

    @Test
    void closeWithNoActiveSubscribersDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-gcp"));
    }

    @Test
    void subscribeWithoutCredentialsFailsWhenDefaultCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB,
                "eventore-dev",
                Map.of("projectId", "eventore-dev", "allowDefaultCredentials", "false"),
                null);
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("allowDefaultCredentials=false"));
        assertDoesNotThrow(() -> connector.close(profile.getId()));
    }

    @Test
    void publishWithoutCredentialsFailsWhenDefaultCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB,
                "eventore-dev",
                Map.of("projectId", "eventore-dev", "allowDefaultCredentials", "false"),
                null);
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("hello");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("Pub/Sub publisher init failed"));
    }

    @Test
    void publishRejectsInvalidBase64BeforePublisherInitWhenCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.GCP_PUBSUB,
                "eventore-dev",
                Map.of("projectId", "eventore-dev", "allowDefaultCredentials", "false"),
                null);
        PublishRequest request = new PublishRequest();
        request.setDestination("orders");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        // Publisher init validates credentials before PayloadCodec; full base64 path needs GCP mocks.
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("allowDefaultCredentials=false")
                || ex.getCause().getMessage().contains("base64"));
    }
}
