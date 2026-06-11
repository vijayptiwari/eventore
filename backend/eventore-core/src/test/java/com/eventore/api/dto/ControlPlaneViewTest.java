package com.eventore.api.dto;

import com.eventore.controlplane.ControlPlaneSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPlaneViewTest {

    @Test
    void fromCopiesSnapshotFields() {
        ControlPlaneSnapshot snapshot = new ControlPlaneSnapshot();
        snapshot.setRevision(42L);
        ControlPlaneSnapshot.UiCascade cascade = new ControlPlaneSnapshot.UiCascade();
        cascade.setConnectionProtocols(List.of("KAFKA"));
        snapshot.setUiCascade(cascade);
        snapshot.setOpenApiStreams(List.of("kafka-stream"));

        ControlPlaneView view = ControlPlaneView.from(snapshot);

        assertEquals(42L, view.getRevision());
        assertEquals(cascade, view.getUiCascade());
        assertEquals(List.of("kafka-stream"), view.getOpenApiStreams());
    }

    @Test
    void fromRejectsNullSnapshot() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ControlPlaneView.from(null));
        assertEquals("control plane snapshot is required", ex.getMessage());
    }
}
