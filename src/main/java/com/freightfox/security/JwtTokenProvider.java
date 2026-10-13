package com.freightfox.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.freightfox.config.DataSourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Utility for generating, signing, and parsing JSON Web Tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    public static final String INSECURE_DEFAULT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    public static final int MIN_SECRET_LENGTH_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    @Autowired
    public JwtTokenProvider(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.issuer:freightfox-platform}") String issuer,
            @Value("${app.jwt.access-token-ttl-seconds:86400}") long accessTokenTtlSeconds,
            Environment environment
    ) {
        String resolvedSecret = validateAndResolveSecret(secret, environment);
        this.key = Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public JwtTokenProvider(String secret, String issuer, long accessTokenTtlSeconds) {
        this(secret, issuer, accessTokenTtlSeconds, null);
    }

    public static String validateAndResolveSecret(String secret, Environment environment) {
        boolean isProd = isProduction(environment);
        if (isProd) {
            if (!StringUtils.hasText(secret)) {
                throw new IllegalStateException(
                        "Production environment ('prod') requires a valid, explicit 'JWT_SECRET'. Missing or blank JWT signing secret configured."
                );
            }
            String trimmed = secret.trim();
            if (INSECURE_DEFAULT_SECRET.equalsIgnoreCase(trimmed)) {
                throw new IllegalStateException(
                        "Production environment ('prod') must not use the publicly known default JWT secret. " +
                        "Please configure a unique, secure 'JWT_SECRET' (at least 32 characters / 256 bits) in your environment variables."
                );
            }
            if (trimmed.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "JWT signing secret is too short for production. It must be at least 32 bytes (256 bits) for HMAC-SHA security."
                );
            }
            return trimmed;
        }

        // Non-production (local or test):
        if (StringUtils.hasText(secret)) {
            return secret.trim();
        }
        return INSECURE_DEFAULT_SECRET;
    }

    public static boolean isProduction(Environment environment) {
        if (environment != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(profile) || profile.toLowerCase().contains("prod")) {
                    return true;
                }
            }
        }
        return DataSourceConfig.isProduction((String) null);
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    public long getTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
