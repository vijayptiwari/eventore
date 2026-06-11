package com.eventore.api.delegate;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.inspect.InspectorRegistry;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectApiDelegateImplTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private InspectorRegistry inspectorRegistry;

    @Mock
    private DeploymentModePolicy policy;

    @Mock
    private MessagingInspector inspector;

    private InspectApiDelegateImpl delegate;

    @BeforeEach
    void setUp() {
        delegate = new InspectApiDelegateImpl(
                connectionRegistry, inspectorRegistry, policy, new AuditService());
    }

    @Test
    void inspectCapabilitiesReturns403WhenPolicyRejectsBrowse() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "not allowed"))
                .when(policy)
                .require(Action.BROWSE_DESTINATIONS);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> delegate.inspectCapabilities("conn-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void searchMessagesAuditsAndDelegatesWhenAllowed() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-1");
        profile.setProtocol(ProtocolType.KAFKA);
        when(connectionRegistry.find("conn-1")).thenReturn(Optional.of(profile));
        when(inspectorRegistry.get(ProtocolType.KAFKA)).thenReturn(inspector);
        when(inspector.searchMessages(any(), any())).thenReturn(java.util.List.of());

        MessageSearchRequest request = new MessageSearchRequest();
        request.setTopic("orders");
        request.setMaxMessages(5);

        delegate.searchMessages("conn-1", request);

        verify(policy).require(Action.BROWSE_DESTINATIONS);
        verify(inspector).searchMessages(profile, request);
    }
}
