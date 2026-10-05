package com.eventore.cluster;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes cluster status, node discovery, and distributed bus metrics (REQ-102).
 */
@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterController {

    private final SubscriptionDistributionBus distributionBus;

    public ClusterController(SubscriptionDistributionBus distributionBus) {
        this.distributionBus = distributionBus;
    }

    @GetMapping("/status")
    public ResponseEntity<ClusterStatusDto> getClusterStatus() {
        return ResponseEntity.ok(distributionBus.getStatus());
    }
}
