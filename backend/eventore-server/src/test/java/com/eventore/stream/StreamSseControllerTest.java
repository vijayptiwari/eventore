package com.eventore.stream;

import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StreamSseControllerTest {

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private DeploymentModePolicy policy;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamSseController controller =
                new StreamSseController(subscriptionManager, new ObjectMapper(), policy);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        doNothing().when(policy).require(Action.SUBSCRIBE);
    }

    @Test
    void streamReturns403WhenConnectionDoesNotOwnSubscription() throws Exception {
        when(subscriptionManager.ownsSubscription(eq("conn-a"), eq("sub-1"))).thenReturn(false);

        mockMvc.perform(get("/api/v1/stream/sub-1").param("connectionId", "conn-a"))
                .andExpect(status().isForbidden());
    }
}
