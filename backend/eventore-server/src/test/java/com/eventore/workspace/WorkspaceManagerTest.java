package com.eventore.workspace;

import com.eventore.security.UserPrincipal;
import com.eventore.security.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceManagerTest {

    private WorkspaceManager manager;

    @BeforeEach
    void setUp() {
        manager = new WorkspaceManager();
    }

    @Test
    void listsInitialWorkspacesForPlatformAdmin() {
        UserPrincipal admin = UserPrincipal.devUser();
        List<Workspace> list = manager.listWorkspaces(admin);
        assertThat(list).isNotEmpty();
        assertThat(list.stream().map(Workspace::id)).contains("default", "analytics", "finance");
    }

    @Test
    void filtersWorkspacesForRestrictedTenantUser() {
        UserPrincipal user = new UserPrincipal(
                "usr-tenant-1",
                "user@tenant.com",
                "Tenant User",
                Set.of(UserRole.OPERATOR),
                Set.of("analytics"),
                "analytics",
                true);

        List<Workspace> list = manager.listWorkspaces(user);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo("analytics");

        // Attempting to get unauthorized workspace throws 403
        assertThatThrownBy(() -> manager.getWorkspace("finance", user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Access denied");

        // Authorized workspace retrieval succeeds
        Optional<Workspace> ws = manager.getWorkspace("analytics", user);
        assertThat(ws).isPresent();
        assertThat(ws.get().name()).isEqualTo("Analytics & Telemetry");
    }

    @Test
    void allowsWorkspaceCreationForWorkspaceAdmin() {
        UserPrincipal wsAdmin = new UserPrincipal(
                "ws-admin-1",
                "admin@tenant.com",
                "WS Admin",
                Set.of(UserRole.WORKSPACE_ADMIN),
                Set.of("*"),
                "default",
                true);

        Workspace newWs = Workspace.of("iot-edge", "IoT & Edge Devices", "Telemetry from edge gateways", "iot", "edge");
        Workspace created = manager.createWorkspace(newWs, wsAdmin);
        assertThat(created.id()).isEqualTo("iot-edge");

        assertThat(manager.getWorkspace("iot-edge", wsAdmin)).isPresent();
    }

    @Test
    void deniesWorkspaceCreationForViewerOrOperator() {
        UserPrincipal operator = new UserPrincipal(
                "op-1",
                "op@tenant.com",
                "Operator",
                Set.of(UserRole.OPERATOR),
                Set.of("default"),
                "default",
                true);

        Workspace newWs = Workspace.of("unauthorized", "Unauthorized", "Desc");
        assertThatThrownBy(() -> manager.createWorkspace(newWs, operator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Requires WORKSPACE_ADMIN or PLATFORM_ADMIN");
    }

    @Test
    void protectsDefaultWorkspaceFromDeletion() {
        UserPrincipal admin = UserPrincipal.devUser();
        assertThatThrownBy(() -> manager.deleteWorkspace("default", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot delete default workspace");
    }
}
