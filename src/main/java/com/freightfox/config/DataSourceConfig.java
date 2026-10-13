package com.freightfox.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Custom DataSource configuration ensuring seamless compatibility with Railway, Render,
 * Heroku, containerized, and local development environments.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties, Environment environment) {
        HikariConfig config = new HikariConfig();

        // 1. Resolve PostgreSQL configuration from environment or properties
        ParsedDataSourceConfig resolved = resolveConfig(properties, environment);

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

    public static ParsedDataSourceConfig resolveConfig(DataSourceProperties properties, Environment environment) {
        String activeProfile = null;
        if (environment != null) {
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles != null && activeProfiles.length > 0) {
                activeProfile = String.join(",", activeProfiles);
            }
        }
        return resolveConfig(properties, activeProfile);
    }

    public static ParsedDataSourceConfig resolveConfig(DataSourceProperties properties, String activeProfile) {
        // 1. Check direct PostgreSQL URL environment variables
        String rawUrl = getFirstNonBlank(
                System.getenv("DATABASE_URL"),
                System.getenv("DATABASE_PUBLIC_URL"),
                System.getenv("SPRING_DATASOURCE_URL"),
                System.getenv("POSTGRES_URL"),
                System.getenv("JDBC_DATABASE_URL")
        );

        // Fallback to properties.getUrl() if it represents a PostgreSQL URL
        if (!StringUtils.hasText(rawUrl) && properties != null && isPostgresUrl(properties.getUrl())) {
            rawUrl = properties.getUrl();
        }

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
                System.getenv("POSTGRES_USER"),
                properties != null ? properties.getUsername() : null
        );
        String pgPassword = getFirstNonBlank(
                System.getenv("PGPASSWORD"),
                System.getenv("POSTGRES_PASSWORD"),
                properties != null ? properties.getPassword() : null
        );

        // Case A: PostgreSQL URL (postgres://, postgresql://, jdbc:postgresql://, jdbc:postgres://)
        if (StringUtils.hasText(rawUrl) && isPostgresUrl(rawUrl)) {
            ParsedUrl parsed = parsePostgresUrl(rawUrl);
            if (parsed != null) {
                String username = StringUtils.hasText(parsed.username()) ? parsed.username() : pgUser;
                String password = StringUtils.hasText(parsed.password()) ? parsed.password() : pgPassword;
                return new ParsedDataSourceConfig(parsed.jdbcUrl(), username, password, "org.postgresql.Driver");
            }
        }

        // Case B: Individual PGHOST / PGDATABASE variables (Railway Postgres linked service)
        if (StringUtils.hasText(pgHost)) {
            int port = 5432;
            try {
                port = Integer.parseInt(pgPort.trim());
            } catch (NumberFormatException ignored) {}
            String db = StringUtils.hasText(pgDatabase) ? pgDatabase.trim() : "postgres";
            String jdbcUrl = "jdbc:postgresql://" + pgHost.trim() + ":" + port + "/" + db;
            String username = StringUtils.hasText(pgUser) ? pgUser.trim() : "postgres";
            return new ParsedDataSourceConfig(jdbcUrl, username, pgPassword, "org.postgresql.Driver");
        }

        // Case C: Standard Spring DataSourceProperties (Local, H2, or custom driver)
        boolean isProd = isProduction(activeProfile);
        String propUrl = properties != null ? properties.getUrl() : null;
        String propDriver = properties != null ? properties.getDriverClassName() : null;

        // Guard against H2 URL or missing configuration in production
        if (isProd) {
            throw new IllegalStateException(
                    "Production environment ('prod') requires a valid PostgreSQL database. " +
                    "Neither DATABASE_URL nor PGHOST/PGDATABASE environment variables were provided, " +
                    "and fallback to in-memory H2 is rejected in production. " +
                    "Please configure DATABASE_URL in your Railway service variables."
            );
        }

        // Non-production (local or test):
        if (StringUtils.hasText(propUrl) && propUrl.startsWith("jdbc:h2:")) {
            return new ParsedDataSourceConfig(propUrl, properties.getUsername(), properties.getPassword(), "org.h2.Driver");
        }

        if (StringUtils.hasText(propUrl)) {
            String driver = StringUtils.hasText(propDriver) ? propDriver : "org.h2.Driver";
            return new ParsedDataSourceConfig(propUrl, properties.getUsername(), properties.getPassword(), driver);
        }

        // Default local dev fallback to H2
        return new ParsedDataSourceConfig(
                "jdbc:h2:mem:freightfox;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "",
                "org.h2.Driver"
        );
    }

    public static boolean isProduction(String activeProfile) {
        if ("prod".equalsIgnoreCase(activeProfile) || (activeProfile != null && activeProfile.contains("prod"))) {
            return true;
        }
        String envProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if ("prod".equalsIgnoreCase(envProfile) || (envProfile != null && envProfile.contains("prod"))) {
            return true;
        }
        String propProfile = System.getProperty("spring.profiles.active");
        if ("prod".equalsIgnoreCase(propProfile) || (propProfile != null && propProfile.contains("prod"))) {
            return true;
        }
        return false;
    }

    public static boolean isPostgresUrl(String url) {
        if (!StringUtils.hasText(url)) return false;
        String clean = url.trim().toLowerCase();
        return clean.startsWith("postgres://") ||
                clean.startsWith("postgresql://") ||
                clean.startsWith("jdbc:postgresql://") ||
                clean.startsWith("jdbc:postgres://");
    }

    public static ParsedUrl parsePostgresUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        String clean = rawUrl.trim();
        String query = "";
        int qIdx = clean.indexOf('?');
        if (qIdx != -1) {
            query = clean.substring(qIdx);
            clean = clean.substring(0, qIdx);
        }

        // If already JDBC format
        if (clean.startsWith("jdbc:postgresql://")) {
            return new ParsedUrl(clean + query, null, null);
        }
        if (clean.startsWith("jdbc:postgres://")) {
            return new ParsedUrl("jdbc:postgresql://" + clean.substring("jdbc:postgres://".length()) + query, null, null);
        }

        // Strip scheme
        if (clean.startsWith("postgresql://")) {
            clean = clean.substring("postgresql://".length());
        } else if (clean.startsWith("postgres://")) {
            clean = clean.substring("postgres://".length());
        } else {
            return null;
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

        if (!StringUtils.hasText(database) || "/".equals(database)) {
            database = "/postgres";
        } else if (!database.startsWith("/")) {
            database = "/" + database;
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
