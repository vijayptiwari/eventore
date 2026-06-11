package com.eventore.inspect.rabbitmq;

import com.eventore.domain.ProtocolType;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class RabbitMqMessagingInspectorTest {

    private HttpClient httpClient;
    private RabbitMqMessagingInspector inspector;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        inspector = new RabbitMqMessagingInspector(httpClient);
    }

    private void stubManagementApiUnavailable() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));
    }

    @SuppressWarnings("unchecked")
    private void stubManagementApiResponse(int status, String body) throws Exception {
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(), any());
    }

    @Test
    void protocolIsRabbitMq() {
        assertEquals(ProtocolType.RABBITMQ, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeQueueOperations() {
        var features = inspector.capabilities().getFeatures();
        assertTrue(features.contains("queues"));
        assertTrue(features.contains("message-search"));
        assertTrue(features.contains("message-get"));
        assertFalse(features.contains("queue-purge"));
    }

    @Test
    void clusterInfoIncludesManagementHintWhenApiUnavailable() throws Exception {
        stubManagementApiUnavailable();
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);

        var info = inspector.clusterInfo(profile);

        assertEquals("rabbitmq", info.getClusterId());
        assertTrue(info.getAttributes().containsKey("note"));
        assertTrue(info.getAttributes().containsKey("error"));
    }

    @Test
    void clusterInfoUsesClusterNameFromManagementApi() throws Exception {
        stubManagementApiResponse(200, "{\"cluster_name\":\"rabbit@node1\"}");
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);

        var info = inspector.clusterInfo(profile);

        assertEquals("rabbit@node1", info.getClusterId());
        assertEquals("http://localhost:15672", info.getAttributes().get("management"));
    }

    @Test
    void listTopicsMapsQueuesAndConsumerCounts() throws Exception {
        stubManagementApiResponse(
                200,
                "[{\"name\":\"orders\",\"consumers\":3,\"messages\":7,\"state\":\"running\"}]");
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);

        var topics = inspector.listTopics(profile, null);

        assertEquals(1, topics.size());
        assertEquals("orders", topics.get(0).getName());
        // partitionCount carries the consumer count for RabbitMQ (documented contract).
        assertEquals(3, topics.get(0).getPartitionCount());
        assertEquals("7", topics.get(0).getConfig().get("messages"));
        assertEquals("running", topics.get(0).getConfig().get("state"));
    }

    @Test
    void listTopicsFallsBackWhenManagementApiUnavailable() throws Exception {
        stubManagementApiUnavailable();
        var profile = StreamTestFixtures.profile(
                ProtocolType.RABBITMQ, "127.0.0.1:5672", Map.of("queue", "fallback-queue"), null);

        var topics = inspector.listTopics(profile, null);

        assertEquals(1, topics.size());
        assertEquals("fallback-queue", topics.get(0).getName());
        assertTrue(topics.get(0).getConfig().get("note").contains("Management API unavailable"));
    }

    @Test
    void consumerLagUsesQueueDepthFromFallbackList() throws Exception {
        stubManagementApiUnavailable();
        var profile = StreamTestFixtures.profile(
                ProtocolType.RABBITMQ, "127.0.0.1:5672", Map.of("queue", "lag-queue"), null);

        var lags = inspector.consumerLag(profile, "unused", null);

        assertEquals(1, lags.size());
        assertEquals("lag-queue", lags.get(0).getTopic());
        assertEquals(0L, lags.get(0).getLag());
    }

    @Test
    void ac4_searchMessagesUsesNonDestructiveGetEndpoint() throws Exception {
        stubManagementApiResponse(200, "[]");
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);
        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("orders");

        inspector.searchMessages(profile, request);

        ArgumentCaptor<java.net.http.HttpRequest> captor = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertEquals("POST", captor.getValue().method());
        assertTrue(captor.getValue().uri().toString().endsWith("/orders/get"));
    }

    @Test
    void ac2_describeTopicIncludesQueueDepthFields() throws Exception {
        stubManagementApiResponse(
                200,
                "{\"messages\":12,\"messages_ready\":8,\"messages_unacknowledged\":4,\"consumers\":2}");
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);

        var detail = inspector.describeTopic(profile, "orders");

        assertEquals("orders", detail.getName());
        assertEquals("12", detail.getConfig().get("messages"));
        assertTrue(detail.getConfig().containsKey("messages_ready"));
        assertTrue(detail.getConfig().containsKey("messages_unacknowledged"));
    }

    @Test
    void ac6_clusterInfoNotesManagementPortRequirementWhenApiUnavailable() throws Exception {
        stubManagementApiUnavailable();
        var profile = StreamTestFixtures.profile(
                ProtocolType.RABBITMQ, "localhost:5672", Map.of("managementPort", "15672"), null);

        var info = inspector.clusterInfo(profile);

        assertTrue(info.getAttributes().containsKey("error"));
        assertTrue(info.getAttributes().containsKey("note"));
    }

    @Test
    void searchMessagesFailsWhenManagementApiReturnsError() throws Exception {
        stubManagementApiResponse(404, "{\"error\":\"not_found\"}");
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);
        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("missing-queue");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> inspector.searchMessages(profile, request));
        assertTrue(ex.getMessage().contains("RabbitMQ message get failed"));
    }

    @Test
    void describeConsumerGroupIsNotSupported() {
        var profile = StreamTestFixtures.profile(ProtocolType.RABBITMQ, "localhost:5672", null, null);

        assertThrows(UnsupportedOperationException.class, () -> inspector.describeConsumerGroup(profile, "g1"));
    }

    @Test
    void brokerInfoIncludesManagementUrl() throws Exception {
        stubManagementApiUnavailable();
        var profile = StreamTestFixtures.profile(
                ProtocolType.RABBITMQ, "rabbit.example:5672", Map.of("managementPort", "15672"), null);

        var info = inspector.brokerInfo(profile);

        assertEquals("http://rabbit.example:15672", info.get("managementUrl"));
    }
}
