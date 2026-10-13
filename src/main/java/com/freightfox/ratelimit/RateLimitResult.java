package com.freightfox.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long retryAfterSeconds,
        long remainingTokens
) {
    public static RateLimitResult allowed(long remainingTokens) {
        return new RateLimitResult(true, 0, remainingTokens);
    }

    public static RateLimitResult denied(long retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds, 0);
    }
}
