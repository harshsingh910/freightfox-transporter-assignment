package com.freightfox.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Custom DataSource configuration ensuring compatibility with Railway, Render,
 * Heroku, and standard JDBC environments.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String databaseUrl = System.getenv("DATABASE_URL");
        String pgHost = System.getenv("PGHOST");
        String pgPort = System.getenv("PGPORT");
        String pgDatabase = System.getenv("PGDATABASE");
        String pgUser = System.getenv("PGUSER");
        String pgPassword = System.getenv("PGPASSWORD");

        HikariConfig config = new HikariConfig();

        if (StringUtils.hasText(databaseUrl) && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            log.info("Detected cloud DATABASE_URL. Converting to PostgreSQL JDBC format.");
            try {
                URI uri = new URI(databaseUrl.replace("postgresql://", "http://").replace("postgres://", "http://"));
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath(); // e.g. /railway

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                config.setJdbcUrl(jdbcUrl);

                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    config.setUsername(userInfo[0]);
                    if (userInfo.length > 1) {
                        config.setPassword(userInfo[1]);
                    }
                }
                config.setDriverClassName("org.postgresql.Driver");
            } catch (Exception e) {
                log.error("Failed to parse DATABASE_URL: {}. Falling back to standard properties.", e.getMessage());
                applyStandardProperties(config, properties);
            }
        } else if (StringUtils.hasText(pgHost) && StringUtils.hasText(pgDatabase)) {
            log.info("Configuring DataSource from individual PG* environment variables.");
            String port = StringUtils.hasText(pgPort) ? pgPort : "5432";
            config.setJdbcUrl("jdbc:postgresql://" + pgHost + ":" + port + "/" + pgDatabase);
            config.setUsername(pgUser);
            config.setPassword(pgPassword);
            config.setDriverClassName("org.postgresql.Driver");
        } else {
            applyStandardProperties(config, properties);
        }

        config.setMaximumPoolSize(Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "10")));
        config.setMinimumIdle(Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "2")));
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);

        return new HikariDataSource(config);
    }

    private void applyStandardProperties(HikariConfig config, DataSourceProperties properties) {
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        if (StringUtils.hasText(properties.getDriverClassName())) {
            config.setDriverClassName(properties.getDriverClassName());
        }
    }
}
