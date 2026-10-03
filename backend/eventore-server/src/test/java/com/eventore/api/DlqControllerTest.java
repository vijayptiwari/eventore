package com.eventore.api;

import com.eventore.dlq.DlqMessageInfo;
import com.eventore.dlq.DlqRedriveRequest;
import com.eventore.dlq.DlqRedriveResult;
import com.eventore.dlq.DlqService;
import com.eventore.dlq.DlqTopicSummary;
import com.eventore.domain.UnifiedMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqControllerTest {

    @Mock
    private DlqService dlqService;

    private DlqController controller;

    @BeforeEach
    void setUp() {
        controller = new DlqController(dlqService);
    }

    @Test
    void listDlqTopicsReturnsSummaries() {
        DlqTopicSummary summary = new DlqTopicSummary("orders.DLQ", "orders", 3, "Pattern match");
        when(dlqService.listDlqTopics("conn-1")).thenReturn(List.of(summary));

        ResponseEntity<List<DlqTopicSummary>> res = controller.listDlqTopics("conn-1");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsExactly(summary);
    }

    @Test
    void inspectDlqMessagesReturnsAnnotatedMessages() {
        UnifiedMessage msg = new UnifiedMessage();
        msg.setId("m-1");
        DlqMessageInfo info = new DlqMessageInfo(msg, "orders", "Error", null, null);
        when(dlqService.inspectDlqMessages("conn-1", "orders.DLQ", 20)).thenReturn(List.of(info));

        ResponseEntity<List<DlqMessageInfo>> res = controller.inspectDlqMessages("conn-1", "orders.DLQ", 20);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsExactly(info);
    }

    @Test
    void redriveMessagesDelegatesToService() {
        DlqRedriveRequest req = new DlqRedriveRequest("orders.DLQ", "orders", List.of("m-1"), null, null, 10);
        DlqRedriveResult expected = DlqRedriveResult.success("orders.DLQ", "orders", 1);
        when(dlqService.redrive("conn-1", req)).thenReturn(expected);

        ResponseEntity<DlqRedriveResult> res = controller.redriveMessages("conn-1", req);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isEqualTo(expected);
    }
}
