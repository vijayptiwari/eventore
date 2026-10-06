package com.eventore.stream;

import com.eventore.security.*;
import com.eventore.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StreamWebSocketPrincipalTest {
    @Test
    void commandsUseSessionIdentityAndClearItAfterward() {
        var principal = new UserPrincipal("reader", "", "", Set.of(UserRole.VIEWER), Set.of("default"), "default", true);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session");
        when(session.getAttributes()).thenReturn(Map.of(ApiTokenFilter.PRINCIPAL_ATTRIBUTE, principal));
        var policy = mock(DeploymentModePolicy.class);
        doAnswer(inv -> {
            assertThat(SecurityContextHolder.getPrincipal()).isEqualTo(principal);
            return null;
        }).when(policy).require(Action.SUBSCRIBE);
        var handler = new StreamWebSocketHandler(new ObjectMapper(), mock(ConnectionRegistry.class), mock(SubscriptionManager.class), mock(MetricsService.class), policy);
        try {
            handler.handleTextMessage(session, new TextMessage("{\"type\":\"SUBSCRIBE\"}"));
            verify(policy).require(Action.SUBSCRIBE);
            assertThat(SecurityContextHolder.getPrincipal()).isEqualTo(UserPrincipal.devUser());
        } finally {
            handler.shutdownLiveViewScheduler();
        }
    }
}
