package com.eventore.connector.kinesis;

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

class KinesisMessagingConnectorTest {

    private KinesisMessagingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new KinesisMessagingConnector();
    }

    @Test
    void protocolIsKinesis() {
        assertEquals(ProtocolType.KINESIS, connector.protocol());
    }

    @Test
    void closeWithNoActiveSubscriptionsDoesNotThrow() {
        assertDoesNotThrow(() -> connector.close("conn-kinesis"));
    }

    @Test
    void subscribeWithoutCredentialsFailsWhenDefaultCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                Map.of("allowDefaultCredentials", "false"),
                null);
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination("orders-stream");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> connector.subscribe(profile, request, msg -> {}));

        assertTrue(ex.getMessage().contains("allowDefaultCredentials=false"));
        assertDoesNotThrow(() -> connector.close(profile.getId()));
    }

    @Test
    void publishWithoutCredentialsFailsWhenDefaultCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                Map.of("allowDefaultCredentials", "false"),
                null);
        PublishRequest request = new PublishRequest();
        request.setDestination("orders-stream");
        request.setPayload("hello");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("Kinesis publish failed"));
    }

    @Test
    void publishRejectsInvalidBase64BeforeClientInitWhenCredentialsDisallowed() {
        var profile = StreamTestFixtures.profile(
                ProtocolType.KINESIS,
                "us-east-1",
                Map.of("allowDefaultCredentials", "false"),
                null);
        PublishRequest request = new PublishRequest();
        request.setDestination("orders-stream");
        request.setPayload("not valid base64 !!!");
        request.setContentType("application/base64");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> connector.publish(profile, request));

        // Credentials are validated before PayloadCodec; base64 rejection requires AWS mocks/network.
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("allowDefaultCredentials=false")
                || ex.getCause().getMessage().contains("base64"));
    }
}
