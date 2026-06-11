package com.eventore.stream;

import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import com.eventore.service.MetricsService;
import com.eventore.service.SubscriptionManager;
import com.eventore.service.SubscriptionManager.StreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class StreamWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamWebSocketHandler.class);

    private static final Set<Integer> ALLOWED_LIVE_VIEW_MINUTES = Set.of(1, 2, 5, 10);
    private static final int MAX_LIVE_VIEW_TOPICS = 32;

    private final ObjectMapper objectMapper;
    private final ConnectionRegistry connectionRegistry;
    private final SubscriptionManager subscriptionManager;
    private final MetricsService metricsService;
    private final DeploymentModePolicy policy;
    private final ScheduledExecutorService liveViewScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "eventore-liveview");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, Map<String, String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, LiveViewHandle>> liveViewsByWsSession = new ConcurrentHashMap<>();
    /** Per-session send locks (removed on close) so one slow session never blocks the others. */
    private final Map<String, Object> sessionSendLocks = new ConcurrentHashMap<>();

    public StreamWebSocketHandler(
            ObjectMapper objectMapper,
            ConnectionRegistry connectionRegistry,
            SubscriptionManager subscriptionManager,
            MetricsService metricsService,
            DeploymentModePolicy policy) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.subscriptionManager = subscriptionManager;
        this.metricsService = metricsService;
        this.policy = policy;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        metricsService.incrementWs();
        sessionSubscriptions.put(session.getId(), new ConcurrentHashMap<>());
        liveViewsByWsSession.put(session.getId(), new ConcurrentHashMap<>());
        sessionSendLocks.put(session.getId(), new Object());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WsCommand command = objectMapper.readValue(message.getPayload(), WsCommand.class);
            WsCommand.validateType(command);
            switch (command.getType()) {
                case "SUBSCRIBE" -> handleSubscribe(session, command);
                case "UNSUBSCRIBE" -> handleUnsubscribe(session, command);
                case "START_LIVE_VIEW" -> handleStartLiveView(session, command);
                case "STOP_LIVE_VIEW" -> handleStopLiveView(session, command);
                case "HEARTBEAT" -> send(session, new StreamFrame("HEARTBEAT", null, null, null, null));
                default -> send(session, errorFrame(command.getClientStreamId(), null, "Unknown command"));
            }
        } catch (JsonProcessingException e) {
            log.warn("Malformed WebSocket command JSON for session {}: {}", session.getId(), e.getOriginalMessage());
            send(session, errorFrame(null, null, "Malformed command JSON"));
        } catch (IllegalArgumentException | ResponseStatusException e) {
            // Validation/policy failures carry messages intended for the client.
            log.warn("WebSocket command rejected for session {}: {}", session.getId(), e.getMessage());
            send(session, errorFrame(null, null, clientDetail(e)));
        } catch (Exception e) {
            log.error("WebSocket command failed for session {}", session.getId(), e);
            send(session, errorFrame(null, null, "Internal error processing command"));
        }
    }

    @PreDestroy
    void shutdownLiveViewScheduler() {
        liveViewScheduler.shutdownNow();
    }

    private static String clientDetail(Exception e) {
        if (e instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return e.getMessage() != null ? e.getMessage() : "Invalid command";
    }

    private void handleSubscribe(WebSocketSession session, WsCommand command) {
        policy.require(Action.SUBSCRIBE);
        String clientStreamId = command.getClientStreamId();
        if (clientStreamId == null || clientStreamId.isBlank()) {
            send(session, errorFrame(null, null, "clientStreamId is required"));
            return;
        }
        ConnectionProfile profile = connectionRegistry
                .find(command.getConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        policy.requireProtocol(profile.getProtocol());
        Map<String, String> subs =
                sessionSubscriptions.computeIfAbsent(session.getId(), k -> new ConcurrentHashMap<>());
        String previous = subs.get(clientStreamId);
        if (previous != null) {
            subscriptionManager.unsubscribe(previous);
        }
        SubscribeRequest request = buildSubscribeRequest(command, clientStreamId, false);
        String subscriptionId = subscriptionManager.subscribe(
                profile,
                request,
                event -> {
                    if (session.isOpen()) {
                        send(session, toFrame(event, clientStreamId));
                    }
                },
                false);
        subs.put(clientStreamId, subscriptionId);
        send(session, new StreamFrame("SUBSCRIBED", subscriptionId, clientStreamId, null, null));
    }

    private void handleStartLiveView(WebSocketSession session, WsCommand command) {
        policy.require(Action.SUBSCRIBE);
        String clientStreamId = command.getClientStreamId();
        if (clientStreamId == null || clientStreamId.isBlank()) {
            send(session, errorFrame(null, null, "clientStreamId is required"));
            return;
        }
        List<String> topics = command.getTopics();
        if (topics == null || topics.isEmpty()) {
            send(session, errorFrame(clientStreamId, null, "At least one topic is required"));
            return;
        }
        if (topics.size() > MAX_LIVE_VIEW_TOPICS) {
            send(session, errorFrame(clientStreamId, null, "Maximum " + MAX_LIVE_VIEW_TOPICS + " topics"));
            return;
        }
        int duration = command.getDurationMinutes() != null ? command.getDurationMinutes() : 0;
        if (!ALLOWED_LIVE_VIEW_MINUTES.contains(duration)) {
            send(session, errorFrame(clientStreamId, null, "durationMinutes must be 1, 2, 5, or 10"));
            return;
        }
        final LiveViewFilter.Compiled filter;
        try {
            filter = LiveViewFilter.compile(command.getHeaderRegex(), command.getBodyRegex());
        } catch (IllegalArgumentException e) {
            send(session, errorFrame(clientStreamId, null, e.getMessage()));
            return;
        }

        SubscribeRequest request = buildSubscribeRequest(command, "lv:" + clientStreamId, true);
        request.setDestinations(topics);
        request.setDestination(topics.get(0));
        request.setConsumerGroup("eventore-lv-" + clientStreamId);

        ConnectionProfile profile = connectionRegistry
                .find(command.getConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        policy.requireProtocol(profile.getProtocol());

        stopLiveView(session, clientStreamId, false);

        String subscriptionId = subscriptionManager.subscribe(
                profile,
                request,
                event -> {
                    if (!session.isOpen()) {
                        return;
                    }
                    if ("MESSAGE".equals(event.type()) && event.message() != null) {
                        if (!LiveViewFilter.matches(filter, event.message())) {
                            return;
                        }
                        send(session, liveViewMessageFrame(event, clientStreamId));
                    } else {
                        send(session, toFrame(event, clientStreamId));
                    }
                },
                false);

        long expiresAt = System.currentTimeMillis() + duration * 60_000L;
        ScheduledFuture<?> expiry = liveViewScheduler.schedule(
                () -> expireLiveView(session, clientStreamId), duration, TimeUnit.MINUTES);

        liveViewsByWsSession
                .computeIfAbsent(session.getId(), k -> new ConcurrentHashMap<>())
                .put(clientStreamId, new LiveViewHandle(subscriptionId, expiry, expiresAt, topics));

        StreamFrame subscribed = new StreamFrame("LIVE_VIEW_STARTED", subscriptionId, clientStreamId, null, null);
        subscribed.setExpiresAt(expiresAt);
        send(session, subscribed);
    }

    private void handleStopLiveView(WebSocketSession session, WsCommand command) {
        String clientStreamId = command.getClientStreamId();
        if (clientStreamId == null || clientStreamId.isBlank()) {
            send(session, errorFrame(null, null, "clientStreamId is required"));
            return;
        }
        stopLiveView(session, clientStreamId, true);
    }

    private void expireLiveView(WebSocketSession session, String clientStreamId) {
        LiveViewHandle handle = stopLiveView(session, clientStreamId, false);
        if (handle != null && session.isOpen()) {
            send(session, new StreamFrame("LIVE_VIEW_EXPIRED", handle.subscriptionId(), clientStreamId, null, null));
        }
    }

    private LiveViewHandle stopLiveView(WebSocketSession session, String clientStreamId, boolean notify) {
        Map<String, LiveViewHandle> liveViews = liveViewsByWsSession.get(session.getId());
        if (liveViews == null) {
            return null;
        }
        LiveViewHandle handle = liveViews.remove(clientStreamId);
        if (handle == null) {
            return null;
        }
        handle.expiry().cancel(false);
        subscriptionManager.unsubscribe(handle.subscriptionId());
        if (notify && session.isOpen()) {
            send(session, new StreamFrame("LIVE_VIEW_STOPPED", handle.subscriptionId(), clientStreamId, null, null));
        }
        return handle;
    }

    private SubscribeRequest buildSubscribeRequest(WsCommand command, String subscriptionKey, boolean liveView) {
        SubscribeRequest request = new SubscribeRequest();
        request.setDestination(command.getDestination());
        if (command.getTopics() != null && !command.getTopics().isEmpty()) {
            request.setDestinations(command.getTopics());
        }
        request.setConsumerGroup(command.getConsumerGroup());
        request.setSubscriptionKey(subscriptionKey);
        if (command.getOptions() != null) {
            request.setOptions(command.getOptions());
        }
        if (liveView) {
            Map<String, String> options = request.getOptions();
            if (options == null) {
                options = new HashMap<>();
                request.setOptions(options);
            }
            options.put("liveView", "true");
        }
        return request;
    }

    private void handleUnsubscribe(WebSocketSession session, WsCommand command) {
        Map<String, String> subs = sessionSubscriptions.get(session.getId());
        String subId = command.getSubscriptionId();
        String clientStreamId = command.getClientStreamId();
        if (subId == null && clientStreamId != null && subs != null) {
            subId = subs.remove(clientStreamId);
        }
        if (subId != null) {
            subscriptionManager.unsubscribe(subId);
        }
        send(session, new StreamFrame("UNSUBSCRIBED", subId, clientStreamId, null, null));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        metricsService.decrementWs();
        Map<String, String> subs = sessionSubscriptions.remove(session.getId());
        if (subs != null) {
            for (String subId : subs.values()) {
                subscriptionManager.unsubscribe(subId);
            }
        }
        Map<String, LiveViewHandle> liveViews = liveViewsByWsSession.remove(session.getId());
        if (liveViews != null) {
            for (LiveViewHandle handle : liveViews.values()) {
                handle.expiry().cancel(false);
                subscriptionManager.unsubscribe(handle.subscriptionId());
            }
        }
        sessionSendLocks.remove(session.getId());
    }

    private StreamFrame toFrame(StreamEvent event, String clientStreamId) {
        return new StreamFrame(
                event.type(), event.subscriptionId(), clientStreamId, event.message(), event.detail());
    }

    private StreamFrame liveViewMessageFrame(StreamEvent event, String clientStreamId) {
        return new StreamFrame(
                "LIVE_VIEW_MESSAGE", event.subscriptionId(), clientStreamId, event.message(), event.detail());
    }

    private StreamFrame errorFrame(String clientStreamId, String subscriptionId, String detail) {
        return new StreamFrame("ERROR", subscriptionId, clientStreamId, null, detail);
    }

    private void send(WebSocketSession session, StreamFrame frame) {
        Object lock = sessionSendLocks.computeIfAbsent(session.getId(), k -> new Object());
        synchronized (lock) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception e) {
                log.warn("Failed to send WebSocket frame to session {}", session.getId(), e);
            }
        }
    }

    private record LiveViewHandle(
            String subscriptionId, ScheduledFuture<?> expiry, long expiresAt, List<String> topics) {}
}
