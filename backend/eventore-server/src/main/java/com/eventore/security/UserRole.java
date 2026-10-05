package com.eventore.security;

/**
 * Enterprise Role-Based Access Control (RBAC) roles (REQ-103).
 */
public enum UserRole {
    PLATFORM_ADMIN,
    WORKSPACE_ADMIN,
    OPERATOR,
    VIEWER;

    public boolean canPerform(Action action) {
        return switch (this) {
            case PLATFORM_ADMIN -> true;
            case WORKSPACE_ADMIN -> action != Action.ADMIN_BROKER_OPS;
            case OPERATOR -> action == Action.BROWSE_DESTINATIONS
                    || action == Action.SUBSCRIBE
                    || action == Action.PUBLISH;
            case VIEWER -> action == Action.BROWSE_DESTINATIONS
                    || action == Action.SUBSCRIBE;
        };
    }
}
