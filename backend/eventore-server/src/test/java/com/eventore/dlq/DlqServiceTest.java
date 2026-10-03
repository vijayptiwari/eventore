package com.eventore.dlq;

import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.InspectorRegistry;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private ConnectorRegistry connectorRegistry;

    @Mock
    private InspectorRegistry inspectorRegistry;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private AuditService auditService;

    @Mock
    private MessagingInspector inspector;

    @Mock
    private MessagingConnector connector;

    private DlqService dlqService;
    private ConnectionProfile profile;

    @BeforeEach
    void setUp() {
        dlqService = new DlqService(
                connectionRegistry, connectorRegistry, inspectorRegistry, policy, auditService);

        profile = new ConnectionProfile();
        profile.setId("conn-kafka-1");
        profile.setProtocol(ProtocolType.KAFKA);
    }

    @Test
    void listDlqTopicsDetectsDlqPatternsAndInfersTargets() {
        when(connectionRegistry.find("conn-kafka-1")).thenReturn(Optional.of(profile));
        when(inspectorRegistry.get(ProtocolType.KAFKA)).thenReturn(inspector);

        TopicDetail t1 = new TopicDetail();
        t1.setName("orders");
        TopicDetail t2 = new TopicDetail();
        t2.setName("orders.DLQ");
        TopicDetail t3 = new TopicDetail();
        t3.setName("payments-dlt");

        when(inspector.listTopics(profile, null)).thenReturn(List.of(t1, t2, t3));

        List<DlqTopicSummary> dlqs = dlqService.listDlqTopics("conn-kafka-1");
        assertThat(dlqs).hasSize(2);
        assertThat(dlqs.get(0).dlqTopic()).isEqualTo("orders.DLQ");
        assertThat(dlqs.get(0).inferredTargetTopic()).isEqualTo("orders");
        assertThat(dlqs.get(1).dlqTopic()).isEqualTo("payments-dlt");
        assertThat(dlqs.get(1).inferredTargetTopic()).isEqualTo("payments");
    }

    @Test
    void inspectDlqMessagesExtractsErrorAnnotations() {
        when(connectionRegistry.find("conn-kafka-1")).thenReturn(Optional.of(profile));
        when(inspectorRegistry.get(ProtocolType.KAFKA)).thenReturn(inspector);

        UnifiedMessage msg = new UnifiedMessage();
        msg.setId("err-99");
        msg.setPayload("{\"bad\":\"data\"}");
        msg.setHeaders(Map.of(
                "x-original-topic", "orders",
                "x-exception-message", "Deserialization error"
        ));

        when(inspector.searchMessages(eq(profile), any())).thenReturn(List.of(msg));

        List<DlqMessageInfo> results = dlqService.inspectDlqMessages("conn-kafka-1", "orders.DLQ", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).originalTopic()).isEqualTo("orders");
        assertThat(results.get(0).failureReason()).isEqualTo("Deserialization error");
        assertThat(results.get(0).message().getId()).isEqualTo("err-99");
    }

    @Test
    void redriveSingleEditedMessagePublishesToTargetWithRedriveHeaders() {
        when(connectionRegistry.find("conn-kafka-1")).thenReturn(Optional.of(profile));
        when(connectorRegistry.get(ProtocolType.KAFKA)).thenReturn(connector);

        doNothing().when(policy).require(Action.PUBLISH);
        doNothing().when(policy).requireProtocol(ProtocolType.KAFKA);
        doNothing().when(policy).validatePublishSize(anyLong());

        DlqRedriveRequest request = new DlqRedriveRequest(
                "orders.DLQ",
                "orders",
                List.of("err-99"),
                "{\"fixed\":\"data\"}",
                Map.of("correlationId", "c-1"),
                1
        );

        DlqRedriveResult result = dlqService.redrive("conn-kafka-1", request);
        assertThat(result.redrivenCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.targetTopic()).isEqualTo("orders");

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(connector).publish(eq(profile), captor.capture());

        PublishRequest pubReq = captor.getValue();
        assertThat(pubReq.getDestination()).isEqualTo("orders");
        assertThat(pubReq.getPayload()).isEqualTo("{\"fixed\":\"data\"}");
        assertThat(pubReq.getHeaders()).containsEntry("x-eventore-redriven-from", "orders.DLQ");
        assertThat(pubReq.getHeaders()).containsKey("x-eventore-redrive-timestamp");
    }
}
