package com.eventore.stream;

import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.SubscriptionManager.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/stream")
public class StreamSseController {

    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper;
    private final DeploymentModePolicy policy;

    public StreamSseController(
            SubscriptionManager subscriptionManager,
            ObjectMapper objectMapper,
            DeploymentModePolicy policy) {
        this.subscriptionManager = subscriptionManager;
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    @GetMapping(value = "/{subscriptionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String subscriptionId) {
        policy.require(Action.SUBSCRIBE);
        BlockingQueue<StreamEvent> queue = subscriptionManager.queue(subscriptionId);
        SseEmitter emitter = new SseEmitter(Duration.ofHours(1).toMillis());
        Thread pump = new Thread(() -> pumpEvents(subscriptionId, queue, emitter), "sse-" + subscriptionId);
        pump.setDaemon(true);
        pump.start();
        Runnable cleanup = () -> {
            pump.interrupt();
            subscriptionManager.unsubscribe(subscriptionId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());
        return emitter;
    }

    private void pumpEvents(String subscriptionId, BlockingQueue<StreamEvent> queue, SseEmitter emitter) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                StreamEvent event = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (event == null) {
                    emitter.send(SseEmitter.event()
                            .name("HEARTBEAT")
                            .data(objectMapper.writeValueAsString(
                                    new StreamFrame("HEARTBEAT", subscriptionId, null, null, null))));
                    continue;
                }
                StreamFrame frame = new StreamFrame(
                        event.type(), event.subscriptionId(), null, event.message(), event.detail());
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(objectMapper.writeValueAsString(frame)));
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
