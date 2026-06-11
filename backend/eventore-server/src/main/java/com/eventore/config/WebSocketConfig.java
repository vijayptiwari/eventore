package com.eventore.config;

import com.eventore.stream.StreamWebSocketHandler;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.eventore.security.ApiTokenFilter;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final StreamWebSocketHandler streamWebSocketHandler;
    private final EventoreProperties properties;

    public WebSocketConfig(StreamWebSocketHandler streamWebSocketHandler, EventoreProperties properties) {
        this.streamWebSocketHandler = streamWebSocketHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamWebSocketHandler, "/ws/stream")
                .setAllowedOriginPatterns(properties.getSecurity().allowedOriginsArray())
                .addInterceptors(tokenHandshakeInterceptor());
    }

    private HandshakeInterceptor tokenHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Map<String, Object> attributes) {
                if (!properties.getSecurity().isAuthEnabled()) {
                    return true;
                }
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    String token = ApiTokenFilter.extractToken(servletRequest.getServletRequest());
                    if (token != null
                            && ApiTokenFilter.constantTimeEquals(
                                    properties.getSecurity().getApiToken(), token)) {
                        return true;
                    }
                }
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            @Override
            public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Exception exception) {}
        };
    }
}
