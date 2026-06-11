package com.eventore.inspect.kafka;

import com.eventore.dataplane.ResourceNotFoundException;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.testsupport.StreamTestFixtures;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class KafkaMessagingInspectorTest {

    private KafkaMessagingInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new KafkaMessagingInspector();
    }

    @Test
    void protocolIsKafka() {
        assertEquals(ProtocolType.KAFKA, inspector.protocol());
    }

    @Test
    void capabilitiesIncludeKafkaAdminFeatures() {
        var features = inspector.capabilities().getFeatures();

        assertTrue(features.contains("cluster"));
        assertTrue(features.contains("consumer-groups"));
        assertTrue(features.contains("message-search"));
        assertTrue(features.contains("topic-create"));
        assertTrue(features.contains("acl-list"));
        assertFalse(features.isEmpty());
    }

    @Test
    void searchMessagesRequiresTopic() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        MessageSearchRequest request = new MessageSearchRequest();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> inspector.searchMessages(profile, request));
        assertEquals("topic is required", ex.getMessage());
    }

    @Test
    void describeConsumerGroupThrowsResourceNotFoundForUnknownGroup() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        AdminClient admin = mock(AdminClient.class);
        DescribeConsumerGroupsResult result = mock(DescribeConsumerGroupsResult.class);
        KafkaFuture<Map<String, ConsumerGroupDescription>> future = KafkaFuture.completedFuture(Map.of());
        when(admin.describeConsumerGroups(anyCollection())).thenReturn(result);
        when(result.all()).thenReturn(future);

        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            assertThrows(ResourceNotFoundException.class,
                    () -> inspector.describeConsumerGroup(profile, "missing-group"));
        }
    }

    @Test
    void consumerLagFiltersByTopicSubstring() throws Exception {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        AdminClient admin = mock(AdminClient.class);
        TopicPartition topicA = new TopicPartition("orders-a", 0);
        TopicPartition topicB = new TopicPartition("orders-b", 0);
        Map<TopicPartition, OffsetAndMetadata> committed = Map.of(
                topicA, new OffsetAndMetadata(10L),
                topicB, new OffsetAndMetadata(20L));
        ListConsumerGroupOffsetsResult offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
        when(admin.listConsumerGroupOffsets("my-group")).thenReturn(offsetsResult);
        when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(committed));

        ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
        when(admin.listOffsets(any())).thenReturn(listOffsetsResult);
        ListOffsetsResult.ListOffsetsResultInfo endA = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        ListOffsetsResult.ListOffsetsResultInfo endB = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        when(endA.offset()).thenReturn(15L);
        when(endB.offset()).thenReturn(25L);
        when(listOffsetsResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(topicA, endA, topicB, endB)));

        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            List<GroupOffset> lags = inspector.consumerLag(profile, "my-group", "orders-a");

            assertEquals(1, lags.size());
            assertEquals("orders-a", lags.get(0).getTopic());
            assertEquals(0, lags.get(0).getPartition());
            assertEquals(5L, lags.get(0).getLag());
        }
    }

    @Test
    void describeTopicThrowsResourceNotFoundForUnknownTopic() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        AdminClient admin = mock(AdminClient.class);
        DescribeTopicsResult result = mock(DescribeTopicsResult.class);
        KafkaFuture<Map<String, TopicDescription>> future = KafkaFuture.completedFuture(Map.of());
        when(admin.describeTopics(anyCollection())).thenReturn(result);
        when(result.allTopicNames()).thenReturn(future);

        try (MockedStatic<AdminClient> mocked = mockStatic(AdminClient.class)) {
            mocked.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            assertThrows(ResourceNotFoundException.class, () -> inspector.describeTopic(profile, "missing"));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchMessagesEncodesBinaryPayloadsAsBase64() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("bin-topic");
        request.setPartition("0");
        request.setStartAt("earliest");
        request.setMaxMessages(1);
        byte[] binary = {(byte) 0xC3, (byte) 0x28, (byte) 0x00, (byte) 0xFF};
        TopicPartition tp = new TopicPartition("bin-topic", 0);
        ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(
                Map.of(tp, List.of(new ConsumerRecord<>("bin-topic", 0, 0L, null, binary))));

        try (MockedConstruction<KafkaConsumer> consumers = mockConstruction(
                KafkaConsumer.class,
                (mock, ctx) -> when(mock.poll(any(Duration.class))).thenReturn(records))) {
            List<UnifiedMessage> found = inspector.searchMessages(profile, request);

            assertEquals(1, found.size());
            assertEquals(Base64.getEncoder().encodeToString(binary), found.get(0).getPayload());
            assertEquals("application/base64", found.get(0).getContentType());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchMessagesPassesTextPayloadsThroughAsPlainText() {
        var profile = StreamTestFixtures.profile(ProtocolType.KAFKA, "localhost:9092", null, null);
        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("text-topic");
        request.setPartition("0");
        request.setStartAt("earliest");
        request.setMaxMessages(1);
        TopicPartition tp = new TopicPartition("text-topic", 0);
        ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(
                Map.of(tp, List.of(new ConsumerRecord<>(
                        "text-topic", 0, 0L, "k1", "hello kafka".getBytes()))));

        try (MockedConstruction<KafkaConsumer> consumers = mockConstruction(
                KafkaConsumer.class,
                (mock, ctx) -> when(mock.poll(any(Duration.class))).thenReturn(records))) {
            List<UnifiedMessage> found = inspector.searchMessages(profile, request);

            assertEquals(1, found.size());
            assertEquals("hello kafka", found.get(0).getPayload());
            assertEquals("text/plain", found.get(0).getContentType());
            assertEquals("k1", found.get(0).getHeaders().get("key"));
        }
    }
}
