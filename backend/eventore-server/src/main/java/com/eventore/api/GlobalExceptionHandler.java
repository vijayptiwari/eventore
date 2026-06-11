package com.eventore.api;

import com.eventore.dataplane.DataPlaneException;
import com.eventore.dataplane.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception-to-response mapping for the REST API. Extends
 * {@link ResponseEntityExceptionHandler} so standard Spring MVC exceptions (malformed JSON,
 * unsupported methods, validation failures, ...) keep their proper 4xx statuses instead of
 * falling into the catch-all 500 handler.
 *
 * <p>Stable error codes returned in the {@code code} field:
 * EVT-1400 validation/bad request, EVT-1404 resource not found, EVT-1501 not implemented,
 * EVT-1502 upstream broker error, EVT-1503 data plane unavailable, EVT-1500 internal error,
 * EVT-HTTP-&lt;status&gt; for explicit {@link ResponseStatusException}s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String CODE_BAD_REQUEST = "EVT-1400";
    static final String CODE_NOT_FOUND = "EVT-1404";
    static final String CODE_INTERNAL = "EVT-1500";
    static final String CODE_NOT_IMPLEMENTED = "EVT-1501";
    static final String CODE_UPSTREAM = "EVT-1502";
    static final String CODE_DATA_PLANE = "EVT-1503";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        if (status.is5xxServerError()) {
            log.error("Request failed ({}): {}", status.value(), message, ex);
        } else {
            log.warn("Request rejected ({}): {}", status.value(), message);
        }
        return ResponseEntity.status(status)
                .body(errorBody("EVT-HTTP-" + status.value(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(CODE_BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(CODE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.error("Upstream/broker operation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(CODE_UPSTREAM, ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedOperationException ex) {
        log.warn("Operation not implemented: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(errorBody(CODE_NOT_IMPLEMENTED, ex.getMessage()));
    }

    @ExceptionHandler(DataPlaneException.class)
    public ResponseEntity<Map<String, String>> handleDataPlane(DataPlaneException ex) {
        log.error("Data plane unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(CODE_DATA_PLANE, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(CODE_INTERNAL, "Internal server error"));
    }

    private static Map<String, String> errorBody(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", message != null ? message : "Unexpected error");
        return body;
    }
}
