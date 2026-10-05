package com.eventore.cluster;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterControllerTest {

    @Test
    void getClusterStatusReturnsMetrics() {
        SubscriptionDistributionBus bus = mock(SubscriptionDistributionBus.class);
        ClusterStatusDto mockStatus = new ClusterStatusDto(
                "REDIS",
                "pod-prod-1",
                4,
                150L,
                148L,
                3,
                true);
        when(bus.getStatus()).thenReturn(mockStatus);

        ClusterController controller = new ClusterController(bus);
        ResponseEntity<ClusterStatusDto> response = controller.getClusterStatus();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().mode()).isEqualTo("REDIS");
        assertThat(response.getBody().nodeId()).isEqualTo("pod-prod-1");
        assertThat(response.getBody().activeSubscribers()).isEqualTo(4);
        assertThat(response.getBody().totalBroadcasts()).isEqualTo(150L);
        assertThat(response.getBody().connectedPeers()).isEqualTo(3);
        assertThat(response.getBody().healthy()).isTrue();
    }
}
