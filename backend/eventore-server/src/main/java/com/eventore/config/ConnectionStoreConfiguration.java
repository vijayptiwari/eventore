package com.eventore.config;

import com.eventore.domain.ConnectionStoreType;
import com.eventore.service.ConnectionProfilePersistence;
import com.eventore.service.store.ConnectionProfileStore;
import com.eventore.service.store.DelegatingConnectionProfileStore;
import com.eventore.service.store.FileConnectionProfileStore;
import com.eventore.service.store.InMemoryConnectionProfileStore;
import com.eventore.service.store.JdbcConnectionProfileStore;
import com.eventore.service.store.K8sCrdConnectionProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration providing pluggable connection profile stores (REQ-101).
 */
@Configuration
public class ConnectionStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConnectionStoreConfiguration.class);

    @Bean
    @Primary
    public DelegatingConnectionProfileStore connectionProfileStore(
            EventoreProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<DataSource> dataSourceProvider,
            ConnectionProfilePersistence filePersistence) {

        EventoreProperties.Connections.Persistence persistenceConfig =
                properties.getConnections().getPersistence();
        boolean enabled = persistenceConfig.isEnabled();
        ConnectionStoreType targetType = persistenceConfig.getType();

        // 1. File Store
        FileConnectionProfileStore fileStore = new FileConnectionProfileStore(
                Path.of(persistenceConfig.getFilePath()),
                enabled,
                objectMapper);

        // 2. In-Memory Store
        InMemoryConnectionProfileStore memStore = new InMemoryConnectionProfileStore(enabled);

        // 3. Kubernetes CRD Store
        K8sCrdConnectionProfileStore crdStore = new K8sCrdConnectionProfileStore(
                Path.of(persistenceConfig.getCrdDirectory()),
                enabled,
                objectMapper);

        // Initial choice based on configuration
        ConnectionProfileStore initialStore = fileStore;

        // 4. JDBC Store (if DataSource available)
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        JdbcConnectionProfileStore jdbcStore = null;
        if (dataSource != null) {
            jdbcStore = new JdbcConnectionProfileStore(
                    dataSource,
                    persistenceConfig.getTableName(),
                    persistenceConfig.isOptimisticLocking(),
                    enabled,
                    objectMapper);
        }

        if (enabled && targetType == ConnectionStoreType.JDBC && jdbcStore == null) {
            throw new IllegalStateException("JDBC connection persistence requires a configured DataSource");
        }
        if (targetType == ConnectionStoreType.JDBC && jdbcStore != null) {
            initialStore = jdbcStore;
        } else if (targetType == ConnectionStoreType.K8S_CRD) {
            initialStore = crdStore;
        } else if (targetType == ConnectionStoreType.IN_MEMORY) {
            initialStore = memStore;
        } else {
            initialStore = filePersistence != null ? filePersistence : fileStore;
        }

        DelegatingConnectionProfileStore delegator = new DelegatingConnectionProfileStore(initialStore);
        delegator.registerStore(fileStore);
        delegator.registerStore(memStore);
        delegator.registerStore(crdStore);
        if (jdbcStore != null) {
            delegator.registerStore(jdbcStore);
        }

        log.info("Initialized pluggable connection store: active={}, enabled={}",
                delegator.getType(), delegator.isEnabled());
        return delegator;
    }
}
