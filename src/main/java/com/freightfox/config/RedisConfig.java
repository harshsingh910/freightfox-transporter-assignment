package com.freightfox.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Robust cloud Redis configuration supporting Railway, Render, Heroku,
 * and containerized environments with automatic URL parsing, private networking,
 * credential resolution, and SSL support.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisURI redisUri(
            @Value("${spring.data.redis.host:localhost}") String defaultHost,
            @Value("${spring.data.redis.port:6379}") int defaultPort,
            @Value("${spring.data.redis.password:}") String defaultPassword
    ) {
        return resolveRedisUri(defaultHost, defaultPort, defaultPassword);
    }

    public static RedisURI resolveRedisUri(String defaultHost, int defaultPort, String defaultPassword) {
        // 1. Check for complete Redis URLs (Railway private internal, Railway public, or standard Redis URL)
        String rawUrl = getFirstNonBlank(
                System.getenv("REDIS_PRIVATE_URL"),
                System.getenv("REDIS_URL"),
                System.getenv("REDISURL"),
                System.getenv("SPRING_DATA_REDIS_URL")
        );

        if (StringUtils.hasText(rawUrl)) {
            try {
                RedisURI uri = RedisURI.create(rawUrl.trim());
                uri.setTimeout(Duration.ofMillis(5000));
                log.info("Configured Redis connection from cloud URL: host={}, port={}, ssl={}, username={}",
                        uri.getHost(), uri.getPort(), uri.isSsl(), uri.getUsername());
                return uri;
            } catch (Exception e) {
                log.error("Failed to parse Redis URL: {}. Falling back to individual properties.", e.getMessage());
            }
        }

        // 2. Check for individual host/port/auth variables (supporting Railway's REDISHOST / REDIS_HOST etc.)
        String host = getFirstNonBlank(
                System.getenv("REDIS_HOST"),
                System.getenv("REDISHOST"),
                System.getenv("SPRING_DATA_REDIS_HOST"),
                defaultHost
        );
        if (!StringUtils.hasText(host)) {
            host = "localhost";
        }

        // Handle case where user pasted full redis:// or rediss:// URI into REDIS_HOST
        if (host.startsWith("redis://") || host.startsWith("rediss://")) {
            try {
                RedisURI uri = RedisURI.create(host.trim());
                uri.setTimeout(Duration.ofMillis(5000));
                log.info("Configured Redis connection from URI provided in host: host={}, port={}, ssl={}",
                        uri.getHost(), uri.getPort(), uri.isSsl());
                return uri;
            } catch (Exception e) {
                log.error("Failed to parse host as URI: {}", e.getMessage());
            }
        }

        String rawPort = getFirstNonBlank(
                System.getenv("REDIS_PORT"),
                System.getenv("REDISPORT"),
                System.getenv("SPRING_DATA_REDIS_PORT"),
                String.valueOf(defaultPort)
        );
        int port = 6379;
        try {
            if (StringUtils.hasText(rawPort)) {
                port = Integer.parseInt(rawPort.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid Redis port '{}', falling back to default {}", rawPort, defaultPort);
            port = defaultPort > 0 ? defaultPort : 6379;
        }

        String password = getFirstNonBlank(
                System.getenv("REDIS_PASSWORD"),
                System.getenv("REDISPASSWORD"),
                System.getenv("SPRING_DATA_REDIS_PASSWORD"),
                defaultPassword
        );

        String username = getFirstNonBlank(
                System.getenv("REDIS_USER"),
                System.getenv("REDISUSER"),
                System.getenv("SPRING_DATA_REDIS_USERNAME")
        );

        boolean ssl = "true".equalsIgnoreCase(getFirstNonBlank(
                System.getenv("REDIS_SSL"),
                System.getenv("SPRING_DATA_REDIS_SSL_ENABLED")
        ));

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host.trim())
                .withPort(port)
                .withTimeout(Duration.ofMillis(5000));

        if (ssl) {
            builder.withSsl(true);
        }

        if (StringUtils.hasText(password)) {
            if (StringUtils.hasText(username)) {
                builder.withAuthentication(username.trim(), password.trim());
            } else {
                builder.withPassword(password.trim().toCharArray());
            }
        }

        RedisURI uri = builder.build();
        log.info("Configured Redis connection from individual properties: host={}, port={}, ssl={}, username={}",
                host, port, ssl, StringUtils.hasText(username) ? username : "(none)");
        return uri;
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(RedisURI redisUri) {
        return RedisClient.create(redisUri);
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(RedisURI redisUri) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(redisUri.getHost());
        standalone.setPort(redisUri.getPort());
        standalone.setDatabase(redisUri.getDatabase());
        if (StringUtils.hasText(redisUri.getUsername())) {
            standalone.setUsername(redisUri.getUsername());
        }
        if (redisUri.getPassword() != null && redisUri.getPassword().length > 0) {
            standalone.setPassword(RedisPassword.of(new String(redisUri.getPassword())));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(5000));
        if (redisUri.isSsl()) {
            clientConfig.useSsl();
        }

        return new LettuceConnectionFactory(standalone, clientConfig.build());
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
}
