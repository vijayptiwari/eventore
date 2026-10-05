package com.eventore.security;

import com.eventore.config.EventoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTokenFilterTest {
    @Test
    void rejectsJwtShapedTokenWhenOnlyStaticAuthenticationIsConfigured() throws Exception {
        EventoreProperties properties = new EventoreProperties();
        properties.getSecurity().setApiToken("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer e30.e30.c2ln");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ApiTokenFilter(properties).doFilter(request, response, (req, res) -> {
            throw new AssertionError("Forged token reached application");
        });
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsStaticTokensContainingDotsAndClearsIdentityAfterApplicationFailure() {
        EventoreProperties properties = new EventoreProperties();
        properties.getSecurity().setApiToken("static.token.with.dots");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/config");
        request.addHeader("Authorization", "Bearer static.token.with.dots");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new ApiTokenFilter(properties).doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getPrincipal()).isEqualTo(UserPrincipal.staticTokenUser());
            throw new IllegalStateException("application failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("application failure");
        assertThat(SecurityContextHolder.getPrincipal()).isEqualTo(UserPrincipal.devUser());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
