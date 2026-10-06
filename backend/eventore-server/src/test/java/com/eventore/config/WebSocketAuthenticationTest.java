package com.eventore.config;

import com.eventore.security.ApiTokenFilter;
import com.eventore.security.UserPrincipal;
import com.eventore.stream.StreamWebSocketHandler;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebSocketAuthenticationTest {
    @Test
    void handshakeRequiresAndPreservesIdentityValidatedByFilter() throws Exception {
        EventoreProperties properties = new EventoreProperties();
        properties.getSecurity().setJwtEnabled(true);
        var handler = mock(StreamWebSocketHandler.class);
        var interceptor = new WebSocketConfig(handler, properties).tokenHandshakeInterceptor();
        var request = new MockHttpServletRequest();
        var attributes = new HashMap<String, Object>();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()), handler, attributes)).isFalse();
        request.setAttribute(ApiTokenFilter.PRINCIPAL_ATTRIBUTE, UserPrincipal.staticTokenUser());
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()), handler, attributes)).isTrue();
        assertThat(attributes).containsEntry(ApiTokenFilter.PRINCIPAL_ATTRIBUTE, UserPrincipal.staticTokenUser());
    }
}
