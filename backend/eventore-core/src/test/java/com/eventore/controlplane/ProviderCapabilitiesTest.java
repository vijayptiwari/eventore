package com.eventore.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderCapabilitiesTest {

    @Test
    void defaultsAreFalseAndPrefixesEmpty() {
        ProviderCapabilities caps = new ProviderCapabilities();
        assertThat(caps.isMessaging()).isFalse();
        assertThat(caps.isInspect()).isFalse();
        assertThat(caps.isAdmin()).isFalse();
        assertThat(caps.isLiveView()).isFalse();
        assertThat(caps.getDataPlaneApiPrefixes()).isEmpty();
    }

    @Test
    void settersUpdateCapabilityFlags() {
        ProviderCapabilities caps = new ProviderCapabilities();
        caps.setMessaging(true);
        caps.setInspect(true);
        caps.setAdmin(true);
        caps.setLiveView(true);
        caps.setDataPlaneApiPrefixes(List.of("/kafka", "/inspect"));
        assertThat(caps.isMessaging()).isTrue();
        assertThat(caps.isInspect()).isTrue();
        assertThat(caps.isAdmin()).isTrue();
        assertThat(caps.isLiveView()).isTrue();
        assertThat(caps.getDataPlaneApiPrefixes()).containsExactly("/kafka", "/inspect");
    }

    @Test
    void nullPrefixesReturnsEmptyList() {
        ProviderCapabilities caps = new ProviderCapabilities();
        caps.setDataPlaneApiPrefixes(null);
        assertThat(caps.getDataPlaneApiPrefixes()).isEmpty();
    }
}
