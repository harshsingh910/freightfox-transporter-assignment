package com.freightfox.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Distributed rate-limiting service backed by Redis and Bucket4j with graceful
 * or strict configurable in-memory fallback.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties properties;
    private final RedisClient redisClient;
    private final io.lettuce.core.RedisURI redisUri;

    private StatefulRedisConnection<String, byte[]> redisConnection;
    private ProxyManager<String> proxyManager;

    private final ConcurrentMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    public RateLimitService(
            RateLimitProperties properties,
            RedisClient redisClient,
            io.lettuce.core.RedisURI redisUri
    ) {
        this.properties = properties;
        this.redisClient = redisClient;
        this.redisUri = redisUri;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Rate limiting is disabled by configuration.");
            return;
        }
        tryConnect();
    }

    public synchronized boolean tryConnect() {
        if (redisConnection != null && redisConnection.isOpen() && proxyManager != null) {
            return true;
        }
        try {
            if (redisConnection != null) {
                try {
                    redisConnection.close();
                } catch (Exception ignored) {}
            }
            this.redisConnection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            this.proxyManager = LettuceBasedProxyManager.builderFor(redisConnection).build();
            log.info("Successfully connected to Redis at {}:{} for distributed rate limiting.",
                    redisUri.getHost(), redisUri.getPort());
            return true;
        } catch (Exception e) {
            log.warn("Could not connect to Redis at {}:{}. Distributed rate limiting fallback active: {}",
                    redisUri.getHost(), redisUri.getPort(), e.getMessage());
            this.proxyManager = null;
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (redisConnection != null) {
            try {
                redisConnection.close();
            } catch (Exception ignored) {}
        }
    }

    public RateLimitResult tryConsume(RateLimitType type, String clientKey) {
        if (!properties.isEnabled()) {
            return RateLimitResult.allowed(Long.MAX_VALUE);
        }

        String fullKey = "rate_limit:" + type.name().toLowerCase() + ":" + clientKey;
        BucketConfiguration config = buildConfiguration(type);

        if (proxyManager != null) {
            try {
                Bucket bucket = proxyManager.builder().build(fullKey, () -> config);
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                return toResult(probe);
            } catch (Exception e) {
                log.warn("Redis rate-limiting failed for key {}: {}. Evaluating fallback policy.", fullKey, e.getMessage());
                if (!properties.isFallbackToInMemory()) {
                    // Strict mode: deny or throw
                    return RateLimitResult.denied(60);
                }
            }
        }

        // In-memory fallback
        Bucket inMemoryBucket = localBuckets.computeIfAbsent(fullKey, k ->
                Bucket.builder().addLimit(getBandwidthForType(type)).build()
        );
        ConsumptionProbe probe = inMemoryBucket.tryConsumeAndReturnRemaining(1);
        return toResult(probe);
    }

    private RateLimitResult toResult(ConsumptionProbe probe) {
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        }
        long waitNanos = probe.getNanosToWaitForRefill();
        long waitSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos));
        return RateLimitResult.denied(waitSeconds);
    }

    private BucketConfiguration buildConfiguration(RateLimitType type) {
        return BucketConfiguration.builder()
                .addLimit(getBandwidthForType(type))
                .build();
    }

    private Bandwidth getBandwidthForType(RateLimitType type) {
        RateLimitProperties.Tier tier = switch (type) {
            case LOGIN -> properties.getLogin();
            case REGISTER -> properties.getRegister();
            case ASSIGNMENT -> properties.getAssignment();
            case IMPORT -> properties.getImport();
            case GENERAL -> properties.getGeneral();
        };

        return Bandwidth.builder()
                .capacity(tier.getCapacity())
                .refillGreedy(tier.getRefillTokens(), tier.getDuration())
                .build();
    }

    public boolean isRedisConnected() {
        if (proxyManager != null && redisConnection != null && redisConnection.isOpen()) {
            return true;
        }
        return tryConnect();
    }
}
