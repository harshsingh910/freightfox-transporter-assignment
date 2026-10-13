package com.freightfox.assignment.optimizer;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain record representing a quoted rate for a specific lane and transporter.
 */
public record OptimizationQuote(
        String laneId,
        String transporterId,
        BigDecimal quoteAmount
) {
    public OptimizationQuote {
        Objects.requireNonNull(laneId, "Lane ID must not be null");
        Objects.requireNonNull(transporterId, "Transporter ID must not be null");
        Objects.requireNonNull(quoteAmount, "Quote amount must not be null");
        if (quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quote amount must be positive. Found: " + quoteAmount);
        }
    }
}
