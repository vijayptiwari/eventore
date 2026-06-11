package com.eventore.stream;

import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.SubscriptionManager.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/stream")
public class StreamSseController {

    private static final Logger log = LoggerFactory.getLogger(StreamSseController.class);

    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper;
    private final DeploymentModePolicy policy;
    private final ExecutorService pumpExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("sse-pump-", 0).factory());

    public StreamSseController(
            SubscriptionManager subscriptionManager,
            ObjectMapper objectMapper,
            DeploymentModePolicy policy) {
        this.subscriptionManager = subscriptionManager;
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    @GetMapping(value = "/{subscriptionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String subscriptionId, @RequestParam String connectionId) {
        policy.require(Action.SUBSCRIBE);
        if (!subscriptionManager.ownsSubscription(connectionId, subscriptionId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Subscription not owned by connection");
        }
        BlockingQueue<StreamEvent> queue = subscriptionManager.queue(subscriptionId);
        log.debug("Opening SSE stream for subscription {} on connection {}", subscriptionId, connectionId);
        SseEmitter emitter = new SseEmitter(Duration.ofHours(1).toMillis());
        Future<?> pump = pumpExecutor.submit(() -> pumpEvents(subscriptionId, queue, emitter));
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (!cleanedUp.compareAndSet(false, true)) {
                return;
            }
            pump.cancel(true);
            subscriptionManager.unsubscribe(subscriptionId);
        };
        emitter.onCompletion(() -> {
            log.debug("SSE stream completed for subscription {}", subscriptionId);
            cleanup.run();
        });
        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for subscription {}", subscriptionId);
            cleanup.run();
        });
        emitter.onError(ex -> {
            log.warn("SSE stream error for subscription {}", subscriptionId, ex);
            cleanup.run();
        });
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.debug("SSE event pump stopped for subscription {}", subscriptionId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // emitter already completed by client disconnect
                }
            }
        }
    }

    @PreDestroy
    void shutdownPumps() {
        pumpExecutor.shutdownNow();
    }
}
