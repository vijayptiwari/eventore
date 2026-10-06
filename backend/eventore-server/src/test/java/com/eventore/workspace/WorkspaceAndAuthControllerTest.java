package com.eventore.workspace;

import com.eventore.security.AuthController;
import com.eventore.security.SecurityContextHolder;
import com.eventore.security.UserPrincipal;
import com.eventore.security.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceAndAuthControllerTest {

    private WorkspaceManager workspaceManager;
    private WorkspaceController workspaceController;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        workspaceManager = new WorkspaceManager();
        workspaceController = new WorkspaceController(workspaceManager, org.mockito.Mockito.mock(com.eventore.security.DeploymentModePolicy.class));
        authController = new AuthController();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void authMeReturnsAuthenticatedPrincipalDetails() {
        UserPrincipal principal = new UserPrincipal(
                "usr-441",
                "sarah@acme.com",
                "Sarah Connor",
                Set.of(UserRole.PLATFORM_ADMIN),
                Set.of("*", "default", "finance"),
                "default",
                true);
        SecurityContextHolder.setPrincipal(principal);

        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("userId")).isEqualTo("usr-441");
        assertThat(body.get("email")).isEqualTo("sarah@acme.com");
        assertThat(body.get("displayName")).isEqualTo("Sarah Connor");
        assertThat(body.get("isPlatformAdmin")).isEqualTo(true);
        assertThat(body.get("isJwt")).isEqualTo(true);
    }

    @Test
    void workspaceControllerListsWorkspacesAndCreatesNew() {
        UserPrincipal admin = UserPrincipal.devUser();
        SecurityContextHolder.setPrincipal(admin);

        ResponseEntity<List<Workspace>> listResp = workspaceController.listWorkspaces();
        assertThat(listResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listResp.getBody()).isNotEmpty();

        Workspace newWs = Workspace.of("test-ws", "Test WS", "Testing description");
        ResponseEntity<Workspace> createResp = workspaceController.createWorkspace(newWs);
        assertThat(createResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(createResp.getBody()).isNotNull();
        assertThat(createResp.getBody().id()).isEqualTo("test-ws");

        ResponseEntity<Workspace> getResp = workspaceController.getWorkspace("test-ws");
        assertThat(getResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(getResp.getBody() != null && getResp.getBody().name().equals("Test WS")).isTrue();
    }
}
