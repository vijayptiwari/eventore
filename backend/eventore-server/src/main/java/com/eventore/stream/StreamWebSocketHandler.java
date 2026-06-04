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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class StreamWebSocketHandler extends TextWebSocketHandler {

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
    private final Object sendLock = new Object();

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
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WsCommand command = objectMapper.readValue(message.getPayload(), WsCommand.class);
            switch (command.getType()) {
                case "SUBSCRIBE" -> handleSubscribe(session, command);
                case "UNSUBSCRIBE" -> handleUnsubscribe(session, command);
                case "START_LIVE_VIEW" -> handleStartLiveView(session, command);
                case "STOP_LIVE_VIEW" -> handleStopLiveView(session, command);
                case "HEARTBEAT" -> send(session, new StreamFrame("HEARTBEAT", null, null, null, null));
                default -> send(session, errorFrame(command.getClientStreamId(), null, "Unknown command"));
            }
        } catch (Exception e) {
            send(session, errorFrame(null, null, e.getMessage()));
        }
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
        try {
            LiveViewFilter.validateRegex(command.getHeaderRegex(), command.getBodyRegex());
        } catch (Exception e) {
            send(session, errorFrame(clientStreamId, null, "Invalid regex: " + e.getMessage()));
            return;
        }

        ConnectionProfile profile = connectionRegistry
                .find(command.getConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        policy.requireProtocol(profile.getProtocol());

        stopLiveView(session, clientStreamId, false);

        SubscribeRequest request = buildSubscribeRequest(command, "lv:" + clientStreamId, true);
        request.setDestinations(topics);
        request.setDestination(topics.get(0));
        request.setConsumerGroup("eventore-lv-" + clientStreamId);

        final String headerRegex = command.getHeaderRegex();
        final String bodyRegex = command.getBodyRegex();

        String subscriptionId = subscriptionManager.subscribe(
                profile,
                request,
                event -> {
                    if (!session.isOpen()) {
                        return;
                    }
                    if ("MESSAGE".equals(event.type()) && event.message() != null) {
                        if (!LiveViewFilter.matches(headerRegex, bodyRegex, event.message())) {
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
            request.getOptions().put("liveView", "true");
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
        synchronized (sendLock) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private record LiveViewHandle(
            String subscriptionId, ScheduledFuture<?> expiry, long expiresAt, List<String> topics) {}

    public static class WsCommand {
        private String type;
        private String connectionId;
        private String destination;
        private List<String> topics;
        private String consumerGroup;
        private String subscriptionId;
        private String clientStreamId;
        private String headerRegex;
        private String bodyRegex;
        private Integer durationMinutes;
        private Map<String, String> options;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public void setConnectionId(String connectionId) {
            this.connectionId = connectionId;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public void setSubscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
        }

        public String getClientStreamId() {
            return clientStreamId;
        }

        public void setClientStreamId(String clientStreamId) {
            this.clientStreamId = clientStreamId;
        }

        public String getHeaderRegex() {
            return headerRegex;
        }

        public void setHeaderRegex(String headerRegex) {
            this.headerRegex = headerRegex;
        }

        public String getBodyRegex() {
            return bodyRegex;
        }

        public void setBodyRegex(String bodyRegex) {
            this.bodyRegex = bodyRegex;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public Map<String, String> getOptions() {
            return options;
        }

        public void setOptions(Map<String, String> options) {
            this.options = options;
        }
    }
}
