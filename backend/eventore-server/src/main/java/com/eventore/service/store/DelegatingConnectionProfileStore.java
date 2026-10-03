package com.eventore.service.store;

import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite delegating store that manages active storage tiers and runtime migrations (REQ-101).
 */
public class DelegatingConnectionProfileStore implements ConnectionProfileStore {

    private static final Logger log = LoggerFactory.getLogger(DelegatingConnectionProfileStore.class);

    private final Map<ConnectionStoreType, ConnectionProfileStore> stores = new EnumMap<>(ConnectionStoreType.class);
    private volatile ConnectionProfileStore activeStore;

    public DelegatingConnectionProfileStore(ConnectionProfileStore initialStore) {
        this.activeStore = Objects.requireNonNull(initialStore, "initialStore must not be null");
        registerStore(initialStore);
    }

    public synchronized void registerStore(ConnectionProfileStore store) {
        if (store != null) {
            stores.put(store.getType(), store);
        }
    }

    public ConnectionProfileStore getActiveStore() {
        return activeStore;
    }

    public synchronized void setActiveStore(ConnectionStoreType type) {
        ConnectionProfileStore target = stores.get(type);
        if (target == null) {
            throw new IllegalArgumentException("Store type '" + type + "' is not registered or configured");
        }
        log.info("Switched active connection profile store from {} to {}", activeStore.getType(), type);
        this.activeStore = target;
    }

    public Map<ConnectionStoreType, ConnectionProfileStore> getRegisteredStores() {
        return Collections.unmodifiableMap(stores);
    }

    /**
     * Migrates all active connection profiles from the current store to the target store,
     * then switches active store to target.
     */
    public synchronized int migrateTo(ConnectionStoreType targetType) {
        ConnectionProfileStore target = stores.get(targetType);
        if (target == null) {
            throw new IllegalArgumentException("Target store type '" + targetType + "' is not registered or configured");
        }
        if (target == activeStore) {
            log.info("Store is already active on {}", targetType);
            return activeStore.loadAll().size();
        }

        Map<String, ConnectionProfile> profiles = activeStore.loadAll();
        log.info("Migrating {} profile(s) from {} to {}", profiles.size(), activeStore.getType(), targetType);
        target.saveAll(profiles);
        this.activeStore = target;
        log.info("Successfully migrated to {} store", targetType);
        return profiles.size();
    }

    @Override
    public ConnectionStoreType getType() {
        return activeStore.getType();
    }

    @Override
    public boolean isEnabled() {
        return activeStore.isEnabled();
    }

    @Override
    public Map<String, ConnectionProfile> loadAll() {
        return activeStore.loadAll();
    }

    @Override
    public void save(ConnectionProfile profile) {
        activeStore.save(profile);
    }

    @Override
    public void saveAll(Map<String, ConnectionProfile> profiles) {
        activeStore.saveAll(profiles);
    }

    @Override
    public void delete(String id) {
        activeStore.delete(id);
    }

    @Override
    public Optional<Long> getVersion(String id) {
        return activeStore.getVersion(id);
    }

    @Override
    public ConnectionStoreInfo getInfo() {
        return activeStore.getInfo();
    }
}
