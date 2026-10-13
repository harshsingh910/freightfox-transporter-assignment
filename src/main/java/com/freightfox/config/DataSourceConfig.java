package com.freightfox.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Custom DataSource configuration ensuring seamless compatibility with Railway, Render,
 * Heroku, containerized, and local development environments.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();

        // 1. Resolve PostgreSQL configuration from environment or properties
        ParsedDataSourceConfig resolved = resolveConfig(properties, activeProfile);

        config.setJdbcUrl(resolved.jdbcUrl());
        config.setDriverClassName(resolved.driverClassName());
        if (StringUtils.hasText(resolved.username())) {
            config.setUsername(resolved.username());
        }
        if (StringUtils.hasText(resolved.password())) {
            config.setPassword(resolved.password());
        }

        config.setMaximumPoolSize(Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "15")));
        config.setMinimumIdle(Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "2")));
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);

        log.info("Initialized DataSource: driver={}, url={}",
                resolved.driverClassName(), sanitizeJdbcUrl(resolved.jdbcUrl()));

        return new HikariDataSource(config);
    }

    public static ParsedDataSourceConfig resolveConfig(DataSourceProperties properties, String activeProfile) {
        String rawUrl = getFirstNonBlank(
                System.getenv("DATABASE_URL"),
                System.getenv("DATABASE_PUBLIC_URL"),
                System.getenv("SPRING_DATASOURCE_URL"),
                System.getenv("POSTGRES_URL")
        );

        String pgHost = getFirstNonBlank(
                System.getenv("PGHOST"),
                System.getenv("POSTGRES_HOST")
        );
        String pgPort = getFirstNonBlank(
                System.getenv("PGPORT"),
                System.getenv("POSTGRES_PORT"),
                "5432"
        );
        String pgDatabase = getFirstNonBlank(
                System.getenv("PGDATABASE"),
                System.getenv("POSTGRES_DB")
        );
        String pgUser = getFirstNonBlank(
                System.getenv("PGUSER"),
                System.getenv("POSTGRES_USER")
        );
        String pgPassword = getFirstNonBlank(
                System.getenv("PGPASSWORD"),
                System.getenv("POSTGRES_PASSWORD")
        );

        // Case A: PostgreSQL URL (postgres://, postgresql://, jdbc:postgresql://, jdbc:postgres://)
        if (StringUtils.hasText(rawUrl)) {
            ParsedUrl parsed = parsePostgresUrl(rawUrl);
            String username = StringUtils.hasText(parsed.username()) ? parsed.username() : pgUser;
            String password = StringUtils.hasText(parsed.password()) ? parsed.password() : pgPassword;
            return new ParsedDataSourceConfig(parsed.jdbcUrl(), username, password, "org.postgresql.Driver");
        }

        // Case B: Individual PGHOST / PGDATABASE variables
        if (StringUtils.hasText(pgHost) && StringUtils.hasText(pgDatabase)) {
            int port = 5432;
            try {
                port = Integer.parseInt(pgPort.trim());
            } catch (NumberFormatException ignored) {}
            String jdbcUrl = "jdbc:postgresql://" + pgHost.trim() + ":" + port + "/" + pgDatabase.trim();
            String username = StringUtils.hasText(pgUser) ? pgUser.trim() : "postgres";
            return new ParsedDataSourceConfig(jdbcUrl, username, pgPassword, "org.postgresql.Driver");
        }

        // Case C: Standard Spring DataSourceProperties (Local, H2, or custom driver)
        String propUrl = properties.getUrl();
        String propDriver = properties.getDriverClassName();

        // Guard against mismatched H2 URL with PostgreSQL Driver in production
        boolean isProd = "prod".equalsIgnoreCase(activeProfile) ||
                (activeProfile != null && activeProfile.contains("prod"));

        if (StringUtils.hasText(propUrl) && propUrl.startsWith("jdbc:h2:")) {
            if (isProd) {
                throw new IllegalStateException(
                        "Production environment ('prod') requires a valid PostgreSQL database. " +
                        "Neither DATABASE_URL nor PGHOST/PGDATABASE environment variables were provided, " +
                        "and fallback to in-memory H2 is rejected in production. " +
                        "Please configure DATABASE_URL in your Railway service variables."
                );
            }
            // In non-production (e.g. test or local dev), ensure H2 driver is used with H2 URL
            return new ParsedDataSourceConfig(propUrl, properties.getUsername(), properties.getPassword(), "org.h2.Driver");
        }

        if (StringUtils.hasText(propUrl)) {
            String driver = StringUtils.hasText(propDriver) ? propDriver : "org.postgresql.Driver";
            return new ParsedDataSourceConfig(propUrl, properties.getUsername(), properties.getPassword(), driver);
        }

        if (isProd) {
            throw new IllegalStateException(
                    "Production environment ('prod') requires a valid PostgreSQL database, but no database configuration " +
                    "(DATABASE_URL, SPRING_DATASOURCE_URL, or PGHOST) was found."
            );
        }

        // Default local dev fallback to H2
        return new ParsedDataSourceConfig(
                "jdbc:h2:mem:freightfox;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                "org.h2.Driver"
        );
    }

    public static ParsedUrl parsePostgresUrl(String rawUrl) {
        String clean = rawUrl.trim();
        String query = "";
        int qIdx = clean.indexOf('?');
        if (qIdx != -1) {
            query = clean.substring(qIdx);
            clean = clean.substring(0, qIdx);
        }

        // If already JDBC format
        if (clean.startsWith("jdbc:postgresql://") || clean.startsWith("jdbc:postgres://")) {
            return new ParsedUrl(clean + query, null, null);
        }

        // Strip scheme
        if (clean.startsWith("postgresql://")) {
            clean = clean.substring("postgresql://".length());
        } else if (clean.startsWith("postgres://")) {
            clean = clean.substring("postgres://".length());
        }

        String userInfo = null;
        int atIdx = clean.lastIndexOf('@');
        if (atIdx != -1) {
            userInfo = clean.substring(0, atIdx);
            clean = clean.substring(atIdx + 1);
        }

        String username = null;
        String password = null;
        if (userInfo != null) {
            int colonIdx = userInfo.indexOf(':');
            if (colonIdx != -1) {
                username = userInfo.substring(0, colonIdx);
                password = userInfo.substring(colonIdx + 1);
            } else {
                username = userInfo;
            }
        }

        String hostPort = clean;
        String database = "";
        int slashIdx = clean.indexOf('/');
        if (slashIdx != -1) {
            hostPort = clean.substring(0, slashIdx);
            database = clean.substring(slashIdx); // includes '/'
        }

        String host = hostPort;
        int port = 5432;
        int portColon = hostPort.lastIndexOf(':');
        if (portColon != -1) {
            host = hostPort.substring(0, portColon);
            try {
                port = Integer.parseInt(hostPort.substring(portColon + 1));
            } catch (NumberFormatException ignored) {}
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + database + query;
        return new ParsedUrl(jdbcUrl, username, password);
    }

    private static String getFirstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }

    public static String sanitizeJdbcUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]+", "password=****")
                  .replaceAll(":[^/@]+@", ":****@");
    }

    public record ParsedDataSourceConfig(String jdbcUrl, String username, String password, String driverClassName) {}
    public record ParsedUrl(String jdbcUrl, String username, String password) {}
}
