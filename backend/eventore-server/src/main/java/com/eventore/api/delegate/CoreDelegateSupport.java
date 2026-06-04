package com.eventore.api.delegate;

import com.eventore.domain.ConnectionProfile;
import com.eventore.service.ConnectionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

final class CoreDelegateSupport {

    private CoreDelegateSupport() {}

    static ConnectionProfile profile(ConnectionRegistry registry, String connectionId) {
        return registry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
