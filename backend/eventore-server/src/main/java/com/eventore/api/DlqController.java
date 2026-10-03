package com.eventore.api;

import com.eventore.dlq.DlqMessageInfo;
import com.eventore.dlq.DlqRedriveRequest;
import com.eventore.dlq.DlqRedriveResult;
import com.eventore.dlq.DlqService;
import com.eventore.dlq.DlqTopicSummary;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connections/{connectionId}/dlq")
public class DlqController {

    private final DlqService dlqService;

    public DlqController(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<DlqTopicSummary>> listDlqTopics(
            @PathVariable("connectionId") String connectionId) {
        return ResponseEntity.ok(dlqService.listDlqTopics(connectionId));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<DlqMessageInfo>> inspectDlqMessages(
            @PathVariable("connectionId") String connectionId,
            @RequestParam("topic") String topic,
            @RequestParam(value = "max", required = false, defaultValue = "50") Integer max) {
        return ResponseEntity.ok(dlqService.inspectDlqMessages(connectionId, topic, max));
    }

    @PostMapping("/redrive")
    public ResponseEntity<DlqRedriveResult> redriveMessages(
            @PathVariable("connectionId") String connectionId,
            @RequestBody DlqRedriveRequest request) {
        return ResponseEntity.ok(dlqService.redrive(connectionId, request));
    }
}
