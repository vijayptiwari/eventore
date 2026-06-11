package com.eventore.api;

import com.eventore.dataplane.DataPlaneException;
import com.eventore.dataplane.ResourceNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MalformedJsonProbeController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void unsupportedOperationReturns501() {
        var response = handler.handleUnsupported(new UnsupportedOperationException("not available"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertEquals("not available", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_NOT_IMPLEMENTED, response.getBody().get("code"));
    }

    @Test
    void illegalArgumentReturns400WithCode() {
        var response = handler.handleBadRequest(new IllegalArgumentException("bad input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad input", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_BAD_REQUEST, response.getBody().get("code"));
    }

    @Test
    void resourceNotFoundReturns404() {
        var response = handler.handleNotFound(new ResourceNotFoundException("Topic not found: orders"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Topic not found: orders", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_NOT_FOUND, response.getBody().get("code"));
    }

    @Test
    void catchAllReturnsSanitized500() {
        var response = handler.handleUnexpected(new RuntimeException("secret internal detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_INTERNAL, response.getBody().get("code"));
    }

    @Test
    void responseStatusExceptionKeepsStatusAndNullSafeMessage() {
        var response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, null));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("EVT-HTTP-403", response.getBody().get("code"));
    }

    @Test
    void illegalStateMapsToBadGatewayWithUpstreamCode() {
        var response = handler.handleIllegalState(new IllegalStateException("broker unreachable"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("broker unreachable", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_UPSTREAM, response.getBody().get("code"));
    }

    @Test
    void dataPlaneExceptionMapsToServiceUnavailable() {
        var response = handler.handleDataPlane(new DataPlaneException("No data-plane provider for KAFKA"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("No data-plane provider for KAFKA", response.getBody().get("error"));
        assertEquals(GlobalExceptionHandler.CODE_DATA_PLANE, response.getBody().get("code"));
    }

    @Test
    void malformedJsonReturns400ViaResponseEntityExceptionHandler() throws Exception {
        var result = mockMvc.perform(post("/probe/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(
                body.contains(GlobalExceptionHandler.CODE_INTERNAL),
                "Malformed JSON must not be handled by the EVT-1500 catch-all");
    }

    @RestController
    static class MalformedJsonProbeController {
        @PostMapping("/probe/json")
        void acceptJson(@RequestBody Map<String, String> body) {}
    }
}
