package com.freightfox.config;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedisConfig Unit Tests")
class RedisConfigTest {

    @Test
    @DisplayName("Should parse standard Redis URL with default user and password")
    void testParseStandardRedisUrl() {
        RedisURI uri = RedisConfig.resolveRedisUri("localhost", 6379, "");
        assertNotNull(uri);
        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertFalse(uri.isSsl());
    }

    @Test
    @DisplayName("Should detect and parse full redis:// URL passed in host parameter")
    void testParseRedisUrlInHost() {
        RedisURI uri = RedisConfig.resolveRedisUri("redis://default:myPass123@redis.railway.internal:6379", 6379, "");
        assertNotNull(uri);
        assertEquals("redis.railway.internal", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertEquals("default", uri.getUsername());
        assertEquals("myPass123", new String(uri.getPassword()));
        assertFalse(uri.isSsl());
    }

    @Test
    @DisplayName("Should detect SSL/TLS for rediss:// URLs")
    void testParseRedissUrlWithSsl() {
        RedisURI uri = RedisConfig.resolveRedisUri("rediss://default:cloudPass@roundhouse.proxy.rlwy.net:45678", 6379, "");
        assertNotNull(uri);
        assertEquals("roundhouse.proxy.rlwy.net", uri.getHost());
        assertEquals(45678, uri.getPort());
        assertTrue(uri.isSsl());
        assertEquals("cloudPass", new String(uri.getPassword()));
    }

    @Test
    @DisplayName("Should build RedisURI with credentials when provided")
    void testBuildRedisUriWithCredentials() {
        RedisURI uri = RedisConfig.resolveRedisUri("custom.host", 6380, "secretKey");
        assertNotNull(uri);
        assertEquals("custom.host", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertEquals("secretKey", new String(uri.getPassword()));
    }
}
