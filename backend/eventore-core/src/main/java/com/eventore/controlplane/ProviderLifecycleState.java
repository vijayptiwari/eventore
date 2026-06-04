package com.eventore.controlplane;

/** Lifecycle state of a stream provider in the control plane (desired state, not broker health). */
public enum ProviderLifecycleState {
    REGISTERED,
    DEREGISTERED
}
