package com.eventore.security;

/**
 * Deployment posture that gates mutating API operations and UI affordances.
 *
 * <ul>
 *   <li>{@link #ADMIN} — full read/write access to connections, publish, subscribe, and admin APIs</li>
 *   <li>{@link #DEV} — same as admin; intended for local development only</li>
 *   <li>{@link #READONLY} — inspect and live-view only; publish, subscribe, and destructive admin calls are blocked</li>
 * </ul>
 */
public enum DeploymentMode {
    ADMIN,
    DEV,
    READONLY
}
