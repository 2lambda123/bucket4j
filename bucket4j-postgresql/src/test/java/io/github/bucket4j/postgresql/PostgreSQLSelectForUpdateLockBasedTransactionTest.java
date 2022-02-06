package io.github.bucket4j.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PostgreSQLSelectForUpdateLockBasedTransactionTest {

    private static PostgreSQLContainer container;
    private static DataSource dataSource;
    private static PostgreSQLProxyManager proxyManager;

    @BeforeClass
    public static void initializeInstance() throws SQLException {
        container = startPostgreSQLContainer();
        dataSource = createJdbcDataSource(container);
        BucketTableSettings tableSettings = BucketTableSettings.defaultSettings();
        final String INIT_TABLE_SCRIPT = "CREATE TABLE IF NOT EXISTS {0}({1} BIGINT PRIMARY KEY, {2} BYTEA)";
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                String query = MessageFormat.format(INIT_TABLE_SCRIPT, tableSettings.getTableName(), tableSettings.getIdName(), tableSettings.getStateName());
                statement.execute(query);
            }
        }
        PostgreSQLProxyConfiguration configuration = PostgreSQLProxyConfigurationBuilder.builder()
                .addLockBasedTransactionType(LockBasedTransactionType.SELECT_FOR_UPDATE)
                .addClientSideConfig(ClientSideConfig.getDefault())
                .addTableSettings(tableSettings)
                .build(dataSource);
        proxyManager = new PostgreSQLProxyManager(configuration);
    }

    @Test
    public void testBucketRemoval() {
        Long key = 1L;
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(4, Duration.ofHours(1)))
                .build();
        BucketProxy bucket = proxyManager.builder().build(key, configuration);
        bucket.getAvailableTokens();

        assertTrue(proxyManager.getProxyConfiguration(key).isPresent());
        proxyManager.removeProxy(key);
        assertFalse(proxyManager.getProxyConfiguration(key).isPresent());
    }

    @AfterClass
    public static void shutdown() {
        if (container != null) {
            container.stop();
        }
    }

    private static DataSource createJdbcDataSource(PostgreSQLContainer container) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(container.getJdbcUrl());
        hikariConfig.setUsername(container.getUsername());
        hikariConfig.setPassword(container.getPassword());
        hikariConfig.setDriverClassName(container.getDriverClassName());
        hikariConfig.setMaximumPoolSize(100);
        return new HikariDataSource(hikariConfig);
    }

    private static PostgreSQLContainer startPostgreSQLContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer();
        container.start();
        return container;
    }

}
