package com.eventore.security;

import com.eventore.config.EventoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Static API token authentication. Active only when eventore.security.api-token is set.
 * Accepts the token via "Authorization: Bearer &lt;token&gt;" or "X-API-Key" header,
 * or "token" query parameter (WebSocket/SSE clients that cannot set headers).
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final EventoreProperties properties;
    private final JwtTokenValidator jwtTokenValidator;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiTokenFilter(EventoreProperties properties, JwtTokenValidator jwtTokenValidator) {
        this.properties = properties;
        this.jwtTokenValidator = jwtTokenValidator != null ? jwtTokenValidator : new JwtTokenValidator(null);
    }

    public ApiTokenFilter(EventoreProperties properties) {
        this(properties, null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        return !properties.getSecurity().isAuthEnabled();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String provided = extractToken(request);
            if (provided != null) {
                String expected = properties.getSecurity().getApiToken();
                if (!expected.isBlank() && constantTimeEquals(expected, provided)) {
                    SecurityContextHolder.setPrincipal(UserPrincipal.staticTokenUser());
                    chain.doFilter(request, response);
                    return;
                }
                if (properties.getSecurity().isJwtEnabled() && jwtTokenValidator.isJwtFormat(provided)) {
                    UserPrincipal principal;
                    try {
                        principal = jwtTokenValidator.validateToken(
                                provided,
                                properties.getSecurity().getJwtSecret(),
                                properties.getSecurity().getJwtIssuer(),
                                properties.getSecurity().getJwtAudience());
                    } catch (IllegalArgumentException e) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"Invalid or expired JWT token\"}");
                        return;
                    }
                    SecurityContextHolder.setPrincipal(principal);
                    chain.doFilter(request, response);
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Missing or invalid API token\"}");
        } finally {
            SecurityContextHolder.clear();
        }
    }

    public static String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length()).trim();
        }
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }

    public static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
