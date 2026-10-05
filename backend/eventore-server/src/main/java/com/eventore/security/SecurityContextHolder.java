package com.eventore.security;

/**
 * Thread-local security context storing the active {@link UserPrincipal} for the request.
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<UserPrincipal> CURRENT_USER = new ThreadLocal<>();

    private SecurityContextHolder() {}

    public static void setPrincipal(UserPrincipal principal) {
        CURRENT_USER.set(principal);
    }

    public static UserPrincipal getPrincipal() {
        UserPrincipal p = CURRENT_USER.get();
        return p != null ? p : UserPrincipal.devUser();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
