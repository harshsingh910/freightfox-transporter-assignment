package com.freightfox.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataSourceConfig Unit Tests")
class DataSourceConfigTest {

    @Test
    @DisplayName("Should parse standard Railway PostgreSQL URL")
    void testParseStandardRailwayUrl() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "postgresql://postgres:secret123@postgres.railway.internal:5432/railway"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway", parsed.jdbcUrl());
        assertEquals("postgres", parsed.username());
        assertEquals("secret123", parsed.password());
    }

    @Test
    @DisplayName("Should parse postgres:// scheme")
    void testParsePostgresScheme() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "postgres://dbuser:mypass@dbhost:5432/customdb"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://dbhost:5432/customdb", parsed.jdbcUrl());
        assertEquals("dbuser", parsed.username());
        assertEquals("mypass", parsed.password());
    }

    @Test
    @DisplayName("Should handle complex passwords and preserve query parameters")
    void testParseComplexPasswordWithQuery() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "postgresql://postgres:p@ss:word!@roundhouse.proxy.rlwy.net:12345/railway?sslmode=disable"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://roundhouse.proxy.rlwy.net:12345/railway?sslmode=disable", parsed.jdbcUrl());
        assertEquals("postgres", parsed.username());
        assertEquals("p@ss:word!", parsed.password());
    }

    @Test
    @DisplayName("Should preserve already-formatted JDBC URLs without double-prefixing")
    void testParseAlreadyJdbcUrl() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "jdbc:postgresql://postgres.railway.internal:5432/railway?sslmode=require"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway?sslmode=require", parsed.jdbcUrl());
    }

    @Test
    @DisplayName("Should normalize jdbc:postgres:// to valid jdbc:postgresql://")
    void testNormalizeJdbcPostgresUrl() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "jdbc:postgres://postgres.railway.internal:5432/railway"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway", parsed.jdbcUrl());
    }

    @Test
    @DisplayName("Should supply default database when missing in URL")
    void testDefaultDatabaseWhenMissing() {
        DataSourceConfig.ParsedUrl parsed = DataSourceConfig.parsePostgresUrl(
                "postgresql://user:pass@host:5432"
        );
        assertNotNull(parsed);
        assertEquals("jdbc:postgresql://host:5432/postgres", parsed.jdbcUrl());
    }

    @Test
    @DisplayName("Should convert PostgreSQL URI supplied via DataSourceProperties in production profile")
    void testPostgresUrlInPropertiesForProd() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("postgresql://railwayuser:railwaypass@pg.railway.internal:5432/railwaydb");
        properties.setDriverClassName("org.postgresql.Driver");

        DataSourceConfig.ParsedDataSourceConfig resolved = DataSourceConfig.resolveConfig(properties, "prod");
        assertEquals("jdbc:postgresql://pg.railway.internal:5432/railwaydb", resolved.jdbcUrl());
        assertEquals("railwayuser", resolved.username());
        assertEquals("railwaypass", resolved.password());
        assertEquals("org.postgresql.Driver", resolved.driverClassName());
    }

    @Test
    @DisplayName("Should use org.h2.Driver for H2 URLs in local/test profile")
    void testH2InLocalProfile() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:h2:mem:freightfoxtest;DB_CLOSE_DELAY=-1");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setDriverClassName("org.postgresql.Driver"); // Even if misconfigured as Postgres in local/test

        DataSourceConfig.ParsedDataSourceConfig resolved = DataSourceConfig.resolveConfig(properties, "test");
        assertEquals("org.h2.Driver", resolved.driverClassName());
        assertEquals("jdbc:h2:mem:freightfoxtest;DB_CLOSE_DELAY=-1", resolved.jdbcUrl());
    }

    @Test
    @DisplayName("Should reject H2 URL with IllegalStateException in production profile")
    void testRejectH2InProduction() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:h2:mem:freightfox;DB_CLOSE_DELAY=-1");
        properties.setDriverClassName("org.postgresql.Driver");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DataSourceConfig.resolveConfig(properties, "prod")
        );
        assertTrue(ex.getMessage().contains("Production environment ('prod') requires a valid PostgreSQL database"));
    }

    @Test
    @DisplayName("Should reject missing PostgreSQL configuration in production profile")
    void testRejectMissingConfigInProduction() {
        DataSourceProperties properties = new DataSourceProperties();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DataSourceConfig.resolveConfig(properties, "prod")
        );
        assertTrue(ex.getMessage().contains("Production environment ('prod') requires a valid PostgreSQL database"));
    }

    @Test
    @DisplayName("Should sanitize passwords from logged JDBC URLs")
    void testSanitizeJdbcUrl() {
        String sanitized = DataSourceConfig.sanitizeJdbcUrl("jdbc:postgresql://postgres:secret123@host:5432/db");
        assertFalse(sanitized.contains("secret123"));
        assertTrue(sanitized.contains("****"));
    }
}
