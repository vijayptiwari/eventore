package com.eventore.security;

import java.util.Set;

/**
 * Authenticated principal holding user identities, enterprise roles, and multi-tenant workspace assignments (REQ-103 & REQ-104).
 */
public record UserPrincipal(
        String userId,
        String email,
        String displayName,
        Set<UserRole> roles,
        Set<String> assignedWorkspaces,
        String currentWorkspaceId,
        boolean isJwt) {

    public boolean isPlatformAdmin() {
        return roles != null && roles.contains(UserRole.PLATFORM_ADMIN);
    }

    public boolean hasRole(UserRole role) {
        if (roles == null) {
            return false;
        }
        return roles.contains(role) || isPlatformAdmin();
    }

    public boolean canAccessWorkspace(String workspaceId) {
        if (isPlatformAdmin() || (assignedWorkspaces != null && assignedWorkspaces.contains("*"))) {
            return true;
        }
        return workspaceId != null && assignedWorkspaces != null && assignedWorkspaces.contains(workspaceId);
    }

    public boolean canPerformAction(Action action) {
        if (isPlatformAdmin()) {
            return true;
        }
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(r -> r.canPerform(action));
    }

    public static UserPrincipal devUser() {
        return new UserPrincipal(
                "dev-admin",
                "admin@eventore.local",
                "Developer Admin",
                Set.of(UserRole.PLATFORM_ADMIN),
                Set.of("*", "default"),
                "default",
                false);
    }

    public static UserPrincipal staticTokenUser() {
        return new UserPrincipal(
                "token-operator",
                "token@eventore.local",
                "API Token Operator",
                Set.of(UserRole.PLATFORM_ADMIN),
                Set.of("*", "default"),
                "default",
                false);
    }
}
