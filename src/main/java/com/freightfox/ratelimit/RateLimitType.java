package com.freightfox.ratelimit;

/**
 * Categorization of endpoints for rate-limiting tiers.
 */
public enum RateLimitType {
    LOGIN,
    REGISTER,
    ASSIGNMENT,
    IMPORT,
    GENERAL
}
