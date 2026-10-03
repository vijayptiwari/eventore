package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.eventore.domain.ProtocolType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DelegatingConnectionProfileStoreTest {

    private InMemoryConnectionProfileStore store1;
    private InMemoryConnectionProfileStore store2;
    private DelegatingConnectionProfileStore delegator;

    @BeforeEach
    void setUp() {
        this.store1 = new InMemoryConnectionProfileStore(true);
        this.store2 = new InMemoryConnectionProfileStore(true) {
            @Override
            public ConnectionStoreType getType() {
                return ConnectionStoreType.FILE;
            }
        };
        this.delegator = new DelegatingConnectionProfileStore(store1);
        this.delegator.registerStore(store2);
    }

    @Test
    void delegatorDelegatesToActiveStore() {
        assertEquals(ConnectionStoreType.IN_MEMORY, delegator.getType());

        ConnectionProfile profile = new ConnectionProfile();
        profile.setId("conn-1");
        profile.setName("First");
        profile.setProtocol(ProtocolType.KAFKA);

        delegator.save(profile);
        assertEquals(1, delegator.loadAll().size());
        assertEquals(1, store1.loadAll().size());
        assertEquals(0, store2.loadAll().size());
    }

    @Test
    void migrateToTransfersProfilesAndSwitchesActiveStore() {
        ConnectionProfile profile1 = new ConnectionProfile();
        profile1.setId("conn-1");
        profile1.setProtocol(ProtocolType.MQTT);

        ConnectionProfile profile2 = new ConnectionProfile();
        profile2.setId("conn-2");
        profile2.setProtocol(ProtocolType.PULSAR);

        delegator.save(profile1);
        delegator.save(profile2);

        assertEquals(2, store1.loadAll().size());
        assertEquals(0, store2.loadAll().size());
        assertEquals(ConnectionStoreType.IN_MEMORY, delegator.getType());

        int count = delegator.migrateTo(ConnectionStoreType.FILE);
        assertEquals(2, count);
        assertEquals(ConnectionStoreType.FILE, delegator.getType());
        assertEquals(2, store2.loadAll().size());
    }
}
