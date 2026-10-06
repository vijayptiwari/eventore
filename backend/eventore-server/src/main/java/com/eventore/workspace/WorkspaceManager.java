package com.eventore.workspace;

import com.eventore.security.UserPrincipal;
import com.eventore.security.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service managing multi-tenant workspaces and access control (REQ-104).
 */
@Service
public class WorkspaceManager {

    private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();

    public WorkspaceManager() {
        // Initialize default enterprise workspaces
        registerInitial(Workspace.of(
                "default",
                "Default Workspace",
                "Primary operational workspace for default streaming connections",
                "default", "production"));
        registerInitial(Workspace.of(
                "analytics",
                "Analytics & Telemetry",
                "Streaming clickstream, metrics, and BI data pipelines",
                "analytics", "telemetry"));
        registerInitial(Workspace.of(
                "finance",
                "Payments & Ledger",
                "PCI-DSS audited transaction events and payment integrations",
                "finance", "audit"));
    }

    private void registerInitial(Workspace ws) {
        workspaces.put(ws.id(), ws);
    }

    public List<Workspace> listWorkspaces(UserPrincipal principal) {
        if (principal == null || principal.isPlatformAdmin() || principal.assignedWorkspaces().contains("*")) {
            return new ArrayList<>(workspaces.values());
        }
        return workspaces.values().stream()
                .filter(w -> principal.canAccessWorkspace(w.id()))
                .toList();
    }

    public Optional<Workspace> getWorkspace(String id, UserPrincipal principal) {
        if (id == null) {
            return Optional.empty();
        }
        Workspace ws = workspaces.get(id);
        if (ws == null) {
            return Optional.empty();
        }
        if (principal != null && !principal.canAccessWorkspace(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to workspace: " + id);
        }
        return Optional.of(ws);
    }

    public Workspace createWorkspace(Workspace workspace, UserPrincipal principal) {
        if (workspace == null || workspace.id() == null || workspace.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace ID is required");
        }
        if (principal != null && !principal.hasRole(UserRole.WORKSPACE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires WORKSPACE_ADMIN or PLATFORM_ADMIN role to create workspaces");
        }
        if (workspaces.putIfAbsent(workspace.id(), workspace) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace already exists: " + workspace.id());
        }
        return workspace;
    }

    public boolean deleteWorkspace(String id, UserPrincipal principal) {
        if ("default".equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete default workspace");
        }
        if (principal != null && !principal.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires PLATFORM_ADMIN role to delete workspaces");
        }
        return workspaces.remove(id) != null;
    }
}
